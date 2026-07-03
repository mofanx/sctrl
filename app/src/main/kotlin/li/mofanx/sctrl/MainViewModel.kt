package li.mofanx.sctrl

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.mofanx.sctrl.a11y.useA11yServiceEnabledFlow
import li.mofanx.sctrl.a11y.useEnabledA11yServicesFlow
import li.mofanx.sctrl.data.CrashData
import li.mofanx.sctrl.permission.AuthReason
import li.mofanx.sctrl.permission.shizukuGrantedState
import li.mofanx.sctrl.shizuku.shizukuContextFlow
import li.mofanx.sctrl.shizuku.updateBinderMutex
import li.mofanx.sctrl.store.storeFlow
import li.mofanx.sctrl.ui.WebViewRoute
import li.mofanx.sctrl.ui.component.AlertDialogOptions
import li.mofanx.sctrl.ui.home.BottomNavItem
import li.mofanx.sctrl.ui.home.HomeRoute
import li.mofanx.sctrl.ui.share.BaseViewModel
import li.mofanx.sctrl.util.DefaultSimpleLifeImpl
import li.mofanx.sctrl.util.LogUtils
import li.mofanx.sctrl.util.OnSimpleLife
import li.mofanx.sctrl.util.ThrottleTimer
import li.mofanx.sctrl.util.crashFolder
import li.mofanx.sctrl.util.crashTempFolder
import li.mofanx.sctrl.util.json
import li.mofanx.sctrl.util.launchTry
import li.mofanx.sctrl.util.openUri
import li.mofanx.sctrl.util.runMainPost
import li.mofanx.sctrl.util.stopCoroutine
import li.mofanx.sctrl.util.toast
import li.songe.loc.Loc
import rikka.shizuku.Shizuku
import java.nio.file.Files
import kotlin.time.Duration.Companion.days

class MainViewModel : BaseViewModel(), OnSimpleLife by DefaultSimpleLifeImpl() {
    companion object {
        private var _instance: MainViewModel? = null
        val instance get() = _instance!!
        private var tempTermsAccepted = false
    }

    init {
        LogUtils.d("MainViewModel:init")
        _instance = this
        addCloseable {
            LogUtils.d("MainViewModel:close")
            if (_instance == this) {
                _instance = null
            }
        }
    }

    override val scope get() = viewModelScope

    val backStack: NavBackStack<NavKey> = NavBackStack(HomeRoute)
    val topRoute get() = backStack.last()

    private val backThrottleTimer = ThrottleTimer()

    fun popPage(@Loc loc: String = "") = runMainPost {
        if (backThrottleTimer.expired() && backStack.size > 1) {
            val old = backStack.last()
            backStack.removeAt(backStack.lastIndex)
            LogUtils.d("popPage", "$old -> ${backStack.last()}", loc = loc)
        }
    }

    fun navigatePage(
        navKey: NavKey,
        replaced: Boolean = false,
        @Loc loc: String = "",
    ) = runMainPost {
        if (navKey != backStack.last()) {
            val old = backStack.last()
            if (replaced) {
                backStack[backStack.lastIndex] = navKey
            } else {
                backStack.add(navKey)
            }
            LogUtils.d("navigatePage", "$old -> ${backStack.last()}", loc = loc)
        }
    }

    fun navigateWebPage(url: String) = navigatePage(WebViewRoute(url))

    val dialogFlow = MutableStateFlow<AlertDialogOptions?>(null)
    val authReasonFlow = MutableStateFlow<AuthReason?>(null)

    val shizukuErrorFlow = MutableStateFlow<Throwable?>(null)

    val textFlow = MutableStateFlow<String?>(null)
    fun openUrl(url: String) {
        if (URLUtil.isNetworkUrl(url)) {
            textFlow.value = url
        } else {
            openUri(url)
        }
    }

    fun switchEnableShizuku(value: Boolean) {
        if (updateBinderMutex.mutex.isLocked) {
            toast("正在连接中，请稍后")
            return
        }
        storeFlow.update { s -> s.copy(enableShizuku = value) }
    }

    fun requestShizuku() {
        if (shizukuContextFlow.value.ok) return
        if (updateBinderMutex.mutex.isLocked) {
            toast("正在连接中，请稍后")
            return
        }
        try {
            Shizuku.requestPermission(Activity.RESULT_OK)
        } catch (e: Throwable) {
            shizukuErrorFlow.value = e
        }
    }

    suspend fun guardShizukuContext() {
        if (shizukuContextFlow.value.ok) return
        if (!storeFlow.value.enableShizuku) {
            storeFlow.update { it.copy(enableShizuku = true) }
        }
        if (!shizukuGrantedState.updateAndGet()) {
            requestShizuku()
            stopCoroutine()
        }
        if (shizukuContextFlow.value.ok) return
        stopCoroutine()
    }

    private val a11yServicesFlow = useEnabledA11yServicesFlow()
    val a11yServiceEnabledFlow = useA11yServiceEnabledFlow(a11yServicesFlow)

    val tabFlow = MutableStateFlow(BottomNavItem.Home.key)
    val resetPageScrollEvent = MutableSharedFlow<BottomNavItem>()
    private var lastClickTabTime = 0L
    fun handleClickTab(navItem: BottomNavItem) {
        val t = System.currentTimeMillis()
        if (navItem.key == tabFlow.value && t - lastClickTabTime < 500) {
            viewModelScope.launch { resetPageScrollEvent.emit(navItem) }
        }
        tabFlow.value = navItem.key
        lastClickTabTime = t
    }

    val termsAcceptedFlow = MutableStateFlow(tempTermsAccepted)

    var tempCrashDataList = emptyList<CrashData>()

    init {
        viewModelScope.launchTry(Dispatchers.IO) {
            val list = (crashTempFolder.listFiles() ?: emptyArray()).mapNotNull {
                try {
                    json.decodeFromString<CrashData>(it.readText())
                } catch (e: Exception) {
                    LogUtils.d("解析崩溃日志失败: ${it.name}", e)
                    null
                }
            }.sortedBy { -it.mtime }
            crashTempFolder.deleteRecursively()
            val t = System.currentTimeMillis()
            crashFolder.listFiles()?.filter {
                val name = it.name
                !list.any { f -> name == f.filename }
            }?.forEach {
                val mtime = Files.getLastModifiedTime(it.toPath()).toMillis()
                if (t - mtime > 30.days.inWholeMilliseconds) {
                    it.delete()
                }
            }
            tempCrashDataList = list
        }

        // for OnSimpleLife
        onCreated()
        addCloseable { onDestroyed() }
    }
}
