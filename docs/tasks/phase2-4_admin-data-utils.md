# タスク: 管理画面のデータ処理ロジック・共通ページネーション実装とVitest導入 (Step 2-4)

## 1. 目的と背景
次ステップ以降で実装する「タイムライン」および「実績」画面の基盤として、データ変換・集計ロジックを純粋関数として切り出し、共通のページネーションコンポーネントを作成する。
また、ペアリングやJST日付境界などのバグが起きやすい処理を自動検証できるよう、`/admin-web-viewer` に **Vitest** によるユニットテスト環境を導入する。
※本タスクではバックエンドAPIおよび既存画面のUIは変更しない。

## 2. 対象コンポーネント
- `/admin-web-viewer` 配下のファイル
  - `package.json` (Vitestの追加とテストスクリプト定義)
  - `src/utils/eventProcessing.ts` (新規作成: ペアリング・フォーマット・JST判定・実績集計)
  - `src/utils/eventProcessing.test.ts` (新規作成: 上記関数のユニットテスト)
  - `src/components/Pagination.tsx` (新規作成: 20件ページネーション共通部品)

## 3. 実装要件

### 3-1. Vitest の導入
- `/admin-web-viewer` に `vitest` を devDependencies としてインストールする。
- `package.json` の `scripts` に以下を追加する。
  - `"test": "vitest run"`
  - `"test:watch": "vitest"`
- 既存の `npm run build`（TypeScriptビルド）に影響が出ないよう、`tsconfig.json` 等を必要に応じて確認・調整すること。

### 3-2. データ処理ロジック (`src/utils/eventProcessing.ts`)
既存の `WalkEvent` / `Device` 型（必要に応じて共通型ファイルへ整理）を利用し、以下の純粋関数を実装する。

1. **イベントのペアリング (`pairWalkEvents`)**
   - 入力: `WalkEvent[]`
   - 同一 `deviceId` ごとにグループ化し、各デバイス内で `timestamp` 昇順に並べ替えて走査する。
   - `DETECT` の直後に `RELEASE` があれば1つのペア（`PairedWalkEvent`）としてまとめる。
     - プロパティ例: `deviceId`, `userName`, `deviceModel`, `detectTimestamp`, `releaseTimestamp: number | null`, `durationMs: number | null`
   - **異常系1（連続 `DETECT` 等）**: 対になる `RELEASE` が存在しない `DETECT` は、`releaseTimestamp: null`, `durationMs: null` として1行生成する。
   - **異常系2（単独 `RELEASE`）**: 対になる `DETECT` が存在しない `RELEASE` は無視し、結果配列に含めない。
   - 戻り値はデフォルトで `detectTimestamp` の降順（新しい順）にソートして返す。

2. **経過時間のフォーマット (`formatDuration`)**
   - 入力: `durationMs: number | null`
   - `null` の場合は半角ハイフン `"-"` を返す。
   - 0以上のミリ秒は秒単位（切り捨てまたは四捨五入で統一）に換算し、時分秒形式で返す。
     - 例: `45000` -> `"45秒"`, `135000` -> `"2分15秒"`, `3665000` -> `"1時間1分5秒"`, `0` -> `"0秒"`

3. **JST基準の日付範囲・当月計算 (`getCurrentMonthRangeJst`, `isWithinJstDateRange`)**
   - `getCurrentMonthRangeJst(now?: Date)`: 現在時刻（JST）の当月初日〜末日を `<input type="date">` 用の `{ startDate: "YYYY-MM-DD", endDate: "YYYY-MM-DD" }` で返す。
   - `isWithinJstDateRange(timestampMs: number, startDate?: string, endDate?: string)`:
     - `startDate` が指定されていれば、JSTの `startDate 00:00:00.000` 以上であることを判定。
     - `endDate` が指定されていれば、JSTの `endDate 23:59:59.999` 以下であることを判定。
     - 実行環境のタイムゾーンに依存しないよう、`+09:00` を明示したパースまたはUTCオフセット計算で厳密にJST境界を扱うこと。

4. **実績集計 (`aggregateDeviceStats`)**
   - 入力: `events: WalkEvent[]`, `devices: Device[]`, `startDate?: string`, `endDate?: string`
   - 指定期間（JST境界）内の `category === "DETECT"` のみを対象に、`deviceId` ごとの検知件数を集計する。
   - **0件デバイスの扱い**: `devices` に登録されているデバイスは、検知件数が `0` でも必ず結果に含める。
   - **未登録デバイスの扱い**: `devices` に存在しない `deviceId` で検知があった場合も結果に含める（`userName` はイベント側の値または `"未登録"`、`deviceModel` は `"-"`）。
   - 戻り値型（`DeviceStat`）: `deviceId`, `userName`, `deviceModel`, `detectCount`

5. **ソート時の `null` / `"-"` 末尾固定ヘルパー (`compareNullable`)**
   - 画面1のソートで、`releaseTimestamp` や `durationMs` が `null`（表示上 `"-"`）の行が昇順・降順にかかわらず**常に末尾に来る**比較ヘルパー関数を用意する。

### 3-3. ユニットテスト (`src/utils/eventProcessing.test.ts`)
以下のケースを網羅するテストを作成し、`npm test` がすべてパスすること。
- `pairWalkEvents`:
  - 正常系（DETECT → RELEASE のペアリングと差分計算）
  - 複数端末（Device A / Device B）のイベントが時系列で交錯している場合の正しいペアリング
  - 異常系1（DETECT が2回連続し、2回目の後に RELEASE が来るケース）
  - 異常系2（先頭や途中に単独の RELEASE がある場合に無視されること）
- `formatDuration`: `null` (`"-"`), `0秒`, `45秒`, `2分15秒`, `1時間以上` の各パターン
- `isWithinJstDateRange`:
  - JST `00:00:00.000`（境界内）と前日 `23:59:59.999`（境界外）
  - JST `23:59:59.999`（境界内）と翌日 `00:00:00.000`（境界外）
- `aggregateDeviceStats`:
  - `DETECT` のみカウントされ `RELEASE` はカウントされないこと
  - 期間内0件の登録済みデバイスが `detectCount: 0` で含まれること
  - 未登録 `deviceId` の検知も含まれること
- `compareNullable`: 昇順・降順どちらでも `null` が末尾に配置されること

### 3-4. 共通ページネーション部品 (`src/components/Pagination.tsx`)
- Props例: `currentPage: number` (1始まり), `totalItems: number`, `pageSize?: number` (デフォルト `20`), `onPageChange: (page: number) => void`
- 表示内容:
  - 「前へ」「次へ」ボタン（先頭/末尾ページでは `disabled`）
  - 現在のページ / 総ページ数、および表示件数（例: `1〜20件 / 全45件`）
- `totalItems === 0` の場合でもエラーにならず適切に表示（または非表示）されること。

## 4. 実行手順
1. ブランチ `feature/admin-data-utils` を作成して切り替える。
2. Vitest をインストールし、`package.json` にテストスクリプトを追加する。
3. `src/utils/eventProcessing.ts` と `src/components/Pagination.tsx` を実装する。
4. `src/utils/eventProcessing.test.ts` を実装し、`cd admin-web-viewer && npm test` で全テストがパスすることを確認する。
5. `npm run build` でTypeScriptビルドエラーがないことを確認する。
6. 変更をコミットし、テスト実行結果とともに完了を報告する。