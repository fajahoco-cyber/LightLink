package app.lightlink.light

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lightlink.dsp.LightConfig
import app.lightlink.dsp.LightModulator
import app.lightlink.dsp.LightSchedule
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

enum class SendIssue { NO_TORCH, TOO_LONG, EMPTY, TORCH_FAILED }

data class SendUiState(
    val torchAvailable: Boolean = true,
    val sending: Boolean = false,
    val done: Boolean = false,
    val progress: Float = 0f,
    val frameIndex: Int = 0,
    val frameCount: Int = 0,
    val remainingSec: Int = 0,
    val lateWarning: Boolean = false,
    val issue: SendIssue? = null,
)

/** تخمین مدت ارسال بر حسب ثانیه؛ برای پیام خالی یا بیش‌ازحد بلند null. */
fun estimateSendSeconds(text: String, symbolMs: Int, repeats: Int): Int? {
    val clean = text.trim()
    if (clean.isEmpty()) return null
    return try {
        val schedule = LightModulator.schedule(
            MessageSplitter.split(clean, 0),
            LightConfig(symbolMs = symbolMs, repeats = repeats),
        )
        (schedule.durationMs / 1000).toInt()
    } catch (e: IllegalArgumentException) {
        null
    }
}

class LightSendViewModel(app: Application) : AndroidViewModel(app) {
    private val torch = TorchTransmitter(app)
    private val _state = MutableStateFlow(SendUiState(torchAvailable = torch.isAvailable))
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastMsgId = Random.nextInt(256)

    fun start(text: String, symbolMs: Int, repeats: Int) {
        if (job?.isActive == true) return
        if (!torch.isAvailable) {
            _state.update { it.copy(issue = SendIssue.NO_TORCH) }
            return
        }
        val clean = text.trim()
        if (clean.isEmpty()) {
            _state.update { it.copy(issue = SendIssue.EMPTY) }
            return
        }
        // هر ارسال شناسه‌ی متفاوتی از ارسال قبلی دارد تا گیرنده آن را «تکراری» نداند.
        lastMsgId = (lastMsgId + 1 + Random.nextInt(255)) % 256
        val schedule: LightSchedule = try {
            LightModulator.schedule(
                MessageSplitter.split(clean, lastMsgId),
                LightConfig(symbolMs = symbolMs, repeats = repeats),
            )
        } catch (e: MessageTooLongException) {
            _state.update { it.copy(issue = SendIssue.TOO_LONG) }
            return
        }

        _state.value = SendUiState(
            torchAvailable = true,
            sending = true,
            frameCount = schedule.frameStarts.size,
            frameIndex = 1,
            remainingSec = (schedule.durationMs / 1000).toInt(),
        )
        job = viewModelScope.launch(Dispatchers.Default) {
            try {
                val late = torch.transmit(schedule) { i -> publish(schedule, i) }
                val lateShare = late.toFloat() / schedule.levels.size
                _state.update {
                    it.copy(sending = false, done = true, progress = 1f, remainingSec = 0, lateWarning = lateShare > 0.05f)
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(sending = false, progress = 0f) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, issue = SendIssue.TORCH_FAILED) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun clearStatus() {
        _state.update { it.copy(issue = null, done = false, lateWarning = false) }
    }

    private fun publish(schedule: LightSchedule, symbolIndex: Int) {
        val total = schedule.levels.size
        val frame = schedule.frameAt(minOf(symbolIndex, total - 1))
        val remaining = ((total - symbolIndex).toLong() * schedule.symbolMs / 1000).toInt()
        _state.update {
            it.copy(progress = symbolIndex.toFloat() / total, frameIndex = frame + 1, remainingSec = remaining)
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
