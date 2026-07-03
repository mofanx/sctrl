package li.mofanx.sctrl.shizuku

import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.DataOutputStream
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 屏幕电源控制帮助类
 *
 * 通过反射调用 android.view.SurfaceControl.setDisplayPowerMode() 实现熄屏/亮屏。
 * 需要运行在 shell/root 权限进程（如 Shizuku UserService）中，因为调用需要
 * ACCESS_SURFACE_FLINGER 权限。
 */
object ScreenControlHelper {

    private const val TAG = "ScreenControlHelper"
    private const val POWER_MODE_OFF = 0
    private const val POWER_MODE_NORMAL = 2

    // 守护模式：持续保持屏幕关闭
    private val keepScreenOff = AtomicBoolean(false)
    private var guardThread: Thread? = null

    /**
     * 查询当前是否处于持续息屏模式
     */
    fun isKeepingScreenOff(): Boolean = keepScreenOff.get()

    /**
     * 启动/停止屏幕关闭守护
     * 开启后会每 2 秒检查屏幕状态，如果屏幕被系统唤醒则重新关闭
     */
    private fun startScreenOffGuard() {
        if (guardThread?.isAlive == true) return
        keepScreenOff.set(true)
        guardThread = Thread({
            Log.d(TAG, "screen off guard started")
            while (keepScreenOff.get()) {
                try {
                    Thread.sleep(1500)
                    if (!keepScreenOff.get()) break
                    // 检查屏幕是否已被唤醒（通过查询 display state）
                    val state = execShell("dumpsys display | grep 'mScreenState'")
                        .result.trim()
                    // mScreenState=ON 表示屏幕已亮
                    if (state.contains("ON", ignoreCase = true)) {
                        Log.d(TAG, "screen was woken up, re-applying power-off")
                        // 重新关屏
                        val ok = trySetDisplayPowerModeViaSurfaceControl(true)
                        if (!ok) {
                            execShell("cmd display power-off 0")
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Throwable) {
                    Log.d(TAG, "guard thread error", e)
                }
            }
            Log.d(TAG, "screen off guard stopped")
        }, "ScreenOffGuard").apply { isDaemon = true; start() }
    }

    private fun stopScreenOffGuard() {
        keepScreenOff.set(false)
        guardThread?.interrupt()
        guardThread = null
    }

    // Android 14+ 需要从 services.jar 加载 DisplayControl 获取 display token
    // 因为 SurfaceControl.getInternalDisplayToken() 从 Android 14 开始被移除
    private val useDisplayControl: Boolean
        get() = Build.VERSION.SDK_INT >= 34

    private val surfaceControlClass by lazy {
        Class.forName("android.view.SurfaceControl")
    }

    private var displayControlClass: Class<*>? = null
    private var getBuiltInDisplayMethod: Method? = null
    private var getInternalDisplayTokenMethod: Method? = null
    private var getPhysicalDisplayIdsMethod: Method? = null
    private var getPhysicalDisplayTokenMethod: Method? = null
    private var displayControlGetPhysicalDisplayIdsMethod: Method? = null
    private var displayControlGetPhysicalDisplayTokenMethod: Method? = null
    private var setDisplayPowerModeMethod: Method? = null

    init {
        // Android 14+ 都需要加载 DisplayControl 来获取 display token
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                loadDisplayControlClass()
            } catch (e: Throwable) {
                Log.d(TAG, "loadDisplayControlClass failed", e)
            }
        }
    }

