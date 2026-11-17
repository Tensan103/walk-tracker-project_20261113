import { useEffect, useState } from 'react';

// DynamoDBから返ってくるデータの型
type WalkEvent = {
  deviceId: string;
  timestamp: number;
  category: string;
};

function App() {
  const [events, setEvents] = useState<WalkEvent[]>([]);

  // CDK出力の "ApiUrl" に "/events" を追加
  const API_URL = "https://4ss98ipt4m.execute-api.ap-northeast-3.amazonaws.com/prod/events";
  // Outputs:
  // BackendStack.ApiUrl = https://4ss98ipt4m.execute-api.ap-northeast-3.amazonaws.com/prod/
  // BackendStack.CloudFrontUrl = https://d2c7vkr5tsw06w.cloudfront.net
  // BackendStack.WalkEventsApiEndpoint083EAA71 = https://4ss98ipt4m.execute-api.ap-northeast-3.amazonaws.com/prod/
  // Stack ARN:
  // arn:aws:cloudformation:ap-northeast-3:704243371083:stack/BackendStack/dbf5b330-e2b0-11f1-a4cf-0e91d5596fc1

  const fetchEvents = async () => {
    try {
      const response = await fetch(API_URL);
      const data = await response.json();
      setEvents(data);
    } catch (error) {
      console.error("Failed to fetch data:", error);
    }
  };

  useEffect(() => {
    fetchEvents();
  }, []);

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif' }}>
      <h2>歩行検知ダッシュボード</h2>
      <button onClick={fetchEvents} style={{ marginBottom: '10px' }}>
        最新データに更新
      </button>

      <table border={1} cellPadding={8} style={{ borderCollapse: 'collapse', width: '100%' }}>
        <thead>
          <tr style={{ backgroundColor: '#f0f0f0' }}>
            <th>デバイスID</th>
            <th>イベント</th>
            <th>検知日時 (JST)</th>
          </tr>
        </thead>
        <tbody>
          {events.map((ev, index) => (
            <tr key={index}>
              <td>{ev.deviceId}</td>
              <td style={{ color: ev.category === 'DETECT' ? 'red' : 'blue' }}>
                {ev.category === 'DETECT' ? '歩行開始 (DETECT)' : '待機に戻る (RELEASE)'}
              </td>
              <td>{new Date(ev.timestamp).toLocaleString('ja-JP')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default App;