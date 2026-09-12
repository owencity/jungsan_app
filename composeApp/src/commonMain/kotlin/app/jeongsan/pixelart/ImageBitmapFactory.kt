package app.jeongsan.pixelart

import androidx.compose.ui.graphics.ImageBitmap

/**
 * `width*height` 크기의 ARGB(`0xAARRGGBB`) 픽셀 배열을 화면에 그릴 수 있는
 * 비트맵으로 바꾼다. 안드로이드와 iOS 가 비트맵을 만드는 하위 API가 달라
 * (`android.graphics.Bitmap` vs Skia `Bitmap`) 플랫폼별로 구현을 나눈다.
 */
expect fun argbPixelsToImageBitmap(width: Int, height: Int, pixels: IntArray): ImageBitmap
