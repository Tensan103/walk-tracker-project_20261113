import { useEffect, useState } from 'react';

type Device = {
  deviceId: string;
  userName: string;
  deviceModel: string;
  status: string;
  createdAt: number;
};

type NewForm = { deviceId: string; userName: string; deviceModel: string };
type EditForm = { userName: string; deviceModel: string };

const BASE = 'https://hv9doulbs3.execute-api.ap-northeast-1.amazonaws.com/prod';
const DEVICES_URL = `${BASE}/devices`;

export function DevicesPage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // 新規登録フォーム
  const [newForm, setNewForm] = useState<NewForm>({ deviceId: '', userName: '', deviceModel: '' });

  // 編集中の行
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<EditForm>({ userName: '', deviceModel: '' });

  // ── API ──────────────────────────────────────────────

  const fetchDevices = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(DEVICES_URL);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: Device[] = await res.json();
      setDevices(data.sort((a, b) => b.createdAt - a.createdAt));
    } catch (e) {
      setError(e instanceof Error ? e.message : '取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const createDevice = async () => {
    if (!newForm.deviceId.trim()) { alert('デバイスIDを入力してください'); return; }
    setSubmitting(true);
    setError(null);
    try {
      const res = await fetch(DEVICES_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newForm),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setNewForm({ deviceId: '', userName: '', deviceModel: '' });
      await fetchDevices();
    } catch (e) {
      setError(e instanceof Error ? e.message : '登録に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const updateDevice = async (deviceId: string) => {
    setSubmitting(true);
    setError(null);
    try {
      const res = await fetch(DEVICES_URL, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ deviceId, ...editForm }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setEditingId(null);
      await fetchDevices();
    } catch (e) {
      setError(e instanceof Error ? e.message : '更新に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const deleteDevice = async (deviceId: string) => {
    if (!window.confirm(`デバイス「${deviceId}」を削除しますか？`)) return;
    setError(null);
    try {
      const res = await fetch(`${DEVICES_URL}/${encodeURIComponent(deviceId)}`, { method: 'DELETE' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      await fetchDevices();
    } catch (e) {
      setError(e instanceof Error ? e.message : '削除に失敗しました');
    }
  };

  const startEdit = (d: Device) => {
    setEditingId(d.deviceId);
    setEditForm({ userName: d.userName, deviceModel: d.deviceModel });
  };

  useEffect(() => { fetchDevices(); }, []);

  // ── UI ───────────────────────────────────────────────

  return (
    <div>
      {/* ── 新規登録フォーム ── */}
      <section style={cardStyle}>
        <h3 style={sectionTitleStyle}>新規デバイス登録</h3>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <label style={labelStyle}>
            デバイスID *
            <div style={{ display: 'flex', gap: '4px', marginTop: '4px' }}>
              <input
                style={inputStyle}
                value={newForm.deviceId}
                onChange={e => setNewForm(f => ({ ...f, deviceId: e.target.value }))}
                placeholder="例: EMP-001"
              />
              <button
                style={btnStyle('#757575')}
                onClick={() => setNewForm(f => ({ ...f, deviceId: crypto.randomUUID() }))}
                title="UUIDを自動生成"
              >
                UUID生成
              </button>
            </div>
          </label>
          <label style={labelStyle}>
            ユーザー名
            <input
              style={{ ...inputStyle, marginTop: '4px' }}
              value={newForm.userName}
              onChange={e => setNewForm(f => ({ ...f, userName: e.target.value }))}
              placeholder="例: 田中 太郎"
            />
          </label>
          <label style={labelStyle}>
            端末モデル
            <input
              style={{ ...inputStyle, marginTop: '4px' }}
              value={newForm.deviceModel}
              onChange={e => setNewForm(f => ({ ...f, deviceModel: e.target.value }))}
              placeholder="例: Pixel 7a"
            />
          </label>
          <button
            style={{ ...btnStyle('#1976D2'), height: '36px', alignSelf: 'flex-end', marginBottom: '1px' }}
            onClick={createDevice}
            disabled={submitting}
          >
            {submitting ? '登録中...' : '登録'}
          </button>
        </div>
      </section>

      {/* ── エラーと操作バー ── */}
      {error && <p style={{ color: '#D32F2F', marginBottom: '8px', fontSize: '14px' }}>⚠ エラー: {error}</p>}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
        <h3 style={{ margin: 0, fontSize: '15px' }}>デバイス一覧 ({devices.length}件)</h3>
        <button onClick={fetchDevices} style={btnStyle('#1976D2')} disabled={loading}>
          {loading ? '読み込み中...' : '更新'}
        </button>
      </div>

      {/* ── 一覧テーブル ── */}
      <table style={tableStyle}>
        <thead>
          <tr style={{ backgroundColor: '#f5f5f5' }}>
            <th style={thStyle}>デバイスID</th>
            <th style={thStyle}>ユーザー名</th>
            <th style={thStyle}>端末モデル</th>
            <th style={thStyle}>登録日時</th>
            <th style={{ ...thStyle, textAlign: 'center' }}>操作</th>
          </tr>
        </thead>
        <tbody>
          {loading && devices.length === 0 && (
            <tr><td colSpan={5} style={{ ...tdStyle, textAlign: 'center', color: '#888' }}>読み込み中...</td></tr>
          )}
          {!loading && devices.length === 0 && !error && (
            <tr><td colSpan={5} style={{ ...tdStyle, textAlign: 'center', color: '#888' }}>デバイスが登録されていません</td></tr>
          )}
          {devices.map((d, i) =>
            editingId === d.deviceId ? (
              // ── 編集行 ──
              <tr key={d.deviceId} style={{ backgroundColor: '#fffde7' }}>
                <td style={tdStyle}>{d.deviceId}</td>
                <td style={tdStyle}>
                  <input
                    style={{ ...inputStyle, width: '100%' }}
                    value={editForm.userName}
                    onChange={e => setEditForm(f => ({ ...f, userName: e.target.value }))}
                  />
                </td>
                <td style={tdStyle}>
                  <input
                    style={{ ...inputStyle, width: '100%' }}
                    value={editForm.deviceModel}
                    onChange={e => setEditForm(f => ({ ...f, deviceModel: e.target.value }))}
                  />
                </td>
                <td style={tdStyle}>{new Date(d.createdAt).toLocaleString('ja-JP')}</td>
                <td style={{ ...tdStyle, textAlign: 'center', whiteSpace: 'nowrap' }}>
                  <button style={btnStyle('#388E3C')} onClick={() => updateDevice(d.deviceId)} disabled={submitting}>
                    {submitting ? '保存中...' : '保存'}
                  </button>{' '}
                  <button style={btnStyle('#757575')} onClick={() => setEditingId(null)}>キャンセル</button>
                </td>
              </tr>
            ) : (
              // ── 通常行 ──
              <tr key={d.deviceId} style={{ backgroundColor: i % 2 === 0 ? '#fff' : '#fafafa' }}>
                <td style={{ ...tdStyle, fontFamily: 'monospace' }}>{d.deviceId}</td>
                <td style={tdStyle}>{d.userName || '—'}</td>
                <td style={tdStyle}>{d.deviceModel || '—'}</td>
                <td style={{ ...tdStyle, whiteSpace: 'nowrap' }}>{new Date(d.createdAt).toLocaleString('ja-JP')}</td>
                <td style={{ ...tdStyle, textAlign: 'center', whiteSpace: 'nowrap' }}>
                  <button style={btnStyle('#1976D2')} onClick={() => startEdit(d)}>編集</button>{' '}
                  <button style={btnStyle('#D32F2F')} onClick={() => deleteDevice(d.deviceId)}>削除</button>
                </td>
              </tr>
            )
          )}
        </tbody>
      </table>
    </div>
  );
}

// ── スタイル定数 ──────────────────────────────────────

const cardStyle: React.CSSProperties = {
  border: '1px solid #e0e0e0',
  borderRadius: '6px',
  padding: '16px',
  marginBottom: '16px',
  backgroundColor: '#fafafa',
};
const sectionTitleStyle: React.CSSProperties = {
  margin: '0 0 12px',
  fontSize: '14px',
  fontWeight: 600,
  color: '#333',
};
const labelStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  fontSize: '13px',
  color: '#555',
};
const inputStyle: React.CSSProperties = {
  padding: '6px 10px',
  border: '1px solid #ccc',
  borderRadius: '4px',
  fontSize: '13px',
  minWidth: '160px',
};
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
  backgroundColor: '#f5f5f5',
};
const tdStyle: React.CSSProperties = {
  padding: '10px 12px',
  borderBottom: '1px solid #e0e0e0',
};
const btnStyle = (bg: string): React.CSSProperties => ({
  padding: '5px 12px',
  backgroundColor: bg,
  color: '#fff',
  border: 'none',
  borderRadius: '4px',
  cursor: 'pointer',
  fontSize: '13px',
});
