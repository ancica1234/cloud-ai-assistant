import React, { useState } from 'react';
import Chat from './components/Chat';
import Search from './components/Search';
import './App.css';

export default function App() {
  const [tab, setTab] = useState('chat');

  return (
    <div className="app">
      <header className="header">
        <div className="header-inner">
          <div className="logo">
            <span className="logo-icon">🤖</span>
            <span className="logo-text">Cloud AI Assistant</span>
          </div>
          <nav className="tabs">
            <button
              className={`tab ${tab === 'chat' ? 'active' : ''}`}
              onClick={() => setTab('chat')}>
              💬 Chat
            </button>
            <button
              className={`tab ${tab === 'search' ? 'active' : ''}`}
              onClick={() => setTab('search')}>
              🔍 Keyword Search
            </button>
          </nav>
        </div>
      </header>
      <main className="main">
        {tab === 'chat' ? <Chat /> : <Search />}
      </main>
    </div>
  );
}
