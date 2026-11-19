import { useEffect, useMemo, useState } from 'react';
import { Pagination } from '../components/Pagination';
import {
  compareNullable,
  formatDuration,
  isWithinJstDateRange,
  pairWalkEvents,
} from '../utils/eventProcessing';
import type { WalkEvent } from '../utils/eventProcessing';

const _base = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');
if (!_base) console.warn('[EventsPage] VITE_API_URL is not set. Copy .env.example to .env.local.');
const EVENTS_URL = `${_base}/events`;

type SortKey = 'detectTimestamp' | 'durationMs' | 'deviceModel' | 'userName';
type SortDir = 'asc' | 'desc';

const PAGE_SIZE = 20;

function formatJst(ts: number | null): string {
  if (ts === null) return '-';
  return new Date(ts).toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' });
}

export function EventsPage() {
  const [events, setEvents] = useState<WalkEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [deviceModelFilter, setDeviceModelFilter] = useState('');
  const [userNameFilter, setUserNameFilter] = useState('');

  const [sortKey, setSortKey] = useState<SortKey>('detectTimestamp');
  const [sortDir, setSortDir] = useState<SortDir>('desc');

  const [currentPage, setCurrentPage] = useState(1);

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

  useEffect(() => { setCurrentPage(1); }, [startDate, endDate, deviceModelFilter, userNameFilter, sortKey, sortDir]);

  const paired = useMemo(() => pairWalkEvents(events), [events]);

  const filtered = useMemo(() => paired.filter(ev => {
    if (!isWithinJstDateRange(ev.detectTimestamp, startDate || undefined, endDate || undefined)) return false;
    if (deviceModelFilter && !ev.deviceModel.toLowerCase().includes(deviceModelFilter.toLowerCase())) return false;
    if (userNameFilter && !ev.userName.toLowerCase().includes(userNameFilter.toLowerCase())) return false;
    return true;
  }), [paired, startDate, endDate, deviceModelFilter, userNameFilter]);

  const sorted = useMemo(() => [...filtered].sort((a, b) => {
    const dir = sortDir === 'asc' ? 1 : -1;
    if (sortKey === 'detectTimestamp') return dir * (a.detectTimestamp - b.detectTimestamp);
    if (sortKey === 'durationMs') return compareNullable(a.durationMs, b.durationMs, (x, y) => dir * (x - y));
    if (sortKey === 'deviceModel') {
      const aVal = a.deviceModel === '-' ? null : a.deviceModel;
      const bVal = b.deviceModel === '-' ? null : b.deviceModel;
      return compareNullable(aVal, bVal, (x, y) => dir * x.localeCompare(y, 'ja'));
    }
    // userName
    const aVal = a.userName === '未登録' ? null : a.userName;
    const bVal = b.userName === '未登録' ? null : b.userName;
    return compareNullable(aVal, bVal, (x, y) => dir * x.localeCompare(y, 'ja'));
  }), [filtered, sortKey, sortDir]);

  const pageItems = useMemo(() => {
    const start = (currentPage - 1) * PAGE_SIZE;
    return sorted.slice(start, start + PAGE_SIZE);
  }, [sorted, currentPage]);

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortDir(key === 'detectTimestamp' ? 'desc' : 'asc');
    }
  };

  const clearFilters = () => {
    setStartDate('');
    setEndDate('');
    setDeviceModelFilter('');
    setUserNameFilter('');
  };

  const sortIndicator = (key: SortKey) => sortKey === key ? (sortDir === 'asc' ? ' ▲' : ' ▼') : '';

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
        <h2 style={{ margin: 0, fontSize: '16px' }}>タイムライン</h2>
        <button onClick={fetchEvents} style={btnStyle('#1976D2')} disabled={loading}>
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
        <label style={labelStyle}>
          デバイス名
          <input type="text" value={deviceModelFilter} onChange={e => setDeviceModelFilter(e.target.value)} placeholder="部分一致" style={{ ...inputStyle, width: '120px' }} />
        </label>
        <label style={labelStyle}>
          ユーザー名
          <input type="text" value={userNameFilter} onChange={e => setUserNameFilter(e.target.value)} placeholder="部分一致" style={{ ...inputStyle, width: '120px' }} />
        </label>
        <button onClick={clearFilters} style={{ ...btnStyle('#757575'), marginBottom: '1px' }}>クリア</button>
      </div>

      {error && <p style={{ color: '#D32F2F', marginBottom: '8px' }}>⚠ エラー: {error}</p>}

      <Pagination currentPage={currentPage} totalItems={sorted.length} pageSize={PAGE_SIZE} onPageChange={setCurrentPage} />

      <table style={tableStyle}>
        <thead>
          <tr style={{ backgroundColor: '#f5f5f5' }}>
            <th style={{ ...thStyle, cursor: 'pointer' }} onClick={() => handleSort('detectTimestamp')}>
              検知日時 (JST){sortIndicator('detectTimestamp')}
            </th>
            <th style={thStyle}>解除日時 (JST)</th>
            <th style={{ ...thStyle, cursor: 'pointer' }} onClick={() => handleSort('durationMs')}>
              時間{sortIndicator('durationMs')}
            </th>
            <th style={{ ...thStyle, cursor: 'pointer' }} onClick={() => handleSort('deviceModel')}>
              デバイス名{sortIndicator('deviceModel')}
            </th>
            <th style={{ ...thStyle, cursor: 'pointer' }} onClick={() => handleSort('userName')}>
              ユーザー名{sortIndicator('userName')}
            </th>
            <th style={thStyle}>デバイスID</th>
          </tr>
        </thead>
        <tbody>
          {loading && paired.length === 0 && (
            <tr><td colSpan={6} style={{ ...tdStyle, textAlign: 'center', color: '#888' }}>読み込み中...</td></tr>
          )}
          {!loading && paired.length === 0 && !error && (
            <tr><td colSpan={6} style={{ ...tdStyle, textAlign: 'center', color: '#888' }}>データがありません</td></tr>
          )}
          {!loading && paired.length > 0 && sorted.length === 0 && (
            <tr><td colSpan={6} style={{ ...tdStyle, textAlign: 'center', color: '#888' }}>条件に一致するデータがありません</td></tr>
          )}
          {pageItems.map((ev, i) => (
            <tr key={`${ev.deviceId}-${ev.detectTimestamp}`} style={{ backgroundColor: i % 2 === 0 ? '#fff' : '#fafafa' }}>
              <td style={{ ...tdStyle, whiteSpace: 'nowrap' }}>{formatJst(ev.detectTimestamp)}</td>
              <td style={{ ...tdStyle, whiteSpace: 'nowrap', color: ev.releaseTimestamp === null ? '#aaa' : undefined }}>
                {formatJst(ev.releaseTimestamp)}
              </td>
              <td style={{ ...tdStyle, whiteSpace: 'nowrap' }}>{formatDuration(ev.durationMs)}</td>
              <td style={tdStyle}>{ev.deviceModel}</td>
              <td style={{ ...tdStyle, color: ev.userName === '未登録' ? '#aaa' : undefined }}>
                {ev.userName}
              </td>
              <td style={{ ...tdStyle, fontFamily: 'monospace', fontSize: '12px' }}>{ev.deviceId}</td>
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
