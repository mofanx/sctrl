package li.mofanx.sctrl.service

import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.mofanx.sctrl.accessRestrictedSettingsShowFlow
import li.mofanx.sctrl.app
import li.mofanx.sctrl.appScope
import li.mofanx.sctrl.permission.writeSecureSettingsState
import li.mofanx.sctrl.shizuku.AutomationService
import li.mofanx.sctrl.shizuku.shizukuContextFlow
import li.mofanx.sctrl.shizuku.uiAutomationFlow
import li.mofanx.sctrl.util.launchTry
import li.mofanx.sctrl.util.toast

class SctrlTileService : BaseTileService() {
    override val activeFlow = combine(A11yService.isRunning, uiAutomationFlow) { a11y, automator ->
        a11y || automator != null
    }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        onTileClicked { switchAutomatorService() }
    }
}

private suspend fun switchA11yService() {
    if (A11yService.isRunning.value) {
        A11yService.instance?.disableSelf()
    } else {
        if (!writeSecureSettingsState.updateAndGet()) {
            if (!writeSecureSettingsState.value) {
                toast("请先授予「写入安全设置权限」")
                return
            }
        }
        val names = app.getSecureA11yServices()
        app.putSecureInt(Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        if (names.contains(A11yService.a11yCn)) {
            names.remove(A11yService.a11yCn)
            app.putSecureA11yServices(names)
            delay(1000L)
        }
        names.add(A11yService.a11yCn)
        app.putSecureA11yServices(names)
        delay(2000L)
        if (!A11yService.isRunning.value) {
            toast("开启无障碍失败")
            accessRestrictedSettingsShowFlow.value = true
        }
    }
}

private fun switchAutomationService() {
    val newEnabled = uiAutomationFlow.value == null
    uiAutomationFlow.value?.shutdown()
    if (newEnabled && shizukuContextFlow.value.ok) {
        AutomationService.tryConnect()
    }
}

fun switchAutomatorService() = appScope.launchTry(Dispatchers.IO) {
    switchA11yService()
}
