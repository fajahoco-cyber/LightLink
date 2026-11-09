package app.lightlink.light

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.lightlink.dsp.LightReceiverCore
import app.lightlink.dsp.ReceiverEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

data class ReceiveUiState(
    val receiving: Boolean = false,
    val locked: Boolean = false,
    /** روشنایی فعلی ناحیه‌ی ROI (۰ تا ۲۵۵)؛ برای نشانه‌روی فلش حتی پیش از شروع دریافت. */
    val brightness: Int = 0,
    /** کیفیت سیگنال (۰ تا ۱) بر پایه‌ی اختلاف روشن/خاموش دیده‌شده. */
    val signal: Float = 0f,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val correctedBits: Int = 0,
    val progressReceived: Int = 0,
    val progressTotal: Int = 0,
    val missing: List<Int> = emptyList(),
    val message: String? = null,
    val invalidText: Boolean = false,
)

class LightReceiveViewModel(app: Application) : AndroidViewModel(app) {
    val roiHolder = RoiHolder()

    private val _roi = MutableStateFlow(0.5f to 0.5f)
    val roi: StateFlow<Pair<Float, Float>> = _roi.asStateFlow()

    private val _state = MutableStateFlow(ReceiveUiState())
    val state: StateFlow<ReceiveUiState> = _state.asStateFlow()

    private val lock = Any()
    private val core = LightReceiverCore { event -> handle(event) }

    @Volatile
    private var receiving = false
    private var sampleCount = 0

    fun setRoi(u: Float, v: Float) {
        roiHolder.u = u
        roiHolder.v = v
        _roi.value = u to v
    }

    /** از رشته‌ی تحلیلگر دوربین برای هر فریم فراخوانی می‌شود. */
    fun onSample(timeNs: Long, brightness: Double) {
        sampleCount++
        var contrast = 0.0
        if (receiving) {
            synchronized(lock) {
                core.pushSample(timeNs, brightness)
                contrast = core.contrast
            }
        }
        if (sampleCount % 4 == 0) {
            val level = brightness.roundToInt().coerceIn(0, 255)
            val signal = (contrast / 80.0).toFloat().coerceIn(0f, 1f)
            _state.update { it.copy(brightness = level, signal = signal) }
        }
    }

    fun start() {
        synchronized(lock) { core.resetMessage() }
        _state.value = ReceiveUiState(receiving = true, brightness = _state.value.brightness)
        receiving = true
    }

    fun stop() {
        receiving = false
        _state.update { it.copy(receiving = false, locked = false, signal = 0f) }
    }

    fun clear() {
        receiving = false
        synchronized(lock) { core.resetMessage() }
        _state.value = ReceiveUiState(brightness = _state.value.brightness)
    }

    private fun handle(event: ReceiverEvent) {
        when (event) {
            ReceiverEvent.Locked -> _state.update { it.copy(locked = true) }
            ReceiverEvent.Lost -> _state.update { it.copy(locked = false) }
            is ReceiverEvent.PacketAccepted -> {
                val missing = core.missingSeqs()
                _state.update {
                    it.copy(
                        locked = false,
                        accepted = it.accepted + 1,
                        correctedBits = it.correctedBits + event.correctedBits,
                        progressReceived = event.receivedCount,
                        progressTotal = event.total,
                        missing = missing,
                    )
                }
            }

            ReceiverEvent.PacketDuplicate -> _state.update { it.copy(locked = false) }
            is ReceiverEvent.PacketRejected ->
                _state.update { it.copy(locked = false, rejected = it.rejected + 1) }

            is ReceiverEvent.MessageComplete ->
                _state.update { it.copy(message = event.text, invalidText = false, missing = emptyList()) }

            ReceiverEvent.MessageInvalid -> _state.update { it.copy(invalidText = true) }
        }
    }

    override fun onCleared() {
        receiving = false
        super.onCleared()
    }
}
