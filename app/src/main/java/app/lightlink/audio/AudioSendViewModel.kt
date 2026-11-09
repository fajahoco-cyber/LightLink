package app.lightlink.audio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lightlink.dsp.AudioConfig
import app.lightlink.dsp.AudioModulator
import app.lightlink.dsp.AudioSchedule
import app.lightlink.protocol.MessageSplitter
import app.lightlink.protocol.MessageTooLongException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class AudioSendIssue { TOO_LONG, EMPTY, PLAYBACK_FAILED }

data class AudioSendUiState(
    val sending: Boolean = false,
    val done: Boolean = false,
    val progress: Float = 0f,
    val frameIndex: Int = 0,
    val frameCount: Int = 0,
    val remainingSec: Int = 0,
    val issue: AudioSendIssue? = null,
)

/** تخمین مدت ارسال صدا بر حسب ثانیه؛ برای پیام خالی یا بیش‌ازحد بلند null. */
fun estimateAudioSendSeconds(text: String, symbolMs: Int, repeats: Int): Int? {
    val clean = text.trim()
    if (clean.isEmpty()) return null
    return try {
        val schedule = AudioModulator.schedule(
            MessageSplitter.split(clean, 0),
            AudioConfig(symbolMs = symbolMs, repeats = repeats),
        )
        ((schedule.durationMs + 999) / 1000).toInt()
    } catch (e: IllegalArgumentException) {
        null
    }
}

class AudioSendViewModel(app: Application) : AndroidViewModel(app) {
    private val transmitter = AudioTransmitter(app)
    private val _state = MutableStateFlow(AudioSendUiState())
    val state: StateFlow<AudioSendUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastMsgId = Random.nextInt(256)

    fun start(text: String, symbolMs: Int, repeats: Int) {
        if (job?.isActive == true) return
        val clean = text.trim()
        if (clean.isEmpty()) {
            _state.update { it.copy(issue = AudioSendIssue.EMPTY) }
            return
        }
        lastMsgId = (lastMsgId + 1 + Random.nextInt(255)) % 256
        val schedule: AudioSchedule = try {
            AudioModulator.schedule(
                MessageSplitter.split(clean, lastMsgId),
                AudioConfig(symbolMs = symbolMs, repeats = repeats),
            )
        } catch (e: MessageTooLongException) {
            _state.update { it.copy(issue = AudioSendIssue.TOO_LONG) }
            return
        }

        _state.value = AudioSendUiState(
            sending = true,
            frameCount = schedule.frameStarts.size,
            frameIndex = 1,
            remainingSec = ((schedule.durationMs + 999) / 1000).toInt(),
        )
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                transmitter.transmit(schedule) { pos -> publish(schedule, pos) }
                _state.update { it.copy(sending = false, done = true, progress = 1f, remainingSec = 0) }
            } catch (e: CancellationException) {
                _state.update { it.copy(sending = false, progress = 0f) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, issue = AudioSendIssue.PLAYBACK_FAILED) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun clearStatus() {
        _state.update { it.copy(issue = null, done = false) }
    }

    private fun publish(schedule: AudioSchedule, position: Int) {
        val total = schedule.samples.size
        val frame = schedule.frameAt(minOf(position, total - 1))
        val remaining = ((total - position).toLong() / schedule.sampleRate).toInt()
        _state.update {
            it.copy(progress = position.toFloat() / total, frameIndex = frame + 1, remainingSec = remaining)
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