    /**
     * 设置屏幕电源模式
     *
     * 按优先级依次尝试：
     * 1. cmd display power-off/power-on（Android 15+，走 DisplayManagerService）
     * 2. SurfaceControl.setDisplayPowerMode() 反射（Android ≤14）
     * 3. settings put system screen_brightness（最终 fallback）
     *
     * @param turnOff true 熄屏，false 亮屏
     * @return 是否成功
     */
    fun setDisplayPowerMode(turnOff: Boolean): Boolean {
        // 1. 优先尝试 SurfaceControl.setDisplayPowerMode()（硬件级，最稳定）
        //    Android 14+ 通过 DisplayControl 获取 token，Android <14 用旧 API
        val surfaceControlOk = trySetDisplayPowerModeViaSurfaceControl(turnOff)
        if (surfaceControlOk) {
            // 硬件级关屏成功，不需要守护线程
            if (turnOff) keepScreenOff.set(true) else stopScreenOffGuard()
            return true
        }

        // 2. Android 15+：cmd display power-off/power-on（软件级，需要守护）
        if (Build.VERSION.SDK_INT >= 35) {
            Log.d(TAG, "SurfaceControl failed, trying cmd display")
            val cmdOk = setDisplayPowerModeViaCmdDisplay(turnOff)
            if (cmdOk) return true
            Log.d(TAG, "cmd display power-off/on failed")
        }

        // 3. 最终 fallback：settings 修改亮度
        Log.d(TAG, "All methods failed, fallback to settings brightness")
        return setDisplayPowerModeViaSettingsBrightness(turnOff)
    }

    private fun trySetDisplayPowerModeViaSurfaceControl(turnOff: Boolean): Boolean {
        return try {
            val mode = if (turnOff) POWER_MODE_OFF else POWER_MODE_NORMAL
            val tokens = getDisplayTokens()
            if (tokens.isEmpty()) {
                Log.d(TAG, "no display token found")
                return false
            }
            var allOk = true
            for (token in tokens) {
                allOk = allOk && invokeSetDisplayPowerMode(token, mode)
            }
            Log.d(TAG, "setDisplayPowerMode via SurfaceControl turnOff=$turnOff, ok=$allOk")
            allOk
        } catch (e: Throwable) {
            Log.d(TAG, "setDisplayPowerMode via SurfaceControl failed", e)
            false
        }
    }

    // 保存熄屏前的 screen_off_timeout，用于恢复
    private var savedScreenOffTimeout: String = ""

    /**
     * 通过 cmd display power-off/power-on 控制屏幕电源
     *
     * Android 15 (SDK 35) 起可用，走 DisplayManagerService，
     * 真正关闭屏幕硬件而不触发系统锁屏。
     *
     * 为防止系统自动唤醒屏幕（触摸、通知等），熄屏时同步：
     * - 将 screen_off_timeout 设为最大值，阻止超时唤醒
     * - 设置 stay_on_while_plugged_in，防止充电时唤醒
     * 亮屏时恢复这些设置。
     */
    private fun setDisplayPowerModeViaCmdDisplay(turnOff: Boolean): Boolean {
        return try {
            if (turnOff) {
                // 保存当前 screen_off_timeout
                savedScreenOffTimeout = execShell("settings get system screen_off_timeout")
                    .result.trim()
                // 设置超长超时防止系统自动唤醒
                execShell("settings put system screen_off_timeout 2147483647")
                // 关闭屏幕
                val result = execShell("cmd display power-off 0")
                if (result.ok) {
                    // 启动守护线程，持续保持屏幕关闭
                    startScreenOffGuard()
                }
                Log.d(TAG, "screen off via cmd display power-off, ok=${result.ok}")
                result.ok
            } else {
                // 停止守护线程
                stopScreenOffGuard()
                // 开启屏幕
                val result = execShell("cmd display power-on 0")
                // 恢复原始 screen_off_timeout
                val timeout = savedScreenOffTimeout.toLongOrNull() ?: 120000L
                execShell("settings put system screen_off_timeout $timeout")
                Log.d(TAG, "screen on via cmd display power-on, ok=${result.ok}, restored timeout=$timeout")
                result.ok
            }
        } catch (e: Throwable) {
            Log.d(TAG, "setDisplayPowerMode via cmd display failed", e)
            false
        }
    }

    // 保存熄屏前的亮度和亮度模式，用于恢复（settings fallback 方案）
    private var savedBrightness: Int = -1
    private var savedBrightnessMode: Int = -1

