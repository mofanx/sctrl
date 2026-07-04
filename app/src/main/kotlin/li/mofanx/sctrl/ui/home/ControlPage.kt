package li.mofanx.sctrl.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.update
import li.mofanx.sctrl.MainActivity
import li.mofanx.sctrl.R
import li.mofanx.sctrl.shizuku.shizukuContextFlow
import li.mofanx.sctrl.ui.component.PageSwitchItemCard
import li.mofanx.sctrl.ui.component.PerfIcon
import li.mofanx.sctrl.ui.component.PerfIconButton
import li.mofanx.sctrl.ui.component.PerfTopAppBar
import li.mofanx.sctrl.ui.component.useScrollBehaviorState
import li.mofanx.sctrl.ui.share.LocalMainViewModel
import li.mofanx.sctrl.ui.style.EmptyHeight
import li.mofanx.sctrl.ui.style.itemHorizontalPadding
import li.mofanx.sctrl.util.launchTry
import li.mofanx.sctrl.util.throttle
import li.mofanx.sctrl.util.toast

@Composable
fun useControlPage(): ScaffoldExt {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, scrollState) = useScrollBehaviorState(scrollKey)
    LaunchedEffect(null) {
        mainVm.resetPageScrollEvent.collect {
            if (it == BottomNavItem.Home) {
                scrollKey.intValue++
            }
        }
    }
    // 首页 resume 时同步屏幕状态
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
    return ScaffoldExt(
        navItem = BottomNavItem.Home,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(text = stringResource(R.string.app_name))
            }, actions = {
                PerfIconButton(
                    imageVector = PerfIcon.RocketLaunch,
                    onClickLabel = "Shizuku 授权",
                    contentDescription = "Shizuku 授权",
                    onClick = throttle { mainVm.requestShizuku() },
                )
            })
        }) { contentPadding ->
        val screenOff by vm.screenOffFlow.collectAsState()
        val stayAwake by vm.stayAwakeFlow.collectAsState()
        val shizukuContext by shizukuContextFlow.collectAsState()
        val shizukuOk = shizukuContext.serviceWrapper != null

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = itemHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Shizuku 未连接提示
            if (!shizukuOk) {
                Spacer(modifier = Modifier.height(itemHorizontalPadding / 2))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 自动开启启用优化开关并请求授权
                            mainVm.switchEnableShizuku(true)
                            mainVm.requestShizuku()
                        },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = "Shizuku 未连接，屏幕控制需要 Shizuku 授权（点击自动授权）",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 大圆形熄屏按钮
            Spacer(modifier = Modifier.height(48.dp))
            Box(contentAlignment = Alignment.Center) {
                FilledTonalIconButton(
                    onClick = throttle {
                        if (!shizukuOk) {
                            toast("Shizuku 未连接")
                            return@throttle
                        }
                        vm.viewModelScope.launchTry {
                            val turnOff = !screenOff
                            val ok = shizukuContext.setDisplayPowerMode(turnOff)
                            if (ok) {
                                vm.screenOffFlow.update { turnOff }
                                toast(if (turnOff) "屏幕已关闭" else "屏幕已开启")
                            } else {
                                toast("操作失败，请检查 Shizuku 授权")
                            }
                        }
                    },
                    modifier = Modifier.size(140.dp),
                    colors = if (screenOff) {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    } else {
                        IconButtonDefaults.filledTonalIconButtonColors()
                    },
                ) {
                    PerfIcon(
                        imageVector = if (screenOff) PerfIcon.DarkMode else PerfIcon.LightMode,
                        contentDescription = if (screenOff) "屏幕已关闭，点击开启" else "屏幕正在显示，点击关闭",
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (screenOff) "屏幕已关闭" else "屏幕正常显示",
                style = MaterialTheme.typography.titleMedium,
                color = if (screenOff) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "点击切换熄屏 / 亮屏",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(48.dp))

            // 保持唤醒开关
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
