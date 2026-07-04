package li.mofanx.sctrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import li.mofanx.sctrl.ui.component.PerfIcon
import li.mofanx.sctrl.ui.component.PerfIconButton
import li.mofanx.sctrl.ui.component.PerfTopAppBar
import li.mofanx.sctrl.ui.share.LocalMainViewModel
import li.mofanx.sctrl.ui.style.EmptyHeight
import li.mofanx.sctrl.ui.style.cardHorizontalPadding
import li.mofanx.sctrl.ui.style.itemHorizontalPadding
import li.mofanx.sctrl.ui.style.surfaceCardColors
import li.mofanx.sctrl.shizuku.shizukuContextFlow
import li.mofanx.sctrl.util.launchAsFn
import li.mofanx.sctrl.util.throttle
import li.mofanx.sctrl.util.toast

@Serializable
data object AuthA11yRoute : NavKey

@Composable
fun AuthA11yPage() {
    val mainVm = LocalMainViewModel.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = { mainVm.popPage() }
            )
        }, title = { Text(text = "Shizuku 授权") })
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = itemHorizontalPadding)
                    .fillMaxWidth(),
                colors = surfaceCardColors,
            ) {
                Text(
                    modifier = Modifier
                        .padding(horizontal = cardHorizontalPadding)
                        .padding(start = 4.dp, top = 12.dp),
                    text = "授权",
                    style = MaterialTheme.typography.titleSmall
                )
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = cardHorizontalPadding)
                        .padding(start = 8.dp, top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        "授予 Shizuku 权限",
                        "屏幕控制等功能需要 Shizuku 提供系统 API"
                    ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = cardHorizontalPadding),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ShizukuAuthButton()
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun ShizukuAuthButton(
    modifier: Modifier = Modifier,
) {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AuthA11yVm>()
    TextButton(
        modifier = modifier,
        onClick = throttle(vm.viewModelScope.launchAsFn(Dispatchers.IO) {
            mainVm.guardShizukuContext()
            if (shizukuContextFlow.value.ok) {
                toast("授权成功")
            }
        })
    ) {
        Text(
            text = "Shizuku 授权",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun TextListItem(
    list: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val lineHeightDp = LocalDensity.current.run { style.lineHeight.toDp() }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        list.forEach { text ->
            Row {
                Spacer(
                    modifier = Modifier
                        .padding(vertical = (lineHeightDp - 4.dp) / 2)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .size(4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, style = style)
            }
        }
    }
}
