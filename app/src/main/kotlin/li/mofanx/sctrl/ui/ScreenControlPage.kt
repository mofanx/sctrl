package li.mofanx.sctrl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import li.mofanx.sctrl.shizuku.shizukuContextFlow
import li.mofanx.sctrl.ui.component.PageSwitchItemCard
import li.mofanx.sctrl.ui.component.PerfIcon
import li.mofanx.sctrl.ui.component.PerfIconButton
import li.mofanx.sctrl.ui.component.PerfTopAppBar
import li.mofanx.sctrl.ui.component.SettingItem
import li.mofanx.sctrl.ui.component.useScrollBehaviorState
import li.mofanx.sctrl.ui.share.LocalMainViewModel
import li.mofanx.sctrl.ui.style.EmptyHeight
import li.mofanx.sctrl.ui.style.itemHorizontalPadding
import li.mofanx.sctrl.util.launchTry
import li.mofanx.sctrl.util.throttle
import li.mofanx.sctrl.util.toast

@Serializable
data object ScreenControlRoute : NavKey

@Composable
fun ScreenControlPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<ScreenControlVm>()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val shizukuContext by shizukuContextFlow.collectAsState()
    val screenOff by vm.screenOffFlow.collectAsState()
    val stayAwake by vm.stayAwakeFlow.collectAsState()

    // Activity resume 时：如果用户已解锁回来，自动停止息屏并同步 UI
    val context = LocalContext.current
    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onPageResume()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popPage() }
                    )
                },
                title = { Text(text = "屏幕控制") }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = itemHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(itemHorizontalPadding / 2)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            val shizukuOk = shizukuContext.serviceWrapper != null
            if (!shizukuOk) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = "Shizuku 未连接，屏幕控制需要 Shizuku 授权",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "说明：熄屏运行只关闭屏幕显示，不锁屏，后台应用继续运行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            PageSwitchItemCard(
                imageVector = PerfIcon.DarkMode,
                title = "熄屏运行",
                subtitle = if (screenOff) "屏幕已关闭" else "屏幕正常显示",
                checked = screenOff,
                enabled = shizukuOk,
                onCheckedChange = { turnOff ->
                    vm.viewModelScope.launchTry {
                        val ok = shizukuContext.setDisplayPowerMode(turnOff)
                        if (ok) {
                            vm.screenOffFlow.update { turnOff }
                            toast(if (turnOff) "屏幕已关闭" else "屏幕已开启")
                        } else {
                            toast("操作失败，请检查 Shizuku 授权")
                        }
                    }
                }
            )

            SettingItem(
                title = "点亮屏幕",
                subtitle = "强制恢复屏幕显示",
                imageVector = PerfIcon.WbSunny,
                enabled = shizukuOk,
                onClick = throttle {
                    vm.viewModelScope.launchTry {
                        val ok = shizukuContext.setDisplayPowerMode(false)
                        if (ok) {
                            vm.screenOffFlow.update { false }
                            toast("屏幕已开启")
                        } else {
                            toast("操作失败")
                        }
                    }
                }
            )

            PageSwitchItemCard(
                imageVector = PerfIcon.LightMode,
                title = "保持唤醒",
                subtitle = "充电时屏幕保持常亮",
                checked = stayAwake,
                enabled = shizukuOk,
                onCheckedChange = { enable ->
                    vm.viewModelScope.launchTry {
                        val ok = shizukuContext.setStayAwake(enable)
                        if (ok) {
                            vm.stayAwakeFlow.update { enable }
                            toast(if (enable) "保持唤醒已开启" else "保持唤醒已关闭")
                        } else {
                            toast("操作失败")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
