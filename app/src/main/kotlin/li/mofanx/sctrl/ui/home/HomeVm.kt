package li.mofanx.sctrl.ui.home

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import li.mofanx.sctrl.shizuku.shizukuContextFlow
import li.mofanx.sctrl.ui.share.BaseViewModel

class HomeVm : BaseViewModel() {
    val screenOffFlow = MutableStateFlow(false)
    val stayAwakeFlow = MutableStateFlow(false)
    val showEditPortDlgFlow = MutableStateFlow(false)
    val showShizukuStateFlow = MutableStateFlow(false)

    /**
     * 首页 resume 时调用：如果远端仍在守护但屏幕实际已开启（用户解锁回来了），
     * 主动停止息屏并同步 UI。
     */
    fun onPageResume() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = shizukuContextFlow.value
            if (ctx.serviceWrapper == null) return@launch
            val remoteScreenOff = ctx.isKeepingScreenOff()
            if (remoteScreenOff) {
                // 守护线程还在，但用户已经解锁回到 App 了，说明用户想亮屏
                // 主动停止息屏模式
                ctx.setDisplayPowerMode(false)
                screenOffFlow.value = false
            } else {
                screenOffFlow.value = false
            }
        }
    }
}
