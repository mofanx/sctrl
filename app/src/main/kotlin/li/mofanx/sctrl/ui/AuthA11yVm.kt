package li.mofanx.sctrl.ui

import kotlinx.coroutines.flow.MutableStateFlow
import li.mofanx.sctrl.ui.share.BaseViewModel
import li.mofanx.sctrl.ui.share.asMutableState

class AuthA11yVm : BaseViewModel() {
    val showCopyDlgFlow = MutableStateFlow(false)
}
