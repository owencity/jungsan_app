package app.jeongsan.pixelart

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** `Bitmap.createBitmap(IntArray, ...)` 은 우리가 쓰는 것과 같은 `0xAARRGGBB` packed-int 를 그대로 받는다. */
actual fun argbPixelsToImageBitmap(width: Int, height: Int, pixels: IntArray): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
