package app.lightlink.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** خواندن پیوسته‌ی میکروفون (PCM ۱۶ بیتی تک‌کاناله). منبع «بدون پردازش» ترجیح داده می‌شود تا AGC و حذف نویز تون‌ها را خراب نکنند. */
class MicRecorder(context: Context, val sampleRate: Int = 44100) {
    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun chooseSource(): Int {
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return if (unprocessed == "true") MediaRecorder.AudioSource.UNPROCESSED
        else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    /**
     * تا لغو شدن coroutine ضبط می‌کند و هر بلوک را به [onSamples] می‌دهد.
     * اگر مجوز نباشد یا میکروفون در دسترس نباشد استثنا می‌اندازد.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(onSamples: (ShortArray, Int) -> Unit) {
        val minBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBytes > 0) { "میکروفون با این نرخ نمونه‌برداری پشتیبانی نمی‌شود" }

        val recorder = AudioRecord.Builder()
            .setAudioSource(chooseSource())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes * 2, sampleRate * 2))
            .build()
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "میکروفون آماده نشد" }
            recorder.startRecording()
            val buffer = ShortArray(sampleRate / 20) // ۵۰ میلی‌ثانیه
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = recorder.read(buffer, 0, buffer.size)
                check(n >= 0) { "خطا در خواندن میکروفون: $n" }
                if (n > 0) onSamples(buffer, n)
            }
        } finally {
            try {
                recorder.stop()
            } catch (e: IllegalStateException) {
                // از قبل متوقف است
            }
            recorder.release()
        }
    }
}
