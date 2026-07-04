package li.mofanx.sctrl.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import li.mofanx.sctrl.MainActivity
import li.mofanx.sctrl.store.storeFlow
import li.mofanx.sctrl.ui.share.LocalMainViewModel
import li.mofanx.sctrl.util.throttle


@Composable
fun TermsAcceptDialog() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val modifier = Modifier.fillMaxWidth()
    val scope = rememberCoroutineScope()
    val stepDataList = remember {
        arrayOf(
            "使用声明" to @Composable {
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
                Text(
                    modifier = modifier,
                    text = buildAnnotatedString {
                        append("感谢使用 SCTRL！您需要阅读并同意「")
                        withLink(
                            LinkAnnotation.Url(
                                "https://github.com/mofanx/sctrl/blob/main/LICENSE",
                                linkStyles
                            )
                        ) {
                            append("用户协议")
                        }
                        append("」和「")
                        withLink(
                            LinkAnnotation.Url(
                                "https://github.com/mofanx/sctrl/blob/main/LICENSE",
                                linkStyles
                            )
                        ) {
                            append("隐私政策")
                        }
                        append("」才能继续使用。本应用通过 Shizuku 提供屏幕控制等系统能力，请确保已安装并授权 Shizuku。")
                    },
                )
            }
        )
    }
    val step = 0 // 只有一个步骤，固定为 0
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = stepDataList[step].first)
        },
        text = stepDataList[step].second,
        confirmButton = {
            TextButton(onClick = throttle {
                mainVm.termsAcceptedFlow.value = true
                scope.launch {
                    val current = storeFlow.value
                    storeFlow.value = current.copy(termsAccepted = true)
                }
            }) {
                Text(text = "同意")
            }
        },
        dismissButton = {
            TextButton(onClick = throttle {
                context.finish()
            }) {
                Text(text = "不同意")
            }
        }
    )
}