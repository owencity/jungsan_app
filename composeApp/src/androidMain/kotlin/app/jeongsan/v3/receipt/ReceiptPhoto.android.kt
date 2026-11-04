package app.jeongsan.v3.receipt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun ReceiptBackHandler(onBack: () -> Unit) { BackHandler(onBack = onBack) }

/** 썸네일 결과 대신 전용 캐시 파일로 원본을 받고 선택한 사진 한 장만 읽는다. */
@Composable
actual fun rememberReceiptPhotoPicker(): (ReceiptSource, (ReceiptPhotoResult) -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<((ReceiptPhotoResult) -> Unit)?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun finish(result: ReceiptPhotoResult) {
        val callback = pending
        pending = null
        cameraFile?.delete()
        cameraFile = null
        callback?.invoke(result)
    }

    fun read(uri: Uri?) {
        if (uri == null) { finish(ReceiptPhotoResult.Cancelled); return }
        val callback = pending
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val raw = checkNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
                        ByteArrayOutputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var count: Int
                            while (stream.read(buffer).also { count = it } != -1) {
                                require(output.size().toLong() + count <= 32 * 1024 * 1024)
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                    }
                    ReceiptPhotoResult.Selected(normalizeReceiptPhoto(raw))
                } catch (_: Exception) { ReceiptPhotoResult.Failed("사진을 읽지 못했어요. 더 작은 사진을 선택하거나 다시 촬영해 주세요.") }
            }
            if (pending === callback) finish(result)
        }
    }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { read(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        read(if (ok) cameraFile?.let { FileProvider.getUriForFile(context, "${context.packageName}.receipts", it) } else null)
    }
    DisposableEffect(Unit) {
        onDispose { pending = null; cameraFile?.delete(); cameraFile = null }
    }
    return { source, callback ->
        if (pending != null) callback(ReceiptPhotoResult.Failed("사진을 선택하고 있어요. 잠시 기다려 주세요."))
        else {
            pending = callback
            try {
                if (source == ReceiptSource.PHOTOS) photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else {
                    val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
                    // 프로세스 종료로 남은 촬영 파일도 다음 촬영 때 정리한다.
                    directory.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
                    val file = File.createTempFile("receipt-", ".jpg", directory)
                    cameraFile = file
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.receipts", file))
                }
            } catch (_: Exception) { finish(ReceiptPhotoResult.Failed("카메라를 열지 못했어요. 사진 선택이나 직접 입력을 이용해 주세요.")) }
        }
    }
}

/** 작은 수량을 위해 품질 95를 유지한다. 파일 한도를 맞추려고 반복 압축하지 않는다. */
private fun normalizeReceiptPhoto(raw: ByteArray): ReceiptPhoto {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 6400) sample *= 2
    val decoded = checkNotNull(BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }))
    var current = decoded
    try {
        val orientation = runCatching { ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }
                8 -> setRotate(270f)
            }
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
            if (rotated !== current) current.recycle()
            current = rotated
        }
        val scale = minOf(1.0, 3200.0 / maxOf(current.width, current.height))
        if (scale < 1.0) {
            val resized = Bitmap.createScaledBitmap(current, maxOf(1,(current.width * scale).toInt()), maxOf(1,(current.height * scale).toInt()), true)
            if (resized !== current) current.recycle()
            current = resized
        }
        val bytes = ByteArrayOutputStream().use { output -> check(current.compress(Bitmap.CompressFormat.JPEG,95,output)); output.toByteArray() }
        require(bytes.size <= MAX_RECEIPT_BYTES)
        return ReceiptPhoto(bytes, current.asImageBitmap())
    } catch (error: Exception) { current.recycle(); throw error }
}
