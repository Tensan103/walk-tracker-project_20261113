package com.example.walkdetection_20260818

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 画面ON/OFFイベントを受信して SensorTrackingService を自動制御する BroadcastReceiver。
 *
 * Android の OS 仕様により、ACTION_SCREEN_ON / ACTION_SCREEN_OFF は
 * AndroidManifest.xml への静的登録ができない。
 * そのため WalkTrackerApplication.onCreate() で動的に登録し、
 * アプリプロセスが生存している間は常に受信できる状態を保つ。
 *
 * 動作:
 *   - SCREEN_ON → SensorTrackingService (ACTION_START) を startForegroundService
 *   - SCREEN_OFF → SensorTrackingService を stopService
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                if (!SensorStateRepository.isMonitoringEnabled.value) {
                    Log.d(TAG, "画面ON: 監視無効のためサービスを起動しません")
                    return
                }
                Log.d(TAG, "画面ON: SensorTrackingService を起動します")
                val serviceIntent = Intent(context, SensorTrackingService::class.java).apply {
                    action = SensorTrackingService.ACTION_START
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    // Android 12+ のバックグラウンド起動制限により失敗する場合あり
                    Log.e(TAG, "サービス起動失敗: ${e.message}")
                }
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "画面OFF: SensorTrackingService を停止します")
                // stopService はサービスが実行中でなくても安全 (no-op)
                val serviceIntent = Intent(context, SensorTrackingService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }
}
