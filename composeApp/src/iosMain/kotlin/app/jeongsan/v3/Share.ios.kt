package app.jeongsan.v3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** 설치 여부 사전 조회 없이 실제 실행 결과를 받는다. 실패해도 먼저 복사한 내용은 유지된다. */
@Composable
actual fun rememberOpenKakaoTalk(): (onResult: (Boolean) -> Unit) -> Unit = remember {
    { onResult ->
        val url = NSURL.URLWithString("kakaotalk://launch")
        if (url == null) onResult(false)
        else UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = onResult)
    }
}
