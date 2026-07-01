package li.mofanx.ank.ui

import kotlinx.coroutines.flow.MutableStateFlow
import li.mofanx.ank.ui.share.BaseViewModel
import li.mofanx.ank.ui.share.asMutableState

class AdvancedVm : BaseViewModel() {
    val showEditPortDlgFlow = MutableStateFlow(false)
    val showShizukuStateFlow = MutableStateFlow(false)
}
