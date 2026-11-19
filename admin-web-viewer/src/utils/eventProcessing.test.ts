import { describe, it, expect } from 'vitest';
import {
  pairWalkEvents,
  formatDuration,
  isWithinJstDateRange,
  aggregateDeviceStats,
  compareNullable,
} from './eventProcessing';
import type { WalkEvent, Device } from './eventProcessing';

// テストデータ生成ヘルパー
const makeEvent = (
  deviceId: string,
  timestamp: number,
  category: string,
  userName = '未登録',
  deviceModel = '-'
): WalkEvent => ({ deviceId, timestamp, category, userName, deviceModel });

const makeDevice = (deviceId: string, userName: string, deviceModel: string): Device => ({
  deviceId,
  userName,
  deviceModel,
  status: 'active',
  createdAt: 0,
});

// ============================================================
// pairWalkEvents
// ============================================================

describe('pairWalkEvents', () => {
  it('正常系: DETECT → RELEASE のペアリングと差分計算', () => {
    const events = [
      makeEvent('dev-A', 1000, 'DETECT', 'Alice', 'Pixel 7'),
      makeEvent('dev-A', 5000, 'RELEASE', 'Alice', 'Pixel 7'),
    ];
    const result = pairWalkEvents(events);
    expect(result).toHaveLength(1);
    expect(result[0].deviceId).toBe('dev-A');
    expect(result[0].userName).toBe('Alice');
    expect(result[0].detectTimestamp).toBe(1000);
    expect(result[0].releaseTimestamp).toBe(5000);
    expect(result[0].durationMs).toBe(4000);
  });

  it('複数端末のイベントが時系列で交錯している場合の正しいペアリング', () => {
    const events = [
      makeEvent('dev-A', 1000, 'DETECT', 'Alice'),
      makeEvent('dev-B', 1500, 'DETECT', 'Bob'),
      makeEvent('dev-A', 3000, 'RELEASE', 'Alice'),
      makeEvent('dev-B', 4000, 'RELEASE', 'Bob'),
    ];
    const result = pairWalkEvents(events);
    expect(result).toHaveLength(2);
    const a = result.find(r => r.deviceId === 'dev-A')!;
    const b = result.find(r => r.deviceId === 'dev-B')!;
    expect(a.durationMs).toBe(2000);
    expect(b.durationMs).toBe(2500);
  });

  it('異常系1: DETECT が2回連続し、2回目の後に RELEASE が来るケース', () => {
    const events = [
      makeEvent('dev-A', 1000, 'DETECT'),
      makeEvent('dev-A', 2000, 'DETECT'),
      makeEvent('dev-A', 5000, 'RELEASE'),
    ];
    const result = pairWalkEvents(events);
    expect(result).toHaveLength(2);
    const unmatched = result.find(r => r.releaseTimestamp === null)!;
    const matched = result.find(r => r.releaseTimestamp !== null)!;
    expect(unmatched.detectTimestamp).toBe(1000);
    expect(matched.detectTimestamp).toBe(2000);
    expect(matched.releaseTimestamp).toBe(5000);
    expect(matched.durationMs).toBe(3000);
  });

  it('異常系2: 先頭や途中に単独の RELEASE がある場合に無視されること', () => {
    const events = [
      makeEvent('dev-A', 500, 'RELEASE'),  // 孤立 RELEASE
      makeEvent('dev-A', 1000, 'DETECT'),
      makeEvent('dev-A', 3000, 'RELEASE'),
    ];
    const result = pairWalkEvents(events);
    expect(result).toHaveLength(1);
    expect(result[0].detectTimestamp).toBe(1000);
    expect(result[0].releaseTimestamp).toBe(3000);
  });

  it('戻り値は detectTimestamp 降順', () => {
    const events = [
      makeEvent('dev-A', 1000, 'DETECT'),
      makeEvent('dev-A', 2000, 'RELEASE'),
      makeEvent('dev-B', 3000, 'DETECT'),
      makeEvent('dev-B', 4000, 'RELEASE'),
    ];
    const result = pairWalkEvents(events);
    expect(result[0].detectTimestamp).toBeGreaterThan(result[1].detectTimestamp);
  });
});

// ============================================================
// formatDuration
// ============================================================

describe('formatDuration', () => {
  it('null → "-"', () => {
    expect(formatDuration(null)).toBe('-');
  });
  it('0ms → "0秒"', () => {
    expect(formatDuration(0)).toBe('0秒');
  });
  it('45000ms → "45秒"', () => {
    expect(formatDuration(45000)).toBe('45秒');
  });
  it('135000ms → "2分15秒"', () => {
    expect(formatDuration(135000)).toBe('2分15秒');
  });
  it('3665000ms → "1時間1分5秒"', () => {
    expect(formatDuration(3665000)).toBe('1時間1分5秒');
  });
  it('3600000ms (ちょうど1時間) → "1時間0分0秒"', () => {
    expect(formatDuration(3600000)).toBe('1時間0分0秒');
  });
});

