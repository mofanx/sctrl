package li.mofanx.sctrl.ui

import li.mofanx.sctrl.MainViewModel
import li.mofanx.sctrl.ui.share.BaseViewModel

class CrashReportVm : BaseViewModel() {
    val crashDataList = MainViewModel.instance.run {
        val v = tempCrashDataList
        tempCrashDataList = emptyList()
        v
    }
}