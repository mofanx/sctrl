package li.mofanx.sctrl

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.dylanc.activityresult.launcher.launchForResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.mofanx.sctrl.a11y.topActivityFlow
import li.mofanx.sctrl.a11y.updateSystemDefaultAppId
import li.mofanx.sctrl.a11y.updateTopActivity
import li.mofanx.sctrl.permission.AuthDialog
import li.mofanx.sctrl.permission.updatePermissionState
import li.mofanx.sctrl.service.A11yService
import li.mofanx.sctrl.service.StatusService
import li.mofanx.sctrl.shizuku.automationRegisteredExceptionFlow
import li.mofanx.sctrl.shizuku.shizukuContextFlow
import li.mofanx.sctrl.store.storeFlow
import li.mofanx.sctrl.ui.AboutPage
import li.mofanx.sctrl.ui.AboutRoute
import li.mofanx.sctrl.ui.AdvancedPage
import li.mofanx.sctrl.ui.AdvancedRoute
import li.mofanx.sctrl.ui.AppOpsAllowPage
import li.mofanx.sctrl.ui.AppOpsAllowRoute
import li.mofanx.sctrl.ui.AuthA11yPage
import li.mofanx.sctrl.ui.AuthA11yRoute
import li.mofanx.sctrl.ui.CrashReportPage
import li.mofanx.sctrl.ui.CrashReportRoute
import li.mofanx.sctrl.ui.ImagePreviewPage
import li.mofanx.sctrl.ui.ImagePreviewRoute
import li.mofanx.sctrl.ui.WebViewPage
import li.mofanx.sctrl.ui.WebViewRoute
import li.mofanx.sctrl.ui.component.BuildDialog
import li.mofanx.sctrl.ui.component.PerfIcon
import li.mofanx.sctrl.ui.component.TermsAcceptDialog
import li.mofanx.sctrl.ui.component.TextDialog
import li.mofanx.sctrl.ui.home.HomePage
import li.mofanx.sctrl.ui.home.HomeRoute
import li.mofanx.sctrl.ui.share.FixedWindowInsets
import li.mofanx.sctrl.ui.share.LocalMainViewModel
import li.mofanx.sctrl.ui.style.AppTheme
import li.mofanx.sctrl.util.AndroidTarget
import li.mofanx.sctrl.util.BarUtils
import li.mofanx.sctrl.util.KeyboardUtils
import li.mofanx.sctrl.util.LogUtils
import li.mofanx.sctrl.util.appInfoMapFlow
import li.mofanx.sctrl.util.componentName
import li.mofanx.sctrl.util.copyText
import li.mofanx.sctrl.util.fixSomeProblems
import li.mofanx.sctrl.util.launchTry
import li.mofanx.sctrl.util.mapState
import li.mofanx.sctrl.util.openApp
import li.mofanx.sctrl.util.openUri
import li.mofanx.sctrl.util.shizukuAppId
import li.mofanx.sctrl.util.throttle
import li.mofanx.sctrl.util.toast
import kotlin.concurrent.Volatile
import kotlin.reflect.jvm.jvmName

class MainActivity : ComponentActivity() {
    val startTime = System.currentTimeMillis()
    val mainVm by viewModels<MainViewModel>()
    val launcher by lazy { StartActivityLauncher(this) }
    val pickContentLauncher by lazy { PickContentLauncher(this) }

    val imeFullHiddenFlow = MutableStateFlow(true)
    val imePlayingFlow = MutableStateFlow(false)

    private val imeVisible: Boolean
        get() = ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    var topBarWindowInsets by mutableStateOf(WindowInsets(top = BarUtils.getStatusBarHeight()))

