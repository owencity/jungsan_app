package app.jeongsan.v3

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual fun encodeSettlementPng(bitmap: ImageBitmap): ByteArray = ByteArrayOutputStream().use { output ->
    val native = bitmap.asAndroidBitmap()
    try { check(native.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray() }
    finally { native.recycle() }
}

/** 시스템 파일 선택기가 쓰기 권한을 위임하므로 전체 사진/저장소 권한을 요청하지 않는다. */
@Composable
actual fun rememberSaveSettlementImage(): (ByteArray, String, (SettlementImageSaveResult) -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<ByteArray, (SettlementImageSaveResult) -> Unit>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val request = pending
        pending = null
        if (request != null) {
            if (uri == null) request.second(SettlementImageSaveResult.CANCELLED)
            else scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(request.first) }
                        SettlementImageSaveResult.SAVED
                    } catch (_: Exception) { SettlementImageSaveResult.FAILED }
                }
                request.second(result)
            }
        }
    }
    return { bytes, filename, callback ->
        if (pending != null) callback(SettlementImageSaveResult.FAILED)
        else {
            pending = bytes to callback
            try { launcher.launch(filename) }
            catch (_: Exception) { pending = null; callback(SettlementImageSaveResult.FAILED) }
        }
    }
}
