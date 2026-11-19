// ---- Shared types ----

export type WalkEvent = {
  deviceId: string;
  timestamp: number;
  category: string;
  userName: string;
  deviceModel: string;
};

export type Device = {
  deviceId: string;
  userName: string;
  deviceModel: string;
  status: string;
  createdAt: number;
};

export type PairedWalkEvent = {
  deviceId: string;
  userName: string;
  deviceModel: string;
  detectTimestamp: number;
  releaseTimestamp: number | null;
  durationMs: number | null;
};

export type DeviceStat = {
  deviceId: string;
  userName: string;
  deviceModel: string;
  detectCount: number;
};

// ---- pairWalkEvents ----

/**
 * WalkEvent[] を deviceId ごとにペアリングし PairedWalkEvent[] として返す。
 * - DETECT の直後が RELEASE なら1ペアにまとめる。
 * - 対応 RELEASE のない DETECT は releaseTimestamp/durationMs = null で1行生成。
 * - 対応 DETECT のない RELEASE は無視する。
 * - 戻り値は detectTimestamp 降順（新しい順）。
 */
export function pairWalkEvents(events: WalkEvent[]): PairedWalkEvent[] {
  const byDevice = new Map<string, WalkEvent[]>();
  for (const ev of events) {
    const list = byDevice.get(ev.deviceId);
    if (list) {
      list.push(ev);
    } else {
      byDevice.set(ev.deviceId, [ev]);
    }
  }

  const result: PairedWalkEvent[] = [];

  for (const [deviceId, deviceEvents] of byDevice) {
    const sorted = [...deviceEvents].sort((a, b) => a.timestamp - b.timestamp);
    let i = 0;
    while (i < sorted.length) {
      const ev = sorted[i];
      if (ev.category === 'DETECT') {
        const next = sorted[i + 1];
        if (next !== undefined && next.category === 'RELEASE') {
          result.push({
            deviceId,
            userName: ev.userName,
            deviceModel: ev.deviceModel,
            detectTimestamp: ev.timestamp,
            releaseTimestamp: next.timestamp,
            durationMs: next.timestamp - ev.timestamp,
          });
          i += 2;
        } else {
          result.push({
            deviceId,
            userName: ev.userName,
            deviceModel: ev.deviceModel,
            detectTimestamp: ev.timestamp,
            releaseTimestamp: null,
            durationMs: null,
          });
          i += 1;
        }
      } else {
        // 孤立 RELEASE は無視
        i += 1;
      }
    }
  }

  return result.sort((a, b) => b.detectTimestamp - a.detectTimestamp);
}

// ---- formatDuration ----

/**
 * ミリ秒を「X時間Y分Z秒」形式にフォーマットする。null は "-" を返す。
 */
export function formatDuration(durationMs: number | null): string {
  if (durationMs === null) return '-';
  const totalSec = Math.floor(durationMs / 1000);
  const hours = Math.floor(totalSec / 3600);
  const minutes = Math.floor((totalSec % 3600) / 60);
  const seconds = totalSec % 60;
  if (hours > 0) return `${hours}時間${minutes}分${seconds}秒`;
  if (minutes > 0) return `${minutes}分${seconds}秒`;
  return `${seconds}秒`;
}

// ---- JST date utilities ----

const JST_OFFSET_MS = 9 * 60 * 60 * 1000;

/**
 * 現在時刻（JST）の当月初日〜末日を YYYY-MM-DD 形式で返す。
 */
export function getCurrentMonthRangeJst(now?: Date): { startDate: string; endDate: string } {
  const d = now ?? new Date();
  const jstDate = new Date(d.getTime() + JST_OFFSET_MS);
  const year = jstDate.getUTCFullYear();
  const month = jstDate.getUTCMonth(); // 0-indexed
  const monthStr = String(month + 1).padStart(2, '0');
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  return {
    startDate: `${year}-${monthStr}-01`,
    endDate: `${year}-${monthStr}-${String(lastDay).padStart(2, '0')}`,
  };
}

/**
 * timestampMs が JST 基準の日付範囲内かどうかを判定する。
 * startDate: JST 当日 00:00:00.000 以上、endDate: JST 当日 23:59:59.999 以下で判定。
 * 実行環境のタイムゾーンに依存しないよう +09:00 オフセットを明示してパースする。
 */
export function isWithinJstDateRange(
  timestampMs: number,
  startDate?: string,
  endDate?: string
): boolean {
  if (startDate !== undefined) {
    const startMs = new Date(`${startDate}T00:00:00.000+09:00`).getTime();
    if (timestampMs < startMs) return false;
  }
  if (endDate !== undefined) {
    const endMs = new Date(`${endDate}T23:59:59.999+09:00`).getTime();
    if (timestampMs > endMs) return false;
  }
  return true;
}

// ---- aggregateDeviceStats ----

/**
 * 指定期間（JST境界）内の DETECT 件数をデバイスごとに集計する。
 * - 登録済みデバイスは 0 件でも必ず含める。
 * - 未登録 deviceId の検知も含める（userName はイベント側の値、deviceModel は "-"）。
 */
export function aggregateDeviceStats(
  events: WalkEvent[],
  devices: Device[],
  startDate?: string,
  endDate?: string
): DeviceStat[] {
  const deviceMap = new Map(devices.map(d => [d.deviceId, d]));
  const countMap = new Map<string, number>();

  for (const d of devices) {
    countMap.set(d.deviceId, 0);
  }

  const unregisteredNames = new Map<string, string>();

  for (const ev of events) {
    if (ev.category !== 'DETECT') continue;
    if (!isWithinJstDateRange(ev.timestamp, startDate, endDate)) continue;
    countMap.set(ev.deviceId, (countMap.get(ev.deviceId) ?? 0) + 1);
    if (!deviceMap.has(ev.deviceId)) {
      unregisteredNames.set(ev.deviceId, ev.userName);
    }
  }

  return Array.from(countMap.entries()).map(([deviceId, detectCount]) => {
    const device = deviceMap.get(deviceId);
    if (device !== undefined) {
      return { deviceId, userName: device.userName, deviceModel: device.deviceModel, detectCount };
    }
    return {
      deviceId,
      userName: unregisteredNames.get(deviceId) ?? '未登録',
      deviceModel: '-',
      detectCount,
    };
  });
}

// ---- compareNullable ----

/**
 * null を昇順・降順問わず常に末尾に配置するソート比較ヘルパー。
 * 降順ソートは compare 関数側で (a, b) => b - a のように反転させて使う。
 */
export function compareNullable<T>(
  a: T | null,
  b: T | null,
  compare: (a: T, b: T) => number
): number {
  if (a === null && b === null) return 0;
  if (a === null) return 1;
  if (b === null) return -1;
  return compare(a, b);
}
