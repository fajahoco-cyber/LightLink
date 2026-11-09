package app.lightlink.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lightlink.R
import app.lightlink.audio.AudioReceiveViewModel

@Composable
fun AudioReceiveTab(vm: AudioReceiveViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }

    val view = LocalView.current
    DisposableEffect(state.receiving) {
        view.keepScreenOn = state.receiving
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) {
        onDispose { vm.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!granted) {
            val activity = context.findActivity()
            val permanent = denied && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.mic_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.mic_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (denied) {
                    Text(
                        text = stringResource(R.string.mic_permission_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (permanent) {
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.recv_settings_button)) }
                } else {
                    Button(
                        onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.mic_permission_button)) }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.audio_level),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(progress = { state.level.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }

            val stateText = when {
                !state.receiving -> R.string.recv_state_idle
                state.locked -> R.string.recv_state_locked
                else -> R.string.audio_state_waiting
            }
            Text(
                text = stringResource(stateText),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (state.locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )

            if (state.micFailed) {
                Text(
                    text = stringResource(R.string.issue_mic_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.progressTotal > 0) {
                Text(
                    text = stringResource(
                        R.string.recv_progress,
                        state.progressReceived.toFa(),
                        state.progressTotal.toFa(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.missing.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.recv_missing,
                            state.missing.joinToString("، ") { (it + 1).toFa() },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.accepted > 0 || state.rejected > 0) {
                Text(
                    text = stringResource(
                        R.string.recv_stats,
                        state.accepted.toFa(),
                        state.rejected.toFa(),
                        state.correctedBits.toFa(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.message?.let { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.recv_message_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (state.invalidText) {
                Text(
                    text = stringResource(R.string.recv_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.receiving) {
                OutlinedButton(onClick = { vm.stop() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.recv_stop))
                }
            } else {
                Button(onClick = { vm.start() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.recv_start))
                }
            }
            if (state.message != null || state.accepted > 0 || state.rejected > 0) {
                OutlinedButton(onClick = { vm.clear() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.recv_clear))
                }
            }

            Text(
                text = stringResource(R.string.audio_recv_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
