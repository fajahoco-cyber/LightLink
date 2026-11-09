package app.lightlink.lightning

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LightningUiState(
    val timing: Boolean = false,
    val elapsedMillis: Long = 0,
    val estimate: LightningEstimate? = null,
    val temperatureC: Int = AppPrefs.DEFAULT_TEMP,
    val history: List<LightningRecord> = emptyList(),
)

/** فاصله‌سنج: کاربر لحظه‌ی دیدن نور و لحظه‌ی شنیدن صدا را لمس می‌کند. */
class LightningViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPrefs(app)
    private val _state = MutableStateFlow(
        LightningUiState(temperatureC = prefs.temperatureC, history = prefs.history()),
    )
    val state: StateFlow<LightningUiState> = _state.asStateFlow()

    private var startNs = 0L
    private var ticker: Job? = null

    fun refreshSettings() {
        _state.update { it.copy(temperatureC = prefs.temperatureC, history = prefs.history()) }
    }

    /** لحظه‌ی دیدن نور. */
    fun onFlash() {
        if (_state.value.timing) return
        startNs = SystemClock.elapsedRealtimeNanos()
        _state.update { it.copy(timing = true, elapsedMillis = 0, estimate = null) }
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(50)
                _state.update { it.copy(elapsedMillis = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L) }
            }
        }
    }

    /** لحظه‌ی شنیدن رعد. */
    fun onThunder() {
        if (!_state.value.timing) return
        val millis = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L
        ticker?.cancel()
        val temp = prefs.temperatureC
        val estimate = LightningMath.estimate(millis / 1000.0, temp.toDouble())
        prefs.addRecord(LightningRecord(System.currentTimeMillis(), millis, temp))
        _state.update {
            it.copy(timing = false, elapsedMillis = millis, estimate = estimate, history = prefs.history())
        }
    }

    fun cancel() {
        ticker?.cancel()
        _state.update { it.copy(timing = false, elapsedMillis = 0) }
    }

    fun clearResult() {
        _state.update { it.copy(estimate = null, elapsedMillis = 0) }
    }

    fun setTemperature(value: Int) {
        prefs.temperatureC = value
        _state.update { it.copy(temperatureC = prefs.temperatureC) }
    }

    fun clearHistory() {
        prefs.clearHistory()
        _state.update { it.copy(history = emptyList()) }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }
}
