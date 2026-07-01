package li.mofanx.ank.store

import kotlinx.coroutines.Dispatchers
import li.mofanx.ank.appScope
import li.mofanx.ank.util.launchTry

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
