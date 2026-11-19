package com.example.walkdetection_20260818

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * アプリケーションクラス。
 *
 * プロセスが起動してからプロセスが終了するまで ScreenStateReceiver を動的登録し続けることで、
 * どの画面が表示されていても画面ON/OFFイベントを確実に捕捉できる。
 *
 * また、アプリ起動時点で画面がON状態であれば SensorTrackingService を即座に開始する。
 * （アプリ起動後の最初の SCREEN_ON イベントは受け取れないため）
 *
 * AndroidManifest.xml に android:name=".WalkTrackerApplication" の登録が必要。
 */
class WalkTrackerApplication : Application() {

    /** サービスのライフサイクルを超えて生存する長寿命スコープ。onDestroy() での RELEASE 送信に使用する。 */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val screenStateReceiver = ScreenStateReceiver()

    override fun onCreate() {
        super.onCreate()
        SensorStateRepository.init(this)  // SharedPreferences から AlertType・監視フラグを復元
        registerScreenStateReceiver()
        startServiceIfScreenIsOn()
    }

    /**
     * ACTION_SCREEN_ON / ACTION_SCREEN_OFF を動的に登録する。
     * Android 13 (API 33) 以降は RECEIVER_NOT_EXPORTED の指定が必要。
     * ContextCompat.registerReceiver が API レベルの差分を吸収する。
     */
    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED  // 保護ブロードキャストのため EXPORTED 不要
        )
        Log.d(TAG, "ScreenStateReceiver 登録完了 (動的登録)")
    }

    /**
     * アプリ起動時点の画面状態を確認し、ON であればサービスを即座に起動する。
     * PowerManager.isInteractive() = true のとき画面は ON 状態。
     */
    private fun startServiceIfScreenIsOn() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isInteractive && SensorStateRepository.isMonitoringEnabled.value) {
            Log.d(TAG, "起動時に画面ON を検出: SensorTrackingService を起動します")
            val intent = Intent(this, SensorTrackingService::class.java).apply {
                action = SensorTrackingService.ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "起動時サービス起動失敗: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "WalkTrackerApp"
    }

    // onTerminate はプロダクション環境では呼ばれないが、シミュレーター用に記述
    override fun onTerminate() {
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        super.onTerminate()
    }
}
