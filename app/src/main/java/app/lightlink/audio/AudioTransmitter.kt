package app.lightlink.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import app.lightlink.dsp.AudioSchedule
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** پخش برنامه‌ی ارسال صدا با AudioTrack به‌صورت جریانی؛ در هر حالت (پایان، لغو، خطا) منابع آزاد می‌شوند. */
class AudioTransmitter(context: Context) {
    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * [onProgress] با شماره‌ی نمونه‌ی در حال پخش (از ۰ تا اندازه‌ی کل) صدا زده می‌شود.
     * هر استثنای AudioTrack به فراخواننده می‌رسد.
     */
    suspend fun transmit(schedule: AudioSchedule, onProgress: (Int) -> Unit) {
        val total = schedule.samples.size
        val minBytes = AudioTrack.getMinBufferSize(
            schedule.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBytes > 0) { "AudioTrack روی این گوشی پشتیبانی نمی‌شود" }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(schedule.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes * 2, CHUNK_SAMPLES * 2 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { }
            .build()
        audioManager.requestAudioFocus(focus)

        try {
            track.play()
            var written = 0
            while (written < total) {
                currentCoroutineContext().ensureActive()
                val size = minOf(CHUNK_SAMPLES, total - written)
                val n = track.write(schedule.samples, written, size) // بلوکه می‌شود تا بافر جا باز کند
                check(n >= 0) { "خطا در نوشتن صدا: $n" }
                written += n
                onProgress(track.playbackHeadPosition.coerceIn(0, total))
            }
            // تا وقتی همه‌ی نمونه‌ها واقعاً پخش نشده‌اند، منتظر می‌مانیم (با سقف زمانی)
            val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS
            while (track.playbackHeadPosition < total && System.currentTimeMillis() < deadline) {
                currentCoroutineContext().ensureActive()
                onProgress(track.playbackHeadPosition.coerceIn(0, total))
                delay(50)
            }
            onProgress(total)
        } finally {
            withContext(NonCancellable) {
                try {
                    track.pause()
                    track.flush()
                } catch (e: IllegalStateException) {
                    // پخش از قبل متوقف شده
                }
                track.release()
                audioManager.abandonAudioFocusRequest(focus)
            }
        }
    }

    private companion object {
        const val CHUNK_SAMPLES = 4096
        const val DRAIN_TIMEOUT_MS = 3000L
    }
}
