package li.mofanx.sctrl.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import li.mofanx.sctrl.MainActivity

import li.mofanx.sctrl.permission.shizukuGrantedState
import li.mofanx.sctrl.service.HttpService
import li.mofanx.sctrl.service.StatusService
import li.mofanx.sctrl.shizuku.shizukuContextFlow
import li.mofanx.sctrl.shizuku.updateBinderMutex
import li.mofanx.sctrl.store.storeFlow
import li.mofanx.sctrl.ui.AboutRoute
import li.mofanx.sctrl.ui.component.AuthCard
import li.mofanx.sctrl.ui.component.PageSwitchItemCard
import li.mofanx.sctrl.ui.component.PerfIcon
import li.mofanx.sctrl.ui.component.PerfTopAppBar
import li.mofanx.sctrl.ui.component.SettingItem
import li.mofanx.sctrl.ui.component.autoFocus
import li.mofanx.sctrl.ui.component.useScrollBehaviorState
import li.mofanx.sctrl.ui.share.LocalMainViewModel
import li.mofanx.sctrl.ui.share.asMutableState
import li.mofanx.sctrl.ui.style.EmptyHeight
import li.mofanx.sctrl.ui.style.itemHorizontalPadding
import li.mofanx.sctrl.ui.style.itemPadding
import li.mofanx.sctrl.ui.style.titleItemPadding
import li.mofanx.sctrl.util.launchAsFn
import li.mofanx.sctrl.util.throttle
import li.mofanx.sctrl.util.toast