// ============================================================
// isWithinJstDateRange
// ============================================================

describe('isWithinJstDateRange', () => {
  // 2024-01-15 00:00:00.000 JST (= 2024-01-14T15:00:00.000Z)
  const jst_start = new Date('2024-01-15T00:00:00.000+09:00').getTime();
  // 2024-01-14 23:59:59.999 JST
  const before_start = new Date('2024-01-14T23:59:59.999+09:00').getTime();
  // 2024-01-15 23:59:59.999 JST
  const jst_end = new Date('2024-01-15T23:59:59.999+09:00').getTime();
  // 2024-01-16 00:00:00.000 JST
  const after_end = new Date('2024-01-16T00:00:00.000+09:00').getTime();

  it('JST 00:00:00.000 は startDate の境界内', () => {
    expect(isWithinJstDateRange(jst_start, '2024-01-15')).toBe(true);
  });
  it('前日 23:59:59.999 は startDate の境界外', () => {
    expect(isWithinJstDateRange(before_start, '2024-01-15')).toBe(false);
  });
  it('JST 23:59:59.999 は endDate の境界内', () => {
    expect(isWithinJstDateRange(jst_end, undefined, '2024-01-15')).toBe(true);
  });
  it('翌日 00:00:00.000 は endDate の境界外', () => {
    expect(isWithinJstDateRange(after_end, undefined, '2024-01-15')).toBe(false);
  });
  it('startDate と endDate 両方指定で範囲内', () => {
    const mid = new Date('2024-01-15T12:00:00+09:00').getTime();
    expect(isWithinJstDateRange(mid, '2024-01-15', '2024-01-15')).toBe(true);
  });
  it('startDate も endDate も未指定なら常に true', () => {
    expect(isWithinJstDateRange(0)).toBe(true);
  });
});

// ============================================================
// aggregateDeviceStats
// ============================================================

describe('aggregateDeviceStats', () => {
  const devices: Device[] = [
    makeDevice('dev-A', 'Alice', 'Pixel 7'),
    makeDevice('dev-B', 'Bob', 'Galaxy S22'),
  ];

  it('DETECT のみカウントされ RELEASE はカウントされないこと', () => {
    const events = [
      makeEvent('dev-A', 1000, 'DETECT'),
      makeEvent('dev-A', 5000, 'RELEASE'),
      makeEvent('dev-A', 8000, 'DETECT'),
    ];
    const result = aggregateDeviceStats(events, devices);
    const a = result.find(r => r.deviceId === 'dev-A')!;
    expect(a.detectCount).toBe(2);
  });

  it('期間内0件の登録済みデバイスが detectCount: 0 で含まれること', () => {
    const events = [makeEvent('dev-A', 1000, 'DETECT')];
    const result = aggregateDeviceStats(events, devices);
    const b = result.find(r => r.deviceId === 'dev-B')!;
    expect(b).toBeDefined();
    expect(b.detectCount).toBe(0);
    expect(b.userName).toBe('Bob');
  });

  it('未登録 deviceId の検知も結果に含まれること', () => {
    const events = [makeEvent('dev-X', 1000, 'DETECT', 'Xユーザー')];
    const result = aggregateDeviceStats(events, devices);
    const x = result.find(r => r.deviceId === 'dev-X')!;
    expect(x).toBeDefined();
    expect(x.detectCount).toBe(1);
    expect(x.deviceModel).toBe('-');
  });

  it('日付フィルタ: 範囲外の DETECT はカウントしない', () => {
    // 2024-01-15 JST の範囲でフィルタ
    const inRange = new Date('2024-01-15T10:00:00+09:00').getTime();
    const outRange = new Date('2024-01-14T10:00:00+09:00').getTime();
    const events = [
      makeEvent('dev-A', inRange, 'DETECT'),
      makeEvent('dev-A', outRange, 'DETECT'),
    ];
    const result = aggregateDeviceStats(events, devices, '2024-01-15', '2024-01-15');
    const a = result.find(r => r.deviceId === 'dev-A')!;
    expect(a.detectCount).toBe(1);
  });
});

// ============================================================
// compareNullable
// ============================================================

describe('compareNullable', () => {
  const numCmp = (a: number, b: number) => a - b;

  it('昇順: null は末尾に配置される', () => {
    const arr: (number | null)[] = [null, 3, 1, null, 2];
    const sorted = [...arr].sort((a, b) => compareNullable(a, b, numCmp));
    expect(sorted).toEqual([1, 2, 3, null, null]);
  });

  it('降順: null は末尾に配置される', () => {
    const arr: (number | null)[] = [null, 3, 1, null, 2];
    const sorted = [...arr].sort((a, b) => compareNullable(a, b, (x, y) => y - x));
    expect(sorted).toEqual([3, 2, 1, null, null]);
  });

  it('両方 null なら 0 を返す', () => {
    expect(compareNullable<number>(null, null, numCmp)).toBe(0);
  });
});
