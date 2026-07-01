package li.mofanx.ank.ui

import li.mofanx.ank.MainViewModel
import li.mofanx.ank.ui.share.BaseViewModel

class CrashReportVm : BaseViewModel() {
    val crashDataList = MainViewModel.instance.run {
        val v = tempCrashDataList
        tempCrashDataList = emptyList()
        v
    }
}