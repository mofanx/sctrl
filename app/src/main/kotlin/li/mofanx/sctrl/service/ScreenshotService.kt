package li.mofanx.sctrl.service

import android.app.Service
import android.content.Intent
import coil3.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import li.mofanx.sctrl.app
import li.mofanx.sctrl.notif.StopServiceReceiver
import li.mofanx.sctrl.notif.screenshotNotif
import li.mofanx.sctrl.util.DefaultSimpleLifeImpl
import li.mofanx.sctrl.util.LogUtils
import li.mofanx.sctrl.util.OnSimpleLife
import li.mofanx.sctrl.util.ScreenshotUtil
import li.mofanx.sctrl.util.componentName
import li.mofanx.sctrl.util.stopServiceByClass

class ScreenshotService : Service(), OnSimpleLife by DefaultSimpleLifeImpl() {
    override fun onBind(intent: Intent?) = null
    override fun onCreate() = onCreated()
    override fun onDestroy() = onDestroyed()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            return super.onStartCommand(intent, flags, startId)
        } finally {
            intent?.let {
                screenshotUtil?.destroy()
                screenshotUtil = ScreenshotUtil(this, intent)
                LogUtils.d("screenshot restart")
            }
        }
    }

    private var screenshotUtil: ScreenshotUtil? = null

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)
        useAliveToast("截屏服务")
        StopServiceReceiver.autoRegister()
        onCreated { screenshotNotif.notifyService() }
        onCreated { instance = this }
        onDestroyed {
            screenshotUtil?.destroy()
            instance = null
        }
    }

    companion object {
        private var instance: ScreenshotService? = null
        val isRunning = MutableStateFlow(false)
        suspend fun screenshot(): Bitmap? {
            if (!isRunning.value) return null
            return withTimeoutOrNull(5_000) {
                instance?.screenshotUtil?.execute()
            }
        }

        fun start(intent: Intent) {
            intent.component = ScreenshotService::class.componentName
            app.startForegroundService(intent)
        }

        fun stop() = stopServiceByClass(ScreenshotService::class)
    }
}