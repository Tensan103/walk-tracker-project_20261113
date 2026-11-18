import { useEffect, useState } from 'react';

type WalkEvent = {
  deviceId: string;
  timestamp: number;
  category: string;
};

const EVENTS_URL = 'https://hv9doulbs3.execute-api.ap-northeast-1.amazonaws.com/prod/events';

export function EventsPage() {
  const [events, setEvents] = useState<WalkEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchEvents = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(EVENTS_URL);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: WalkEvent[] = await res.json();
      setEvents(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : '取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchEvents(); }, []);

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
        <h2 style={{ margin: 0, fontSize: '16px' }}>歩行検知イベント一覧</h2>
        <button onClick={fetchEvents} style={btnStyle('#1976D2')} disabled={loading}>
          {loading ? '読み込み中...' : '最新データに更新'}
        </button>
      </div>

      {error && <p style={{ color: '#D32F2F', marginBottom: '8px' }}>⚠ エラー: {error}</p>}

      <table style={tableStyle}>
        <thead>
          <tr style={{ backgroundColor: '#f5f5f5' }}>
            <th style={thStyle}>デバイスID</th>
            <th style={thStyle}>イベント</th>
            <th style={thStyle}>検知日時 (JST)</th>
          </tr>
        </thead>
        <tbody>
          {loading && events.length === 0 && (
            <tr><td colSpan={3} style={{ ...tdStyle, textAlign: 'center', color: '#888' }}>読み込み中...</td></tr>
          )}
          {!loading && events.length === 0 && !error && (
            <tr><td colSpan={3} style={{ ...tdStyle, textAlign: 'center', color: '#888' }}>データがありません</td></tr>
          )}
          {events.map((ev, i) => (
            <tr key={i} style={{ backgroundColor: i % 2 === 0 ? '#fff' : '#fafafa' }}>
              <td style={tdStyle}>{ev.deviceId}</td>
              <td style={{ ...tdStyle, color: ev.category === 'DETECT' ? '#D32F2F' : '#1976D2', fontWeight: 500 }}>
                {ev.category === 'DETECT' ? '歩行開始 (DETECT)' : '待機に戻る (RELEASE)'}
              </td>
              <td style={tdStyle}>{new Date(ev.timestamp).toLocaleString('ja-JP')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const tableStyle: React.CSSProperties = {
  width: '100%',
  borderCollapse: 'collapse',
  border: '1px solid #e0e0e0',
  fontSize: '14px',
};
const thStyle: React.CSSProperties = {
  padding: '10px 12px',
  textAlign: 'left',
  borderBottom: '2px solid #e0e0e0',
  fontWeight: 600,
  whiteSpace: 'nowrap',
};
const tdStyle: React.CSSProperties = {
  padding: '10px 12px',
  borderBottom: '1px solid #e0e0e0',
};
const btnStyle = (bg: string): React.CSSProperties => ({
  padding: '6px 14px',
  backgroundColor: bg,
  color: '#fff',
  border: 'none',
  borderRadius: '4px',
  cursor: 'pointer',
  fontSize: '13px',
});
