package li.mofanx.ank.ui

import kotlinx.coroutines.flow.MutableStateFlow
import li.mofanx.ank.ui.share.BaseViewModel
import li.mofanx.ank.ui.share.asMutableState

class AuthA11yVm : BaseViewModel() {
    val showCopyDlgFlow = MutableStateFlow(false)
}
