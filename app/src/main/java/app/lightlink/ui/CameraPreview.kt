package app.lightlink.ui

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.lightlink.light.LightRoi
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * پیش‌نمایش دوربین پشت به‌همراه کادر زردرنگ ROI که با لمس جابه‌جا می‌شود.
 * همراه آن یک ImageAnalysis (کانال Y، رزولوشن کم) برای تحلیلگر روشنایی بسته می‌شود.
 * جهت صفحه ثابت (portrait) است، پس تصویر ایستاده نسبت ۳ به ۴ دارد و بدون برش نمایش داده می‌شود.
 */
@Composable
fun CameraPreviewWithRoi(
    roiU: Float,
    roiV: Float,
    onRoiChange: (Float, Float) -> Unit,
    analyzer: ImageAnalysis.Analyzer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val owner = remember(context) { context.findActivity() as? LifecycleOwner }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(owner) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (!disposed && owner != null) {
                    val p = future.get()
                    provider = p
                    bindCamera(context, owner, p, previewView, analyzer, executor)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        onRoiChange(
                            (tap.x / size.width).coerceIn(0f, 1f),
                            (tap.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                },
        ) {
            val side = LightRoi.SIZE_FRACTION * size.width
            drawRect(
                color = Color(0xFFFFC53D),
                topLeft = Offset(roiU * size.width - side / 2f, roiV * size.height - side / 2f),
                size = GeoSize(side, side),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun bindCamera(
    context: Context,
    owner: LifecycleOwner,
    provider: ProcessCameraProvider,
    previewView: PreviewView,
    analyzer: ImageAnalysis.Analyzer,
    executor: ExecutorService,
) {
    val preview = Preview.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build(),
        )
        .build()
        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

    val analysisBuilder = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                )
                .build(),
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    applyShortExposureIfSupported(context, analysisBuilder)

    val analysis = analysisBuilder.build().also { it.setAnalyzer(executor, analyzer) }

    provider.unbindAll()
    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
}

/**
 * اگر حسگر نوردهی دستی را پشتیبانی کند، نوردهی کوتاه ثابت (حدود ۱/۲۵۰ ثانیه) تنظیم می‌شود:
 * نور محیط تیره می‌ماند و فلش روشن به‌صورت نقطه‌ی پرنور دیده می‌شود و نوردهی خودکار با چشمک‌زدن فلش نمی‌جنگد.
 * در گوشی‌هایی که این قابلیت را ندارند، نوردهی خودکار می‌ماند.
 */
@SuppressLint("UnsafeOptInUsageError")
private fun applyShortExposureIfSupported(context: Context, builder: ImageAnalysis.Builder) {
    try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = manager.cameraIdList.firstOrNull { cameraId ->
            manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: return
        val chars = manager.getCameraCharacteristics(id)
        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return
        if (!capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) return
        val exposureRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: return
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: return

        val exposure = 4_000_000L.coerceIn(exposureRange.lower, exposureRange.upper)
        val iso = 400.coerceIn(isoRange.lower, isoRange.upper)

        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
    } catch (e: Exception) {
        // اگر ویژگی‌های دوربین خوانده نشد، با نوردهی خودکار ادامه می‌دهیم.
    }
}
