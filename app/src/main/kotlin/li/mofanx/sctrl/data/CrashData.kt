package li.mofanx.sctrl.data

import kotlinx.serialization.Serializable
import li.mofanx.sctrl.util.crashFolder
import li.mofanx.sctrl.util.crashTempFolder
import li.mofanx.sctrl.util.format
import li.mofanx.sctrl.util.json

@Serializable
data class CrashData(
    val id: Long,
    val mtime: Long,
    val device: String,
    val androidVersionCode: Int,
    val androidVersionName: String,
    val versionCode: Int,
    val versionName: String,
    val name: String,
    val message: String?,
    val thread: String,
    val stackTrace: String,
) {
    val filename get() = "ank_crash-" + mtime.format("yyyyMMdd_HHmmss") + ".json"
    fun save() {
        val text = json.encodeToString(this)
        crashFolder.resolve(filename).writeText(text)
        crashTempFolder.resolve(filename).writeText(text)
    }

}
