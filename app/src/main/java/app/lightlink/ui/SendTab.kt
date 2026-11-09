package app.lightlink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lightlink.R
import app.lightlink.light.LightSendViewModel
import app.lightlink.light.SendIssue
import app.lightlink.light.estimateSendSeconds
import app.lightlink.protocol.ProtocolConstants

private val SYMBOL_MS_OPTIONS = listOf(150, 100, 80)
private val REPEAT_OPTIONS = listOf(1, 2, 3)

@Composable
fun SendTab(vm: LightSendViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var speedIndex by rememberSaveable { mutableIntStateOf(1) }
    var repeatIndex by rememberSaveable { mutableIntStateOf(1) }

    val symbolMs = SYMBOL_MS_OPTIONS[speedIndex]
    val repeats = REPEAT_OPTIONS[repeatIndex]
    val byteCount = text.trim().toByteArray(Charsets.UTF_8).size
    val tooLong = byteCount > ProtocolConstants.MAX_MESSAGE_BYTES
    val estimate = remember(text, symbolMs, repeats) { estimateSendSeconds(text, symbolMs, repeats) }

    val view = LocalView.current
    DisposableEffect(state.sending) {
        view.keepScreenOn = state.sending
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) {
        onDispose { vm.cancel() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                if (state.issue != null || state.done) vm.clearStatus()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.sending,
            label = { Text(stringResource(R.string.send_message_label)) },
            minLines = 2,
            maxLines = 5,
            isError = tooLong,
            supportingText = {
                Text(
                    stringResource(
                        R.string.send_bytes_counter,
                        byteCount.toFa(),
                        ProtocolConstants.MAX_MESSAGE_BYTES.toFa(),
                    ),
                )
            },
        )

        Text(
            text = stringResource(R.string.send_speed_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        ChoiceRow(
            options = listOf(
                stringResource(R.string.speed_stable),
                stringResource(R.string.speed_standard),
                stringResource(R.string.speed_fast),
            ),
            selectedIndex = speedIndex,
            enabled = !state.sending,
            onSelect = { speedIndex = it },
        )
        Text(
            text = stringResource(R.string.send_speed_detail, bitsPerSecondLabel(symbolMs)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(R.string.send_repeats_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        ChoiceRow(
            options = REPEAT_OPTIONS.map { it.toFa() },
            selectedIndex = repeatIndex,
            enabled = !state.sending,
            onSelect = { repeatIndex = it },
        )

        if (estimate != null && !state.sending) {
            Text(
                text = stringResource(R.string.send_estimate, formatDuration(estimate)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (state.sending) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.send_status_progress,
                    state.frameIndex.toFa(),
                    state.frameCount.toFa(),
                    formatDuration(state.remainingSec),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { vm.cancel() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.send_cancel))
            }
        } else {
            Button(
                onClick = { vm.start(text, symbolMs, repeats) },
                enabled = state.torchAvailable && byteCount > 0 && !tooLong,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.send_start))
            }
        }

        if (state.done) {
            Text(
                text = stringResource(R.string.send_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (state.lateWarning) {
            Text(
                text = stringResource(R.string.send_late_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!state.torchAvailable) {
            Text(
                text = stringResource(R.string.issue_no_torch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.issue?.let { issue ->
            Text(
                text = stringResource(issueText(issue)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            text = stringResource(R.string.send_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun issueText(issue: SendIssue): Int = when (issue) {
    SendIssue.NO_TORCH -> R.string.issue_no_torch
    SendIssue.TOO_LONG -> R.string.issue_too_long
    SendIssue.EMPTY -> R.string.issue_empty
    SendIssue.TORCH_FAILED -> R.string.issue_torch_failed
}

/** سرعت خام کانال بر حسب بیت بر ثانیه، با یک رقم اعشار فارسی. */
private fun bitsPerSecondLabel(symbolMs: Int): String {
    val tenths = (10000.0 / (2.0 * symbolMs)).toInt() // دهم بیت بر ثانیه
    return "${(tenths / 10).toFa()}٫${(tenths % 10).toFa()}"
}
