package app.lightlink.light

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import app.lightlink.dsp.LightSchedule
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** کنترل فلش پشت گوشی و اجرای برنامه‌ی ارسال با زمان‌بندی مطلق. */
class TorchTransmitter(context: Context) {
    private val cameraManager: CameraManager =
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val cameraId: String? = findTorchCameraId()

    val isAvailable: Boolean get() = cameraId != null

    private fun findTorchCameraId(): String? = try {
        cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (e: CameraAccessException) {
        null
    }

    /**
     * برنامه را اجرا می‌کند و تعداد نیم‌بیت‌هایی را برمی‌گرداند که بیش از یک‌چهارم طول خود دیر شروع شدند.
     * زمان هر نیم‌بیت از شروع ارسال و با ساعت یکنواخت محاسبه می‌شود، پس تأخیر هر تغییر فلش جمع نمی‌شود.
     * در هر حالت (پایان، لغو، خطا) فلش در پایان خاموش می‌شود.
     */
    suspend fun transmit(schedule: LightSchedule, onSymbol: (Int) -> Unit): Int {
        val id = checkNotNull(cameraId) { "فلش در دسترس نیست" }
        val symbolNs = schedule.symbolMs * 1_000_000L
        var current = false
        var late = 0
        try {
            val start = SystemClock.elapsedRealtimeNanos()
            for (i in schedule.levels.indices) {
                currentCoroutineContext().ensureActive()
                val want = schedule.levels[i]
                if (want != current) {
                    cameraManager.setTorchMode(id, want)
                    current = want
                }
                if (i % 4 == 0) onSymbol(i)
                val deadline = start + (i + 1) * symbolNs
                val now = SystemClock.elapsedRealtimeNanos()
                if (now > deadline + symbolNs / 4) late++
                val remainMs = (deadline - now) / 1_000_000L
                if (remainMs > 3) delay(remainMs - 2)
                while (SystemClock.elapsedRealtimeNanos() < deadline) {
                    // انتظار فعال برای دقت زیر میلی‌ثانیه
                }
            }
            onSymbol(schedule.levels.size)
        } finally {
            withContext(NonCancellable) {
                try {
                    cameraManager.setTorchMode(id, false)
                } catch (e: Exception) {
                    // خاموش‌کردن بی‌خطا؛ اگر نشد کاری از ما برنمی‌آید
                }
            }
        }
        return late
    }
}
