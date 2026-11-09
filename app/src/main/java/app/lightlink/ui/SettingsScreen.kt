package app.lightlink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lightlink.R
import app.lightlink.lightning.Jalali
import app.lightlink.lightning.LightningMath
import app.lightlink.lightning.LightningViewModel
import java.util.Calendar

/** تنظیم دما (برای سرعت صوت) و فهرست اندازه‌گیری‌های قبلی با تاریخ شمسی. */
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: LightningViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refreshSettings() }

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
                text = stringResource(R.string.feature_settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Text(
            text = stringResource(R.string.settings_temperature_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = { vm.setTemperature(state.temperatureC - 1) }) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = stringResource(R.string.settings_temperature_value, state.temperatureC.toFa()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedButton(onClick = { vm.setTemperature(state.temperatureC + 1) }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
        Text(
            text = stringResource(
                R.string.settings_sound_speed,
                LightningMath.speedOfSound(state.temperatureC.toDouble()).toInt().toFa(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(R.string.settings_history_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (state.history.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.history.forEach { r ->
                val cal = Calendar.getInstance().apply { timeInMillis = r.epochMillis }
                val j = Jalali.fromGregorian(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH),
                )
                val time = "${cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')}:" +
                    cal.get(Calendar.MINUTE).toString().padStart(2, '0')
                val est = LightningMath.estimate(r.delayMillis / 1000.0, r.temperatureC.toDouble())
                Text(
                    text = stringResource(
                        R.string.settings_history_row,
                        "${j.year}/${j.month}/${j.day}".toPersianDigits(),
                        time.toPersianDigits(),
                        formatSeconds(r.delayMillis),
                        if (est.reliable) formatDistance(est.distanceMeters) else stringResource(R.string.settings_history_unreliable),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(onClick = { vm.clearHistory() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_history_clear))
            }
        }

        Text(
            text = stringResource(R.string.settings_about),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
