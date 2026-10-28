package app.jeongsan.v3.api

import java.text.Normalizer

internal actual fun compatibilityNormalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
