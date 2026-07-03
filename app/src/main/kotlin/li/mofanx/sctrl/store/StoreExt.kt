package li.mofanx.sctrl.store

import kotlinx.coroutines.Dispatchers
import li.mofanx.sctrl.appScope
import li.mofanx.sctrl.util.launchTry

val storeFlow by lazy {
    createAnyFlow(
        key = "store",
        default = { SettingsStore() }
    )
}

fun initStore() = appScope.launchTry(Dispatchers.IO) {
    // preload
    storeFlow.value
}
