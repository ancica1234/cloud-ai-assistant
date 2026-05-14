import React, { useState } from "react";
import "./Search.css";

const SUGGESTIONS = ["SNIV", "FMC", "EENT", "hotboard", "CSI", "ground school", "aircraft", "schedule"];

export default function Search() {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [limit, setLimit] = useState(5);

  const doSearch = async (q) => {
    const term = q || query.trim();
    if (!term) return;
    setLoading(true);
    setResults(null);
    try {
      const res = await fetch(`/api/ai/search?q=${encodeURIComponent(term)}&limit=${limit}`);
      const data = await res.json();
      setResults(data);
    } catch (err) {
      setResults({ query: term, total: 0, results: [], error: "Failed to connect to server" });
    }
    setLoading(false);
  };

  const highlight = (text, keyword) => {
    if (!keyword) return text;
    const parts = text.split(new RegExp(`(${keyword})`, "gi"));
    return parts.map((part, i) =>
      part.toLowerCase() === keyword.toLowerCase()
        ? <mark key={i} className="highlight">{part}</mark>
        : part
    );
  };

  return (
    <div className="search-page">
      <div className="search-header">
        <h2 className="search-title">🔍 Knowledge Base Search</h2>
        <p className="search-subtitle">Search the documentation directly — no AI, instant results with source citations</p>
      </div>

      <div className="search-bar">
        <input
          className="search-input"
          value={query}
          onChange={e => setQuery(e.target.value)}
          onKeyDown={e => e.key === "Enter" && doSearch()}
          placeholder="Search by keyword or phrase..."
          autoFocus
        />
        <select
          className="limit-select"
          value={limit}
          onChange={e => setLimit(Number(e.target.value))}>
          <option value={3}>Top 3</option>
          <option value={5}>Top 5</option>
          <option value={10}>Top 10</option>
        </select>
        <button
          className="search-btn"
          onClick={() => doSearch()}
          disabled={loading || !query.trim()}>
          {loading ? "Searching..." : "Search"}
        </button>
      </div>

      <div className="suggestions">
        <span className="suggestions-label">Quick searches:</span>
        {SUGGESTIONS.map((s, i) => (
          <button key={i} className="suggestion-chip"
            onClick={() => { setQuery(s); doSearch(s); }}>{s}</button>
        ))}
      </div>

      {results && (
        <div className="results">
          <div className="results-header">
            <span className="results-count">
              {results.total} result{results.total !== 1 ? "s" : ""} for <strong>"{results.query}"</strong>
            </span>
          </div>

          {results.error && <div className="error-msg">{results.error}</div>}

          {results.total === 0 && !results.error && (
            <div className="no-results">
              <p>No matches found for "{results.query}"</p>
              <p className="no-results-hint">Try a broader keyword or check spelling</p>
            </div>
          )}

          {results.results.map((r, i) => (
            <div key={i} className="result-card">
              <div className="result-header">
                <span className="result-num">#{i + 1}</span>
                <span className="result-source">📄 {r.source}</span>
                <span className="result-match">{r.matchedKeyword}</span>
              </div>
              <div className="result-snippet">
                {highlight(r.snippet, results.query)}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}