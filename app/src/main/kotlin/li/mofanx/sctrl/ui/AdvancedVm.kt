package li.mofanx.sctrl.ui

import kotlinx.coroutines.flow.MutableStateFlow
import li.mofanx.sctrl.ui.share.BaseViewModel
import li.mofanx.sctrl.ui.share.asMutableState

class AdvancedVm : BaseViewModel() {
    val showEditPortDlgFlow = MutableStateFlow(false)
    val showShizukuStateFlow = MutableStateFlow(false)
}