    /**
     * 通过 settings put system screen_brightness 控制亮度（最终 fallback）
     *
     * 部分厂商系统会覆盖此值，效果不保证。
     */
    private fun setDisplayPowerModeViaSettingsBrightness(turnOff: Boolean): Boolean {
        return try {
            if (turnOff) {
                // 保存当前亮度和模式
                val brResult = execShell("settings get system screen_brightness")
                savedBrightness = brResult.result.trim().toIntOrNull() ?: 128
                savedBrightnessMode = execShell("settings get system screen_brightness_mode")
                    .result.trim().toIntOrNull() ?: 1

                // 关闭自动亮度，亮度设为 0
                execShell("settings put system screen_brightness_mode 0")
                val ok = execShell("settings put system screen_brightness 0").ok
                Log.d(TAG, "screen off via brightness 0, ok=$ok, saved brightness=$savedBrightness mode=$savedBrightnessMode")
                ok
            } else {
                // 恢复亮度和模式
                val brightness = if (savedBrightness >= 0) savedBrightness else 128
                val mode = if (savedBrightnessMode >= 0) savedBrightnessMode else 1
                execShell("settings put system screen_brightness $brightness")
                val ok = execShell("settings put system screen_brightness_mode $mode").ok
                Log.d(TAG, "screen on via brightness restore=$brightness mode=$mode, ok=$ok")
                ok
            }
        } catch (e: Throwable) {
            Log.d(TAG, "setDisplayPowerMode via brightness failed", e)
            false
        }
    }

    private data class ShellResult(val result: String, val error: String, val code: Int) {
        val ok get() = code == 0
    }

