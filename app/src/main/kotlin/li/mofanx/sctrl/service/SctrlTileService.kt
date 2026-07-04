package li.mofanx.sctrl.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.mofanx.sctrl.app
import li.mofanx.sctrl.appScope
import li.mofanx.sctrl.shizuku.AutomationService
import li.mofanx.sctrl.shizuku.shizukuContextFlow
import li.mofanx.sctrl.shizuku.uiAutomationFlow
import li.mofanx.sctrl.util.launchTry
import li.mofanx.sctrl.util.toast

class SctrlTileService : BaseTileService() {
    override val activeFlow = uiAutomationFlow.map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        onTileClicked { switchAutomationService() }
    }
}

private fun switchAutomationService() {
    val newEnabled = uiAutomationFlow.value == null
    uiAutomationFlow.value?.shutdown()
    if (newEnabled && shizukuContextFlow.value.ok) {
        AutomationService.tryConnect()
    } else if (newEnabled) {
        toast("请先授权 Shizuku")
    }
}

fun switchAutomatorService() = appScope.launchTry(Dispatchers.IO) {
    switchAutomationService()
}
