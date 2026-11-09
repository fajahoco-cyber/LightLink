package app.lightlink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lightlink.R

/** صفحه‌ی ارتباط: انتخاب کانال (نور یا صدا) و زبانه‌های ارسال و دریافت. */
@Composable
fun CommScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var channel by rememberSaveable { mutableIntStateOf(0) } // ۰ = نور، ۱ = صدا

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(
                text = stringResource(R.string.feature_comm_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (channel == 0) {
                Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.channel_light)) }
                OutlinedButton(onClick = { channel = 1 }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.channel_audio))
                }
            } else {
                OutlinedButton(onClick = { channel = 0 }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.channel_light))
                }
                Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.channel_audio)) }
            }
        }

        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_send)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_receive)) })
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                channel == 0 && tab == 0 -> SendTab()
                channel == 0 -> ReceiveTab()
                tab == 0 -> AudioSendTab()
                else -> AudioReceiveTab()
            }
        }
    }
}
