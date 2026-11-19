import { useEffect, useMemo, useState } from 'react';
import { Pagination } from '../components/Pagination';
import { authFetch } from '../utils/apiClient';
import { aggregateDeviceStats, getCurrentMonthRangeJst } from '../utils/eventProcessing';
import type { Device, DeviceStat, WalkEvent } from '../utils/eventProcessing';

const _base = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');
if (!_base) console.warn('[StatsScreen] VITE_API_URL is not set. Copy .env.example to .env.local.');
const EVENTS_URL = `${_base}/events`;
const DEVICES_URL = `${_base}/devices`;

type SortDir = 'asc' | 'desc';
const PAGE_SIZE = 20;

export function StatsScreen() {
  const [events, setEvents] = useState<WalkEvent[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const initRange = getCurrentMonthRangeJst();
  const [startDate, setStartDate] = useState(initRange.startDate);
  const [endDate, setEndDate] = useState(initRange.endDate);

  const [sortDir, setSortDir] = useState<SortDir>('desc');
  const [currentPage, setCurrentPage] = useState(1);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [eventsRes, devicesRes] = await Promise.all([authFetch(EVENTS_URL), authFetch(DEVICES_URL)]);
      if (!eventsRes.ok) throw new Error(`events: HTTP ${eventsRes.status}`);
      if (!devicesRes.ok) throw new Error(`devices: HTTP ${devicesRes.status}`);
      const [eventsData, devicesData]: [WalkEvent[], Device[]] = await Promise.all([
        eventsRes.json(),
        devicesRes.json(),
      ]);
      setEvents(eventsData);
      setDevices(devicesData);
    } catch (e) {
      setError(e instanceof Error ? e.message : '取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);
  useEffect(() => { setCurrentPage(1); }, [startDate, endDate, sortDir]);

  const resetToCurrentMonth = () => {
    const { startDate: s, endDate: e } = getCurrentMonthRangeJst();
    setStartDate(s);
    setEndDate(e);
  };

  const stats: DeviceStat[] = useMemo(
    () => aggregateDeviceStats(events, devices, startDate || undefined, endDate || undefined),
    [events, devices, startDate, endDate]
  );

  const sorted = useMemo(() => [...stats].sort((a, b) => {
    const dir = sortDir === 'asc' ? 1 : -1;
    const diff = dir * (a.detectCount - b.detectCount);
    if (diff !== 0) return diff;
    return a.deviceId.localeCompare(b.deviceId);
  }), [stats, sortDir]);

  const pageItems = useMemo(() => {
    const start = (currentPage - 1) * PAGE_SIZE;
    return sorted.slice(start, start + PAGE_SIZE);
  }, [sorted, currentPage]);

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
        <h2 style={{ margin: 0, fontSize: '16px' }}>実績</h2>
        <button onClick={fetchData} style={btnStyle('#1976D2')} disabled={loading}>
          {loading ? '読み込み中...' : '最新データに更新'}
        </button>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'flex-end', marginBottom: '12px', padding: '10px', backgroundColor: '#f9f9f9', borderRadius: '6px', border: '1px solid #e0e0e0' }}>
        <label style={labelStyle}>
          開始日
          <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} style={inputStyle} />
        </label>
        <span style={{ color: '#666', fontSize: '13px', paddingBottom: '5px' }}>〜</span>
        <label style={labelStyle}>
          終了日
          <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)} style={inputStyle} />
        </label>
        <button onClick={resetToCurrentMonth} style={{ ...btnStyle('#1976D2'), marginBottom: '1px' }}>今月にリセット</button>
        <button onClick={() => { setStartDate(''); setEndDate(''); }} style={{ ...btnStyle('#757575'), marginBottom: '1px' }}>全期間</button>
      </div>

      {error && <p style={{ color: '#D32F2F', marginBottom: '8px' }}>⚠ エラー: {error}</p>}

      <Pagination currentPage={currentPage} totalItems={sorted.length} pageSize={PAGE_SIZE} onPageChange={setCurrentPage} />

      <table style={tableStyle}>
        <thead>
          <tr style={{ backgroundColor: '#f5f5f5' }}>
            <th style={thStyle}>デバイス名</th>
            <th style={thStyle}>ユーザー名</th>
            <th style={thStyle}>デバイスID</th>
            <th
              style={{ ...thStyle, cursor: 'pointer' }}
              onClick={() => setSortDir(d => d === 'asc' ? 'desc' : 'asc')}
            >
              検知件数{sortDir === 'asc' ? ' ▲' : ' ▼'}
            </th>
          </tr>
        </thead>
        <tbody>
          {loading && stats.length === 0 && (
            <tr><td colSpan={4} style={{ ...tdStyle, textAlign: 'center', color: '#888' }}>読み込み中...</td></tr>
          )}
          {!loading && sorted.length === 0 && !error && (
            <tr><td colSpan={4} style={{ ...tdStyle, textAlign: 'center', color: '#888' }}>データがありません</td></tr>
          )}
          {pageItems.map((stat, i) => (
            <tr key={stat.deviceId} style={{ backgroundColor: i % 2 === 0 ? '#fff' : '#fafafa' }}>
              <td style={tdStyle}>{stat.deviceModel}</td>
              <td style={{ ...tdStyle, color: stat.userName === '未登録' ? '#aaa' : undefined }}>
                {stat.userName}
              </td>
              <td style={{ ...tdStyle, fontFamily: 'monospace', fontSize: '12px' }}>{stat.deviceId}</td>
              <td style={{ ...tdStyle, fontWeight: 600 }}>{stat.detectCount}件</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div style={{ marginTop: '8px' }}>
        <Pagination currentPage={currentPage} totalItems={sorted.length} pageSize={PAGE_SIZE} onPageChange={setCurrentPage} />
      </div>
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
const labelStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '3px',
  fontSize: '12px',
  color: '#555',
};
const inputStyle: React.CSSProperties = {
  padding: '5px 8px',
  border: '1px solid #ccc',
  borderRadius: '4px',
  fontSize: '13px',
};
