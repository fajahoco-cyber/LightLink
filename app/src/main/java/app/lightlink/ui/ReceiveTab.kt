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
import app.lightlink.light.LightReceiveViewModel
import app.lightlink.light.LuminanceAnalyzer

@Composable
fun ReceiveTab(vm: LightReceiveViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val roi by vm.roi.collectAsState()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
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
            PermissionCard(
                denied = denied,
                permanentlyDenied = denied && isPermanentlyDenied(context),
                onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            )
        } else {
            val analyzer = remember { LuminanceAnalyzer(vm.roiHolder) { t, v -> vm.onSample(t, v) } }

            CameraPreviewWithRoi(
                roiU = roi.first,
                roiV = roi.second,
                onRoiChange = { u, v -> vm.setRoi(u, v) },
                analyzer = analyzer,
                modifier = Modifier.fillMaxWidth(0.72f),
            )
            Text(
                text = stringResource(R.string.recv_roi_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LabeledBar(
                label = stringResource(R.string.recv_brightness, state.brightness.toFa()),
                value = state.brightness / 255f,
            )
            LabeledBar(
                label = stringResource(R.string.recv_signal),
                value = state.signal,
            )

            val stateText = when {
                !state.receiving -> R.string.recv_state_idle
                state.locked -> R.string.recv_state_locked
                else -> R.string.recv_state_waiting
            }
            Text(
                text = stringResource(stateText),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (state.locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )

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
                text = stringResource(R.string.recv_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabeledBar(label: String, value: Float) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(progress = { value.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PermissionCard(denied: Boolean, permanentlyDenied: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.recv_permission_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.recv_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (denied) {
            Text(
                text = stringResource(R.string.recv_permission_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (permanentlyDenied) {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.recv_settings_button))
            }
        } else {
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recv_permission_button))
            }
        }
    }
}

/** پس از رد مجوز، اگر سیستم دیگر توضیح نشان نمی‌دهد یعنی رد دائمی است و فقط از تنظیمات می‌شود فعالش کرد. */
private fun isPermanentlyDenied(context: android.content.Context): Boolean {
    val activity = context.findActivity() ?: return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}