    private fun watchKeyboardVisible() {
        if (AndroidTarget.R) {
            ViewCompat.setWindowInsetsAnimationCallback(
                window.decorView,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onStart(
                        animation: WindowInsetsAnimationCompat,
                        bounds: WindowInsetsAnimationCompat.BoundsCompat
                    ): WindowInsetsAnimationCompat.BoundsCompat {
                        imePlayingFlow.update { imeVisible }
                        return super.onStart(animation, bounds)
                    }

                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: List<WindowInsetsAnimationCompat>
                    ): WindowInsetsCompat = insets

                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        imeFullHiddenFlow.update { !imeVisible }
                        imePlayingFlow.update { false }
                        super.onEnd(animation)
                    }
                })
        } else {
            KeyboardUtils.registerSoftInputChangedListener(window) { height ->
                imeFullHiddenFlow.update { height == 0 }
            }
        }
    }

    suspend fun hideSoftInput(): Boolean {
        if (!imeFullHiddenFlow.updateAndGet { !imeVisible }) {
            KeyboardUtils.hideSoftInput(this@MainActivity)
            imeFullHiddenFlow.drop(1).first()
            return true
        }
        return false
    }

    fun justHideSoftInput(): Boolean {
        if (!imeFullHiddenFlow.updateAndGet { !imeVisible }) {
            KeyboardUtils.hideSoftInput(this@MainActivity)
            return true
        }
        return false
    }

    suspend fun pickFile(contentType: String): Uri? {
        val u = launcher.launchForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = contentType
        }).data?.data
        if (u == null) {
            toast("未选择文件")
        }
        return u
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        fixSomeProblems()
        super.onCreate(savedInstanceState)
        LogUtils.d()
        mainVm
        launcher
        pickContentLauncher
        lifecycleScope.launch {
            storeFlow.mapState(lifecycleScope) { s -> s.excludeFromRecents }.collect {
                app.activityManager.appTasks.forEach { task ->
                    task.setExcludeFromRecents(it)
                }
            }
        }
        watchKeyboardVisible()
        StatusService.autoStart()
        setContent {
            val latestInsets = TopAppBarDefaults.windowInsets
            val density = LocalDensity.current
            if (latestInsets.getTop(density) > topBarWindowInsets.getTop(density)) {
                topBarWindowInsets = FixedWindowInsets(latestInsets)
            }
            CompositionLocalProvider(
                LocalMainViewModel provides mainVm
            ) {
                AppTheme {
                    NavDisplay(
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        backStack = mainVm.backStack,
                        onBack = mainVm::popPage,
                        entryProvider = entryProvider {
                            entry<HomeRoute> { HomePage() }
                            entry<AuthA11yRoute> { AuthA11yPage() }
                            entry<AboutRoute> { AboutPage() }
                            entry<AdvancedRoute> { AdvancedPage() }
                            entry<AppOpsAllowRoute> { AppOpsAllowPage() }
                            entry<WebViewRoute> { WebViewPage(it) }
                            entry<ImagePreviewRoute> { ImagePreviewPage(it) }
                            entry<CrashReportRoute> { CrashReportPage() }
                        },
                        transitionSpec = {
                            slideInHorizontally(initialOffsetX = { it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { -it })
                        },
                        popTransitionSpec = {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                        },
                        predictivePopTransitionSpec = {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                        },
                    )
                    if (!mainVm.termsAcceptedFlow.collectAsState().value) {
                        TermsAcceptDialog()
                    } else {
                        UiAutomationAlreadyRegisteredDlg()
                        AccessRestrictedSettingsDlg()
                        ShizukuErrorDialog(mainVm.shizukuErrorFlow)
                        AuthDialog(mainVm.authReasonFlow)
                        BuildDialog(mainVm.dialogFlow)
                        TextDialog(mainVm.textFlow)
                    }
                }
            }
            LaunchedEffect(null) {
                intent?.let {
                    intent = null
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LogUtils.d()
        activityVisibleState++
        if (topActivityFlow.value.appId != META.appId) {
            synchronized(topActivityFlow) {
                updateTopActivity(
                    META.appId,
                    MainActivity::class.jvmName
                )
            }
        }
    }

    var isFirstResume = true
    override fun onResume() {
        super.onResume()
        LogUtils.d()
        if (isFirstResume && startTime - app.startTime < 2000) {
            isFirstResume = false
        } else {
            syncFixState()
        }
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d()
        activityVisibleState--
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtils.d()
    }
}

@Volatile
private var activityVisibleState = 0
val isActivityVisible get() = activityVisibleState > 0

val activityNavSourceName by lazy { META.appId + ".activity.nav.source" }

fun Activity.navToMainActivity() {
    if (intent != null) {
        val navIntent = Intent(intent)
        navIntent.component = MainActivity::class.componentName
        navIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        navIntent.putExtra(activityNavSourceName, this::class.jvmName)
        startActivity(navIntent)
    }
    finish()
}

private val syncStateMutex = Mutex()
fun syncFixState() {
    appScope.launchTry(Dispatchers.IO) {
        if (syncStateMutex.isLocked) {
            LogUtils.d("syncFixState isLocked")
        }
        syncStateMutex.withLock {
            updateSystemDefaultAppId()
            shizukuContextFlow.value.grantSelf()
            updatePermissionState()
        }
    }
}

@Composable
private fun ShizukuErrorDialog(stateFlow: MutableStateFlow<Throwable?>) {
    val state = stateFlow.collectAsState().value
    if (state != null) {
        val errorText = remember { state.stackTraceToString() }
        val appInfoCache = appInfoMapFlow.collectAsState().value
        val installed = appInfoCache.contains(shizukuAppId)
        AlertDialog(
            onDismissRequest = { stateFlow.value = null },
            title = { Text(text = "授权错误") },
            text = {
                Column {
                    Text(
                        text = if (installed) {
                            "Shizuku 授权失败，请检查是否运行"
                        } else {
                            "Shizuku 授权失败，检测到 Shizuku 未安装，请先下载后安装"
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = errorText,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(8.dp)
                                    .heightIn(max = 400.dp)
                                    .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        PerfIcon(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickable(onClick = throttle {
                                    copyText(errorText)
                                })
                                .padding(4.dp)
                                .size(20.dp),
                            imageVector = PerfIcon.ContentCopy,
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                        )
                    }
                }
            },
            confirmButton = {
                if (installed) {
                    TextButton(onClick = {
                        stateFlow.value = null
                        openApp(shizukuAppId)
                    }) {
                        Text(text = "打开 Shizuku")
                    }
                } else {
                    TextButton(onClick = {
                        stateFlow.value = null
                        openUri("https://shizuku.rikka.app/")
                    }) {
                        Text(text = "去下载")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { stateFlow.value = null }) {
                    Text(text = "我知道了")
                }
            }
        )
    }
}

val accessRestrictedSettingsShowFlow = MutableStateFlow(false)

@Composable
fun AccessRestrictedSettingsDlg() {
    val a11yRunning by A11yService.isRunning.collectAsState()
    LaunchedEffect(a11yRunning) {
        if (a11yRunning) {
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    val accessRestrictedSettingsShow by accessRestrictedSettingsShowFlow.collectAsState()
    val mainVm = LocalMainViewModel.current
    val isA11yPage = mainVm.topRoute is AuthA11yRoute
    LaunchedEffect(isA11yPage, accessRestrictedSettingsShow) {
        if (isA11yPage && accessRestrictedSettingsShow && !a11yRunning) {
            toast("请重新授权以解除限制")
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    if (accessRestrictedSettingsShow && !isA11yPage && !a11yRunning) {
        AlertDialog(
            title = { Text(text = "权限受限") },
            text = { Text(text = "当前操作权限「访问受限设置」已被限制, 请先解除限制") },
            onDismissRequest = { accessRestrictedSettingsShowFlow.value = false },
            confirmButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                    mainVm.navigatePage(AppOpsAllowRoute)
                }) { Text(text = "解除") }
            },
            dismissButton = {
                TextButton({ accessRestrictedSettingsShowFlow.value = false }) {
                    Text(text = "关闭")
                }
            },
        )
    }
}

@Composable
fun UiAutomationAlreadyRegisteredDlg() {
    if (automationRegisteredExceptionFlow.collectAsState().value != null) {
        AlertDialog(
            onDismissRequest = { automationRegisteredExceptionFlow.value = null },
            title = { Text(text = "启动失败") },
            text = {
                Text(text = "自动化服务启动失败，检测到自动化服务已被其他应用占用，请先关闭已有服务后重试")
            },
            confirmButton = {
                TextButton(onClick = { automationRegisteredExceptionFlow.value = null }) {
                    Text(text = "我知道了")
                }
            }
        )
    }
}
