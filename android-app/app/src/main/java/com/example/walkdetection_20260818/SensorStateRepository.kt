package com.example.walkdetection_20260818

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SensorTrackingService と MainActivity UI の間でセンサー状態を共有するシングルトン。
 * StateFlow を使用することで Jetpack Compose の collectAsState() と自然に連携できる。
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

    // --- サービス稼働中フラグ ---
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

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

    /** サービス開始時に呼び出し、前回の記録をリセットする */
    fun reset() {
        _accelValues.value = FloatArray(3)
        _stepCount.value = 0
        _isWalking.value = false
    }
}
