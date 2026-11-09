package app.lightlink.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** تبدیل ارقام لاتین به فارسی برای نمایش. */
fun String.toPersianDigits(): String =
    map { c -> if (c in '0'..'9') '۰' + (c - '0') else c }.joinToString("")

fun Int.toFa(): String = toString().toPersianDigits()

/** مدت بر حسب ثانیه به‌صورت «دقیقه:ثانیه» فارسی. */
fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toFa()}:${seconds.toString().padStart(2, '0').toPersianDigits()}"
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** ردیفی از دکمه‌های انتخابی؛ گزینه‌ی انتخاب‌شده توپر نمایش داده می‌شود. */
@Composable
fun ChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            if (index == selectedIndex) {
                Button(onClick = { onSelect(index) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = { onSelect(index) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(label)
                }
            }
        }
    }
}
