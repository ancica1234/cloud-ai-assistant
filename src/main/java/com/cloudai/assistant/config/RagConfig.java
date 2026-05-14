
package com.cloudai.assistant.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class RagConfig {

    @Value("${app.ai.vectorstore.path:./vectorstore.json}")
    private String vectorStorePath;

    @Value("${app.ai.docs.path:./docs}")
    private String docsPath;

    @Bean
    public ClearableVectorStore vectorStore(EmbeddingModel embeddingModel) {
        ClearableVectorStore store = new ClearableVectorStore(embeddingModel);
        File vsFile = new File(vectorStorePath);
        if (vsFile.exists()) {
            store.load(vsFile);
            System.out.println("Vector store loaded from cache: " + vsFile.getAbsolutePath());
        } else {
            loadDocuments(store);
            store.save(vsFile);
            System.out.println("Vector store built and saved: " + vsFile.getAbsolutePath());
        }
        return store;
    }

    public void loadDocuments(ClearableVectorStore store) {
        File docsDir = new File(docsPath);
        if (!docsDir.exists() || docsDir.listFiles() == null) {
            System.out.println("No docs directory found at: " + docsDir.getAbsolutePath());
            return;
        }
        TokenTextSplitter splitter = new TokenTextSplitter();
        for (File file : docsDir.listFiles()) {
            try {
                List<Document> docs = new ArrayList<>();
                if (file.getName().endsWith(".pdf")) {
                    PagePdfDocumentReader reader = new PagePdfDocumentReader(
                            new FileSystemResource(file),
                            PdfDocumentReaderConfig.builder().build());
                    docs = reader.get();
                } else if (file.getName().endsWith(".txt") || file.getName().endsWith(".md")) {
                    String content = new String(Files.readAllBytes(file.toPath()));
                    docs = List.of(new Document(content, Map.of("source", file.getName())));
                } else {
                    continue;
                }
                List<Document> chunks = splitter.apply(docs);
                chunks.forEach(chunk -> chunk.getMetadata().put("source", file.getName()));
                store.add(chunks);
                System.out.println("Loaded: " + file.getName() + " (" + chunks.size() + " chunks)");
            } catch (Exception e) {
                System.err.println("Failed to load: " + file.getName() + " - " + e.getMessage());
            }
        }
    }
}
