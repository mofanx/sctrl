package li.mofanx.ank.ui.share

import androidx.compose.runtime.staticCompositionLocalOf
import li.mofanx.ank.MainViewModel

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("not found MainViewModel")
}

val LocalDarkTheme = staticCompositionLocalOf { false }

val LocalIsTalkbackEnabled = staticCompositionLocalOf {
    false
}
