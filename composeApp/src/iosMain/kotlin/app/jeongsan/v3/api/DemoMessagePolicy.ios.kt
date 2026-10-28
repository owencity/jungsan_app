@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.jeongsan.v3.api

import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCompatibilityMapping

internal actual fun compatibilityNormalize(text: String): String =
    NSString.create(string = text).precomposedStringWithCompatibilityMapping
