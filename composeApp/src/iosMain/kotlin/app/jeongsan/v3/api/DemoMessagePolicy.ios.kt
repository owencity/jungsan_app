@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.jeongsan.v3.api

import platform.Foundation.NSString
import platform.Foundation.create

internal actual fun compatibilityNormalize(text: String): String =
    NSString.create(string = text).precomposedStringWithCompatibilityMapping
