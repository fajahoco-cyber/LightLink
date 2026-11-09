package app.lightlink.light

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/** اندازه‌ی ضلع کادر تحلیل (ROI) به‌عنوان کسری از عرض تصویر نمایش‌داده‌شده. */
object LightRoi {
    const val SIZE_FRACTION = 0.14f
}

/** مختصات مرکز ROI روی تصویر ایستاده (۰ تا ۱)؛ از رشته‌ی تحلیلگر و رشته‌ی UI خوانده/نوشته می‌شود. */
class RoiHolder {
    @Volatile
    var u: Float = 0.5f

    @Volatile
    var v: Float = 0.5f
}

/**
 * میانگین روشنایی (کانال Y) را در کادر ROI حساب می‌کند و برای هر فریم به [onSample] می‌دهد.
 * زمان فریم همان زمان حسگر (نانوثانیه) است.
 */
class LuminanceAnalyzer(
    private val roi: RoiHolder,
    private val onSample: (timeNs: Long, brightness: Double) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val w = image.width
            val h = image.height
            val limit = buffer.limit()

            // مختصات ROI روی تصویر ایستاده را به مختصات بافر (بدون چرخش) تبدیل می‌کنیم.
            val u = roi.u
            val v = roi.v
            val cx: Float
            val cy: Float
            when (image.imageInfo.rotationDegrees) {
                90 -> {
                    cx = v * w
                    cy = (1f - u) * h
                }

                180 -> {
                    cx = (1f - u) * w
                    cy = (1f - v) * h
                }

                270 -> {
                    cx = (1f - v) * w
                    cy = u * h
                }

                else -> {
                    cx = u * w
                    cy = v * h
                }
            }
            val half = (LightRoi.SIZE_FRACTION * minOf(w, h) / 2f).coerceAtLeast(2f)
            val x0 = (cx - half).toInt().coerceIn(0, w - 1)
            val x1 = (cx + half).toInt().coerceIn(0, w - 1)
            val y0 = (cy - half).toInt().coerceIn(0, h - 1)
            val y1 = (cy + half).toInt().coerceIn(0, h - 1)

            var sum = 0L
            var count = 0
            var y = y0
            while (y <= y1) {
                var x = x0
                while (x <= x1) {
                    val index = y * rowStride + x * pixelStride
                    if (index < limit) {
                        sum += buffer.get(index).toInt() and 0xFF
                        count++
                    }
                    x += 2
                }
                y += 2
            }
            if (count > 0) onSample(image.imageInfo.timestamp, sum.toDouble() / count)
        } finally {
            image.close()
        }
    }
}
