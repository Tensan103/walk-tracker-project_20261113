import { useState } from 'react';
import { Authenticator } from '@aws-amplify/ui-react';
import '@aws-amplify/ui-react/styles.css';
import { EventsPage } from './pages/EventsPage';
import { StatsScreen } from './pages/StatsScreen';
import { DevicesPage } from './pages/DevicesPage';

type Tab = 'events' | 'stats' | 'devices';

const TABS: { id: Tab; label: string }[] = [
  { id: 'events',  label: 'タイムライン' },
  { id: 'stats',   label: '実績' },
  { id: 'devices', label: 'デバイス管理' },
];

function App() {
  return (
    <Authenticator hideSignUp>
      {({ signOut, user }) => <AppContent signOut={signOut} userEmail={user?.signInDetails?.loginId ?? ''} />}
    </Authenticator>
  );
}

type AppContentProps = { signOut: (() => void) | undefined; userEmail: string };

function AppContent({ signOut, userEmail }: AppContentProps) {
  const [activeTab, setActiveTab] = useState<Tab>('events');

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif', maxWidth: '1200px', margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
        <h1 style={{ fontSize: '20px', margin: 0, color: '#1a1a1a' }}>
          Walk Tracker 管理画面
        </h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '13px', color: '#555' }}>
          {userEmail && <span>{userEmail}</span>}
          <button onClick={signOut} style={signOutBtnStyle}>
            ログアウト
          </button>
        </div>
      </div>

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

      {activeTab === 'events'  && <EventsPage />}
      {activeTab === 'stats'   && <StatsScreen />}
      {activeTab === 'devices' && <DevicesPage />}
    </div>
  );
}

const signOutBtnStyle: React.CSSProperties = {
  padding: '5px 12px',
  backgroundColor: '#757575',
  color: '#fff',
  border: 'none',
  borderRadius: '4px',
  cursor: 'pointer',
  fontSize: '13px',
};

export default App;
