package com.example.walkdetection_20260818

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SensorTrackingService と UI の間でリアルタイムにセンサー状態を共有するシングルトン。
 *
 * 状態は2種類:
 *  - サービス稼働状態 (isServiceRunning): センサー監視・歩行判定・AWS送信が行われているか
 *  - ロギング状態 (isLoggingEnabled): CSVファイルへの書き込みが有効かどうか
 *
 * これにより「歩行判定はON / CSVロギングはOFF」という状態を表現でき、
 * MainScreen と LoggerScreen の干渉を防ぐ。
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

    /** サービス開始時に呼び出し、前回の記録をリセットする。 */
    fun reset() {
        _accelValues.value = FloatArray(3)
        _stepCount.value = 0
        _isWalking.value = false
        _isLoggingEnabled.value = false
    }
}
