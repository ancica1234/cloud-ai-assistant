package com.cloudai.assistant.controller;

import com.cloudai.assistant.config.ClearableVectorStore;
import com.cloudai.assistant.config.RagConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final ChatClient chatClient;
    private final ClearableVectorStore vectorStore;
    private final RagConfig ragConfig;
    private final ConcurrentHashMap<String, List<Map<String,String>>> sessionHistories = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY = 10;

    @Value("${app.ai.system-prompt}")
    private String systemPrompt;

    @Value("${app.ai.rag.top-k:6}")
    private int ragTopK;

    @Value("${app.ai.rag.keyword-top-k:10}")
    private int keywordTopK;

    @Value("${app.ai.vectorstore.path:./vectorstore.json}")
    private String vectorStorePath;

    private static final String UNKNOWN_RESPONSE =
        "I don't have information on that in my knowledge base. " +
        "Please consult the official documentation or your system administrator.";

    public AiController(ChatClient.Builder builder, ClearableVectorStore vectorStore, RagConfig ragConfig) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
        this.ragConfig = ragConfig;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        String sessionId = request.getOrDefault("sessionId", "default");
        List<Map<String,String>> history = sessionHistories.computeIfAbsent(sessionId, id -> new ArrayList<>());
        List<Document> sourceDocs = vectorStore.similaritySearch(SearchRequest.query(userMessage).withTopK(ragTopK));
        Set<String> sources = extractSources(sourceDocs);
        if (sourceDocs.isEmpty() || !isRelevant(sourceDocs)) {
            updateHistory(history, userMessage, UNKNOWN_RESPONSE);
            return Map.of("message", userMessage, "response", UNKNOWN_RESPONSE, "sources", Set.of(), "sessionId", sessionId);
        }
        String ctx = buildContextualMessage(history, userMessage);
        String response = chatClient.prompt()
                .system(systemPrompt).user(ctx)
                .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults().withTopK(ragTopK)))
                .call().content();
        updateHistory(history, userMessage, response);
        return Map.of("message", userMessage, "response", response, "sources", sources, "sessionId", sessionId);
    }

    @GetMapping("/chat/stream")
    public SseEmitter chatStream(@RequestParam String message, @RequestParam(defaultValue = "default") String sessionId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        List<Map<String,String>> history = sessionHistories.computeIfAbsent(sessionId, id -> new ArrayList<>());
        List<Document> sourceDocs = vectorStore.similaritySearch(SearchRequest.query(message).withTopK(ragTopK));
        Set<String> sources = extractSources(sourceDocs);
        if (sourceDocs.isEmpty() || !isRelevant(sourceDocs)) {
            updateHistory(history, message, UNKNOWN_RESPONSE);
            Thread.ofVirtual().start(() -> {
                try {
                    emitter.send(SseEmitter.event().name("sources").data(""));
                    emitter.send(SseEmitter.event().name("token").data(UNKNOWN_RESPONSE));
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();
                } catch (IOException e) { emitter.completeWithError(e); }
            });
            return emitter;
        }
        String ctx = buildContextualMessage(history, message);
        StringBuilder fullResponse = new StringBuilder();
        Thread.ofVirtual().start(() -> {
            try {
                emitter.send(SseEmitter.event().name("sources").data(String.join(",", sources)));
                chatClient.prompt().system(systemPrompt).user(ctx)
                        .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults().withTopK(ragTopK)))
                        .stream().content()
                        .doOnComplete(() -> {
                            updateHistory(history, message, fullResponse.toString());
                            try { emitter.send(SseEmitter.event().name("done").data("[DONE]")); emitter.complete(); }
                            catch (IOException e) { emitter.completeWithError(e); }
                        })
                        .subscribe(token -> {
                            fullResponse.append(token);
                            try { emitter.send(SseEmitter.event().name("token").data(token)); }
                            catch (IOException e) { emitter.completeWithError(e); }
                        });
            } catch (Exception e) { emitter.completeWithError(e); }
        });
        return emitter;
    }

    @GetMapping("/search")
    public Map<String, Object> keywordSearch(@RequestParam String q, @RequestParam(defaultValue = "5") int limit) {
        if (q == null || q.isBlank()) return Map.of("query", "", "results", List.of(), "total", 0);
        int topK = Math.min(limit, keywordTopK);
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.query(q).withTopK(topK));
        String queryLower = q.toLowerCase();
        List<Map<String, Object>> results = docs.stream()
                .filter(doc -> doc.getContent().toLowerCase().contains(queryLower))
                .map(doc -> Map.<String, Object>of(
                        "source", doc.getMetadata().getOrDefault("source", "unknown"),
                        "snippet", extractSnippet(doc.getContent(), q, 300),
                        "matchedKeyword", q))
                .collect(Collectors.toList());
        if (results.isEmpty() && !docs.isEmpty()) {
            results = docs.stream().map(doc -> Map.<String, Object>of(
                    "source", doc.getMetadata().getOrDefault("source", "unknown"),
                    "snippet", doc.getContent().length() > 300 ? doc.getContent().substring(0, 300) + "..." : doc.getContent(),
                    "matchedKeyword", q + " (semantic match)")).collect(Collectors.toList());
        }
        return Map.of("query", q, "results", results, "total", results.size());
    }

    @DeleteMapping("/chat/memory/{sessionId}")
    public Map<String, String> clearMemory(@PathVariable String sessionId) {
        sessionHistories.remove(sessionId);
        return Map.of("status", "cleared", "sessionId", sessionId);
    }

    @PostMapping("/admin/reindex")
    public Map<String, Object> reindex() {
        long start = System.currentTimeMillis();
        vectorStore.clear();
        ragConfig.loadDocuments(vectorStore);
        vectorStore.save(new File(vectorStorePath));
        return Map.of("status", "reindexed", "chunks", vectorStore.size(), "elapsed", (System.currentTimeMillis() - start) + "ms");
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "up", "chunks", vectorStore.size(), "sessions", sessionHistories.size(), "version", "1.0.0");
    }

    private Set<String> extractSources(List<Document> docs) {
        return docs.stream().map(d -> (String) d.getMetadata().getOrDefault("source", "unknown")).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isRelevant(List<Document> docs) {
        String top = docs.get(0).getContent().toLowerCase();
        return top.contains("training") || top.contains("aviation") || top.contains("flight") ||
               top.contains("instructor") || top.contains("student") || top.contains("schedule") ||
               top.contains("aircraft") || top.contains("simulator") || top.contains("sniv") ||
               top.contains("syllabus") || top.contains("its") || top.contains("navair") ||
               top.contains("maintenance") || top.contains("fcif") || top.contains("maf");
    }

    private void updateHistory(List<Map<String,String>> history, String user, String response) {
        history.add(Map.of("role", "user", "content", user));
        history.add(Map.of("role", "assistant", "content", response));
        while (history.size() > MAX_HISTORY) history.remove(0);
    }

    private String buildContextualMessage(List<Map<String,String>> history, String currentMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("RULE: Only answer using the context documents provided. ");
        sb.append("If the answer is not in those documents, say so clearly.\n\n");
        if (!history.isEmpty()) {
            sb.append("[Previous conversation:]\n");
            for (Map<String,String> msg : history) {
                sb.append(msg.get("role").equals("user") ? "User: " : "Assistant: ").append(msg.get("content")).append("\n");
            }
            sb.append("\n");
        }
        sb.append("[Question:] ").append(currentMessage);
        return sb.toString();
    }

    private String extractSnippet(String content, String keyword, int maxLen) {
        int idx = content.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) return content.length() > maxLen ? content.substring(0, maxLen) + "..." : content;
        int start = Math.max(0, idx - 100);
        int end = Math.min(content.length(), idx + maxLen);
        String snippet = content.substring(start, end);
        if (start > 0) snippet = "..." + snippet;
        if (end < content.length()) snippet = snippet + "...";
        return snippet;
    }
}
