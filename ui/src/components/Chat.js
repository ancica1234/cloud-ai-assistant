import React, { useState, useRef, useEffect } from "react";
import "./Chat.css";

const SESSION_ID = "session-" + Math.random().toString(36).slice(2);

const EXAMPLES = [
  "What is a SNIV?",
  "How does the IP Hotboard work?",
  "What are the aircraft status codes?",
  "Explain the daily schedule build process",
  "What is a double schedule conflict?",
  "What is EENT and how does it affect operations?",
];

export default function Chat() {
  const [messages, setMessages] = useState([{
    role: "assistant",
    content: "Hello! I am your aviation training assistant. Ask me anything about flight scheduling, personnel availability, ground school, or aircraft operations.",
    sources: [],
  }]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const sendMessage = async (text) => {
    const msg = text || input.trim();
    if (!msg || loading) return;
    setInput("");
    setMessages(prev => [...prev, { role: "user", content: msg }]);
    setLoading(true);
    setMessages(prev => [...prev, { role: "assistant", content: "", sources: [], streaming: true }]);
    try {
      const es = new EventSource(
        `/api/ai/chat/stream?message=${encodeURIComponent(msg)}&sessionId=${SESSION_ID}`
      );
      let sources = [];
      es.addEventListener("sources", e => {
        sources = e.data ? e.data.split(",").filter(Boolean) : [];
      });
      es.addEventListener("token", e => {
        setMessages(msgs => {
          const updated = [...msgs];
          const last = { ...updated[updated.length - 1] };
          const token = e.data;
          const prev = last.content;
          const needsSpace = prev.length > 0
            && token !== " " && !prev.endsWith(" ")
            && token.charCodeAt(0) !== 10
            && !".,:;!?)}".includes(token[0]);




          last.content += needsSpace ? " " + token : token;
          last.sources = sources;
          updated[updated.length - 1] = last;
          return updated;
        });
      });
      es.addEventListener("done", () => {
        es.close();
        setLoading(false);
        setMessages(prev => {
          const u = [...prev];
          u[u.length - 1] = { ...u[u.length - 1], streaming: false };
          return u;
        });
      });
      es.onerror = () => { es.close(); setLoading(false); };
    } catch (err) {
      setLoading(false);
    }
  };

  const clearChat = async () => {
    await fetch(`/api/ai/chat/memory/${SESSION_ID}`, { method: "DELETE" });
    setMessages([{ role: "assistant", content: "Chat cleared. Ask me anything!", sources: [] }]);
  };

  return (
    <div className="chat">
      <div className="messages">
        {messages.map((msg, i) => (
          <div key={i} className={`message ${msg.role}`}>
            <div className="bubble">
              <div className="avatar">{msg.role === "user" ? "👤" : "🤖"}</div>
              <div className="content">
                <div className="text">
                  {msg.content}
                  {msg.streaming && <span className="cursor">▌</span>}
                </div>
                {msg.sources && msg.sources.length > 0 && (
                  <div className="sources">
                    <span className="sources-label">Sources:</span>
                    {msg.sources.map((s, j) => (
                      <span key={j} className="source-tag">{s}</span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      {messages.length === 1 && (
        <div className="examples">
          <p className="examples-label">Try an example:</p>
          <div className="examples-grid">
            {EXAMPLES.map((ex, i) => (
              <button key={i} className="example-btn" onClick={() => sendMessage(ex)}>{ex}</button>
            ))}
          </div>
        </div>
      )}

      <div className="input-bar">
        <button className="clear-btn" onClick={clearChat} title="Clear chat">🗑️</button>
        <input
          className="input"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === "Enter" && sendMessage()}
          placeholder="Ask anything about aviation training..."
          disabled={loading}
        />
        <button
          className="send-btn"
          onClick={() => sendMessage()}
          disabled={loading || !input.trim()}>
          {loading ? "⏳" : "➤"}
        </button>
      </div>
    </div>
  );
}