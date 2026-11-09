package app.lightlink.audio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lightlink.dsp.AudioReceiverCore
import app.lightlink.dsp.ReceiverEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class AudioReceiveUiState(
    val receiving: Boolean = false,
    val locked: Boolean = false,
    /** سطح تون ورودی (۰ تا ۱)، برای نمایش و تنظیم فاصله/بلندی صدا. */
    val level: Float = 0f,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val correctedBits: Int = 0,
    val progressReceived: Int = 0,
    val progressTotal: Int = 0,
    val missing: List<Int> = emptyList(),
    val message: String? = null,
    val invalidText: Boolean = false,
    val micFailed: Boolean = false,
)

class AudioReceiveViewModel(app: Application) : AndroidViewModel(app) {
    private val recorder = MicRecorder(app)
    private val _state = MutableStateFlow(AudioReceiveUiState())
    val state: StateFlow<AudioReceiveUiState> = _state.asStateFlow()

    private val lock = Any()
    private val core = AudioReceiverCore(sampleRate = recorder.sampleRate) { event -> handle(event) }
    private var job: Job? = null
    private var blocks = 0

    fun start() {
        if (job?.isActive == true) return
        synchronized(lock) { core.resetMessage() }
        _state.value = AudioReceiveUiState(receiving = true)
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                recorder.record { pcm, n ->
                    var amp = 0.0
                    synchronized(lock) {
                        core.pushSamples(pcm, n)
                        amp = core.toneAmplitude
                    }
                    if (++blocks % 2 == 0) {
                        // مقیاس ریشه‌ای تا صداهای ضعیف هم روی نوار دیده شوند
                        val level = sqrt((amp * 6.0).coerceIn(0.0, 1.0)).toFloat()
                        _state.update { it.copy(level = level) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(receiving = false, locked = false, micFailed = true, level = 0f) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.update { it.copy(receiving = false, locked = false, level = 0f) }
    }

    fun clear() {
        job?.cancel()
        synchronized(lock) { core.resetMessage() }
        _state.value = AudioReceiveUiState()
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
        job?.cancel()
        super.onCleared()
    }
}