@Composable
fun useSettingsPage(): ScaffoldExt {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, scrollState) = useScrollBehaviorState(scrollKey)
    LaunchedEffect(null) {
        mainVm.resetPageScrollEvent.collect {
            if (it == BottomNavItem.Settings) {
                scrollKey.intValue++
            }
        }
    }

    // 端口编辑弹窗
    var showEditPortDlg by vm.showEditPortDlgFlow.asMutableState()
    if (showEditPortDlg) {
        val store by storeFlow.collectAsState()
        val portRange = remember { 1000 to 65535 }
        val placeholderText = remember { "请输入 ${portRange.first}-${portRange.second} 的整数" }
        var value by remember { mutableStateOf(store.httpServerPort.toString()) }
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(text = "服务端口") },
            text = {
                OutlinedTextField(
                    value = value,
                    placeholder = { Text(text = placeholderText) },
                    onValueChange = { value = it.filter { c -> c.isDigit() }.take(5) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().autoFocus(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            text = "${value.length} / 5",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                )
            },
            onDismissRequest = { showEditPortDlg = false },
            confirmButton = {
                TextButton(
                    enabled = value.isNotEmpty(),
                    onClick = {
                        val newPort = value.toIntOrNull()
                        if (newPort == null || !(portRange.first <= newPort && newPort <= portRange.second)) {
                            toast(placeholderText)
                            return@TextButton
                        }
                        showEditPortDlg = false
                        if (newPort != store.httpServerPort) {
                            storeFlow.value = store.copy(httpServerPort = newPort)
                            toast("更新成功")
                        }
                    }
                ) { Text(text = "确认") }
            },
            dismissButton = {
                TextButton(onClick = { showEditPortDlg = false }) { Text(text = "取消") }
            }
        )
    }

    // Shizuku 状态弹窗
    var showShizukuState by vm.showShizukuStateFlow.asMutableState()
    if (showShizukuState) {
        val onDismiss = { showShizukuState = false }
        AlertDialog(
            title = { Text(text = "授权状态") },
            text = {
                val states = shizukuContextFlow.collectAsState().value.states
                Column {
                    states.forEach { (name, value) ->
                        Text(
                            text = name,
                            textDecoration = if (value != null) null else TextDecoration.LineThrough,
                        )
                    }
                }
            },
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(text = "我知道了") }
            },
        )
    }

    return ScaffoldExt(
        navItem = BottomNavItem.Settings,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(text = "设置")
            })
        }) { contentPadding ->
        val store by storeFlow.collectAsState()
        val manageRunning by StatusService.isRunning.collectAsState()
        val shizukuGranted by shizukuGrantedState.stateFlow.collectAsState()
        val server by HttpService.httpServerFlow.collectAsState()
        val httpServerRunning = server != null
        val localNetworkIps by HttpService.localNetworkIpsFlow.collectAsState()

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = itemHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(itemHorizontalPadding / 2)
        ) {
            // ── 外观 ──────────────────────────────────────
            Text(
                text = "外观",
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            PageSwitchItemCard(
                imageVector = PerfIcon.AutoMode,
                title = "动态配色",
                subtitle = "使用系统配色方案",
                checked = store.enableDynamicColor,
                onCheckedChange = { storeFlow.value = store.copy(enableDynamicColor = it) }
            )

            // ── 服务 ──────────────────────────────────────
            Text(
                text = "服务",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            PageSwitchItemCard(
                imageVector = PerfIcon.Notifications,
                title = "常驻通知",
                subtitle = "显示运行状态",
                checked = manageRunning && store.enableStatusService,
                onCheckedChange = {
                    if (it) {
                        context.lifecycleScope.launch { StatusService.requestStart(context) }
                    } else {
                        StatusService.stop()
                        storeFlow.value = store.copy(enableStatusService = false)
                    }
                },
            )

            // ── Shizuku ───────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().titleItemPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Shizuku",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                PerfIcon(
                    modifier = Modifier.clickable(
                        onClickLabel = "打开 Shizuku 状态弹窗",
                        onClick = throttle { showShizukuState = true }
                    ),
                    imageVector = PerfIcon.Api,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = "Shizuku 状态",
                )
            }
            AnimatedVisibility(store.enableShizuku && !shizukuGranted) {
                AuthCard(
                    title = "未授权",
                    subtitle = "点击授权以优化体验",
                    onAuthClick = { mainVm.requestShizuku() }
                )
            }
            PageSwitchItemCard(
                imageVector = PerfIcon.VerifiedUser,
                title = "启用优化",
                subtitle = "提升权限优化体验",
                checked = store.enableShizuku,
                onCheckedChange = { mainVm.switchEnableShizuku(it) },
            )

            // ── HTTP ──────────────────────────────────────
            Text(
                text = "HTTP",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            PageSwitchItemCard(
                imageVector = PerfIcon.Api,
                title = "HTTP 服务",
                subtitle = "在浏览器下连接调试",
                checked = httpServerRunning,
                onCheckedChange = throttle(fn = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) HttpService.start() else HttpService.stop()
                })
            )
            AnimatedVisibility(visible = httpServerRunning) {
                Column(
                    modifier = Modifier.itemPadding(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 连接地址
                    Text(
                        text = "连接地址",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val localUrl = "http://127.0.0.1:${store.httpServerPort}"
                    Text(
                        text = localUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = throttle { mainVm.openUrl(localUrl) }),
                    )
                    localNetworkIps.forEach { host ->
                        val lanUrl = "http://${host}:${store.httpServerPort}"
                        Text(
                            text = lanUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = throttle { mainVm.openUrl(lanUrl) })
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // API 接口
                    Text(
                        text = "屏幕控制 API",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val apiBase = if (localNetworkIps.isNotEmpty())
                        "http://${localNetworkIps.first()}:${store.httpServerPort}"
                    else
                        "http://127.0.0.1:${store.httpServerPort}"
                    listOf(
                        "GET  /api/screen/state" to "查询屏幕状态",
                        "POST /api/screen/off"   to "关闭屏幕（熄屏）",
                        "POST /api/screen/on"    to "开启屏幕（亮屏）",
                    ).forEach { (method, desc) ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = method,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = "示例：curl -X POST $apiBase/api/screen/off",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingItem(
                title = "服务端口",
                subtitle = store.httpServerPort.toString(),
                imageVector = PerfIcon.Edit,
                onClickLabel = "编辑服务端口",
                onClick = { showEditPortDlg = true }
            )

            // ── 其他 ──────────────────────────────────────
            Text(
                text = "其他",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            SettingItem(
                title = "关于",
                imageVector = PerfIcon.Info,
                onClick = throttle { mainVm.navigatePage(AboutRoute) }
            )

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
