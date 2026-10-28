package app.jeongsan.v3.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** 인증과 데모 진입을 구분해 데모URL을 로그인 티켓으로 처리하지 않는다. */
object AppLinks {
    var demoRequest by mutableIntStateOf(0)
        private set

    fun isDemoLink(url: String): Boolean = url == "jeongsan://demo" || url == "jeongsan://demo/"

    fun handle(url: String) {
        if (isDemoLink(url)) demoRequest++ else AppAuth.handle(url)
    }
}