    private fun execShell(command: String, timeoutMs: Long = 5000): ShellResult {
        return try {
            val process = Runtime.getRuntime().exec("sh")
            DataOutputStream(process.outputStream).use { outputStream ->
                outputStream.writeBytes(command)
                outputStream.writeBytes("\n")
                outputStream.writeBytes("exit\n")
                outputStream.flush()
            }
            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ShellResult("", "timeout", -1)
            }
            val result = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val code = process.exitValue()
            if (!error.isBlank() || code != 0) {
                Log.d(TAG, "execShell failed: command=$command, code=$code, error=$error")
            }
            ShellResult(result, error, code)
        } catch (e: Throwable) {
            Log.d(TAG, "execShell exception: command=$command", e)
            ShellResult("", e.message ?: "exception", -1)
        }
    }

    /**
     * 设置充电时保持唤醒
     *
     * @param enable true 开启，false 关闭
     * @return 是否成功
     */
    fun setStayAwake(enable: Boolean): Boolean {
        return try {
            val value = if (enable) "7" else "0" // AC + USB + Wireless
            val ok = execShell("settings put global stay_on_while_plugged_in $value").ok
            Log.d(TAG, "setStayAwake enable=$enable, ok=$ok")
            ok
        } catch (e: Throwable) {
            Log.d(TAG, "setStayAwake failed", e)
            false
        }
    }

    private fun getDisplayTokens(): List<IBinder> {
        // 优先尝试 DisplayControl（Android 14+）
        if (useDisplayControl) {
            val tokens = getDisplayTokensViaDisplayControl()
            if (tokens.isNotEmpty()) return tokens
        }
        // 回退到旧 API（Android < 14）
        return getLegacyDisplayToken()?.let { listOf(it) } ?: emptyList()
    }

    private fun getLegacyDisplayToken(): IBinder? {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val method = getBuiltInDisplayMethod ?: surfaceControlClass
                    .getMethod("getBuiltInDisplay", Int::class.java)
                    .also { getBuiltInDisplayMethod = it }
                method.invoke(null, 0) as? IBinder
            } else {
                val method = getInternalDisplayTokenMethod ?: surfaceControlClass
                    .getMethod("getInternalDisplayToken")
                    .also { getInternalDisplayTokenMethod = it }
                method.invoke(null) as? IBinder
            }
        } catch (e: Throwable) {
            Log.d(TAG, "getLegacyDisplayToken failed", e)
            null
        }
    }

    private fun getDisplayTokensViaDisplayControl(): List<IBinder> {
        val result = mutableListOf<IBinder>()
        val cls = displayControlClass ?: return result
        try {
            val ids = getPhysicalDisplayIdsViaDisplayControl(cls) ?: return result
            for (id in ids) {
                getPhysicalDisplayTokenViaDisplayControl(cls, id)?.let { result.add(it) }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "getDisplayTokensViaDisplayControl failed", e)
        }
        return result
    }

    private fun getPhysicalDisplayIdsViaDisplayControl(cls: Class<*>): LongArray? {
        return try {
            val method = displayControlGetPhysicalDisplayIdsMethod ?: cls
                .getMethod("getPhysicalDisplayIds")
                .also { displayControlGetPhysicalDisplayIdsMethod = it }
            method.invoke(null) as? LongArray
        } catch (e: Throwable) {
            Log.d(TAG, "getPhysicalDisplayIdsViaDisplayControl failed", e)
            null
        }
    }

    private fun getPhysicalDisplayTokenViaDisplayControl(cls: Class<*>, id: Long): IBinder? {
        return try {
            val method = displayControlGetPhysicalDisplayTokenMethod ?: cls
                .getMethod("getPhysicalDisplayToken", Long::class.java)
                .also { displayControlGetPhysicalDisplayTokenMethod = it }
            method.invoke(null, id) as? IBinder
        } catch (e: Throwable) {
            Log.d(TAG, "getPhysicalDisplayTokenViaDisplayControl failed", e)
            null
        }
    }

    private fun invokeSetDisplayPowerMode(token: IBinder, mode: Int): Boolean {
        return try {
            val method = setDisplayPowerModeMethod ?: surfaceControlClass
                .getMethod("setDisplayPowerMode", IBinder::class.java, Int::class.java)
                .also { setDisplayPowerModeMethod = it }
            method.invoke(null, token, mode) as? Boolean ?: true
        } catch (e: Throwable) {
            Log.d(TAG, "invokeSetDisplayPowerMode failed", e)
            false
        }
    }

    private fun loadDisplayControlClass() {
        try {
            val classLoaderFactoryClass = Class.forName("com.android.internal.os.ClassLoaderFactory")
            val createClassLoaderMethod = classLoaderFactoryClass.getDeclaredMethod(
                "createClassLoader",
                String::class.java,
                String::class.java,
                String::class.java,
                ClassLoader::class.java,
                Int::class.java,
                Boolean::class.java,
                String::class.java,
            )
            // 使用 SYSTEMSERVERCLASSPATH（与 scrcpy 一致），包含 services.jar 及其他系统 jar
            val systemServerClasspath = System.getenv("SYSTEMSERVERCLASSPATH")
                ?: "/system/framework/services.jar"
            val classLoader = createClassLoaderMethod.invoke(
                null,
                systemServerClasspath,
                null,
                null,
                ClassLoader.getSystemClassLoader(),
                0,
                true,
                null,
            ) as ClassLoader
            val cls = classLoader.loadClass("com.android.server.display.DisplayControl")
            // 加载 android_servers 库，使 native 方法可用
            try {
                val loadMethod = Runtime::class.java.getDeclaredMethod(
                    "loadLibrary0",
                    Class::class.java,
                    String::class.java,
                )
                loadMethod.isAccessible = true
                loadMethod.invoke(Runtime.getRuntime(), cls, "android_servers")
            } catch (e: Throwable) {
                Log.d(TAG, "loadLibrary0 android_servers failed (non-fatal)", e)
            }
            displayControlClass = cls
            Log.d(TAG, "DisplayControl class loaded successfully")
        } catch (e: Throwable) {
            Log.d(TAG, "loadDisplayControlClass failed (non-fatal)", e)
        }
    }
}
