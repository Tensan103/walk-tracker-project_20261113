package com.example.walkdetection_20260818

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 歩行検知時の警告動作の種別 */
enum class AlertType {
    /** 何もしない */
    NONE,
    /** トースト通知 */
    TOAST,
    /** Full-Screen Intent 付き高優先度通知でアプリを前面に強制起動 (Android 14+ は要権限) */
    FULL_SCREEN_INTENT,
    /** WindowManager TYPE_APPLICATION_OVERLAY で最前面に警告View を表示 (要 SYSTEM_ALERT_WINDOW 権限) */
    SYSTEM_ALERT_WINDOW
}

/**
 * 1件の歩行検知セッションを表すデータクラス。
 *
 * @param detectTimeMs       DETECT が発生した時刻 (System.currentTimeMillis())
 * @param releaseTimeMs      RELEASE が発生した時刻 (最後の歩行から rSec 秒後)
 * @param durationSeconds    検知から解除までの経過秒数
 * @param stepsDuringWalking DETECT 発生時点から RELEASE 直前までの歩数差分
 *                           （検知中に何歩歩いたか）
 */
data class WalkHistoryItem(
    val detectTimeMs: Long,
    val releaseTimeMs: Long,
    val durationSeconds: Int,
    val stepsDuringWalking: Int
)

/**
 * SensorTrackingService と UI の間でリアルタイムにセンサー状態を共有するシングルトン。
 *
 * 状態は3種類:
 *  - サービス稼働状態 (isServiceRunning): センサー監視・歩行判定・AWS送信が行われているか
 *  - ロギング状態 (isLoggingEnabled): CSVファイルへの書き込みが有効かどうか
 *  - 履歴 (walkHistory): 過去の歩行検知セッション一覧（最新が先頭）
 */
object SensorStateRepository {

    // --- 加速度センサーの現在値 (X, Y, Z) ---
    private val _accelValues = MutableStateFlow(FloatArray(3))
    val accelValues: StateFlow<FloatArray> = _accelValues.asStateFlow()

    // --- STEP_DETECTOR による累計歩数 ---
    private val _stepCount = MutableStateFlow(0)
    val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

    // --- 歩行検知状態 (DETECT/RELEASE ロジックに連動) ---
    private val _isWalking = MutableStateFlow(false)
    val isWalking: StateFlow<Boolean> = _isWalking.asStateFlow()

    // --- サービス稼働中フラグ (センサー監視・歩行判定が動いているか) ---
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    // --- CSVロギング有効フラグ (LoggerScreen の「記録開始/停止」に連動) ---
    private val _isLoggingEnabled = MutableStateFlow(false)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    // --- 歩行検知履歴 (最新が先頭、アプリプロセスが生きている間は保持) ---
    private val _walkHistory = MutableStateFlow<List<WalkHistoryItem>>(emptyList())
    val walkHistory: StateFlow<List<WalkHistoryItem>> = _walkHistory.asStateFlow()

    // --- 歩行検知時の警告設定 ---
    private val _alertType = MutableStateFlow(AlertType.NONE)
    val alertType: StateFlow<AlertType> = _alertType.asStateFlow()

    // --- 監視機能の有効/無効フラグ (初期値 true、SharedPreferences で永続化) ---
    private val _isMonitoringEnabled = MutableStateFlow(true)
    val isMonitoringEnabled: StateFlow<Boolean> = _isMonitoringEnabled.asStateFlow()

    fun updateAccel(values: FloatArray) {
        _accelValues.value = values.clone()
    }

    fun updateStepCount(count: Int) {
        _stepCount.value = count
    }

    fun updateIsWalking(walking: Boolean) {
        _isWalking.value = walking
    }

    fun updateServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun updateLoggingEnabled(enabled: Boolean) {
        _isLoggingEnabled.value = enabled
    }

    /**
     * 新しい歩行検知履歴をリストの先頭に追加する。
     * (スレッドセーフ: StateFlow の書き込みは内部でアトミックに処理される)
     */
    fun addWalkHistoryItem(item: WalkHistoryItem) {
        _walkHistory.value = listOf(item) + _walkHistory.value
    }

    fun updateAlertType(type: AlertType) {
        _alertType.value = type
    }

    fun updateMonitoringEnabled(enabled: Boolean) {
        _isMonitoringEnabled.value = enabled
    }

    /** Application.onCreate() で呼び出し、SharedPreferences から AlertType と監視フラグを復元する。 */
    fun init(context: Context) {
        _alertType.value = SettingsRepository.getAlertType(context)
        _isMonitoringEnabled.value = SettingsRepository.getMonitoringEnabled(context)
    }

    /**
     * サービス開始時に呼び出し、センサー値・歩数・歩行状態をリセットする。
     * 履歴 (walkHistory) はリセットしない（プロセスが続く限り蓄積）。
     */
    fun reset() {
        _accelValues.value = FloatArray(3)
        _stepCount.value = 0
        _isWalking.value = false
        _isLoggingEnabled.value = false
    }
}
