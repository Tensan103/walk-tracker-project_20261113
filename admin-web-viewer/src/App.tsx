import { useState } from 'react';
import { EventsPage } from './pages/EventsPage';
import { DevicesPage } from './pages/DevicesPage';

type Tab = 'events' | 'devices';

const TABS: { id: Tab; label: string }[] = [
  { id: 'events',  label: '歩行検知ダッシュボード' },
  { id: 'devices', label: 'デバイス管理' },
];

// API Gateway のベース URL は admin-web-viewer/.env.local の VITE_API_URL で設定してください。

function App() {
  const [activeTab, setActiveTab] = useState<Tab>('events');

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif', maxWidth: '1200px', margin: '0 auto' }}>
      <h1 style={{ fontSize: '20px', margin: '0 0 16px', color: '#1a1a1a' }}>
        Walk Tracker 管理画面
      </h1>

      {/* タブナビゲーション */}
      <nav style={{ display: 'flex', borderBottom: '2px solid #e0e0e0', marginBottom: '20px' }}>
        {TABS.map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            style={{
              padding: '10px 20px',
              border: 'none',
              cursor: 'pointer',
              backgroundColor: 'transparent',
              borderBottom: activeTab === tab.id ? '2px solid #1976D2' : '2px solid transparent',
              marginBottom: '-2px',
              color: activeTab === tab.id ? '#1976D2' : '#666',
              fontWeight: activeTab === tab.id ? 600 : 400,
              fontSize: '14px',
              transition: 'color 0.15s',
            }}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      {/* ページコンテンツ */}
      {activeTab === 'events'  && <EventsPage />}
      {activeTab === 'devices' && <DevicesPage />}
    </div>
  );
}

export default App;
