package app.lightlink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lightlink.R
import app.lightlink.lightning.LightningEstimate
import app.lightlink.lightning.LightningViewModel
import app.lightlink.lightning.SafetyLevel

@Composable
fun LightningScreen(onBack: () -> Unit, vm: LightningViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refreshSettings() }

    val view = LocalView.current
    DisposableEffect(state.timing) {
        view.keepScreenOn = state.timing
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) {
        onDispose { vm.cancel() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(
                text = stringResource(R.string.feature_lightning_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Text(
            text = stringResource(R.string.lightning_safety_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource(R.string.lightning_howto),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.lightning_temperature_used, state.temperatureC.toFa()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = formatSeconds(state.elapsedMillis),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )

        if (!state.timing) {
            Button(
                onClick = { vm.onFlash() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            ) { Text(stringResource(R.string.lightning_saw_flash), style = MaterialTheme.typography.titleLarge) }
        } else {
            Button(
                onClick = { vm.onThunder() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            ) { Text(stringResource(R.string.lightning_heard_thunder), style = MaterialTheme.typography.titleLarge) }
            OutlinedButton(onClick = { vm.cancel() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.lightning_cancel))
            }
        }

        state.estimate?.let { ResultCard(it) }

        Text(
            text = stringResource(R.string.lightning_limits),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultCard(e: LightningEstimate) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!e.reliable) {
            Text(
                text = stringResource(R.string.lightning_unreliable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return
        }
        Text(
            text = stringResource(R.string.lightning_result_distance, formatDistance(e.distanceMeters)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(
                R.string.lightning_result_range,
                formatDistance(e.minMeters),
                formatDistance(e.maxMeters),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val advice = when (e.level) {
            SafetyLevel.IMMEDIATE -> R.string.lightning_level_immediate
            SafetyLevel.DANGER -> R.string.lightning_level_danger
            SafetyLevel.DISTANT -> R.string.lightning_level_distant
        }
        Text(
            text = stringResource(advice),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (e.level == SafetyLevel.DISTANT) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.lightning_rule_30_30),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatSeconds(millis: Long): String {
    val tenths = (millis / 100).toInt()
    return "${(tenths / 10).toFa()}٫${(tenths % 10).toFa()} ثانیه"
}

/** متر یا کیلومتر با یک رقم اعشار فارسی. */
internal fun formatDistance(meters: Double): String =
    if (meters < 1000.0) {
        "${meters.toInt().toFa()} متر"
    } else {
        val tenths = (meters / 100.0).toInt()
        "${(tenths / 10).toFa()}٫${(tenths % 10).toFa()} کیلومتر"
    }
