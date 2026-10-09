package app.jeongsan.v3.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.util.generateNonce
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 앱 로그인(FC-014 1-1, 서버 `feat/auth-release`). 앱은 쿠키를 받을 수 없어서 이렇게 한다.
 *
 * ```
 * 앱: verifier(일회용 비밀) 생성 → 브라우저로 /auth/{kakao|apple}/login?client=app&codeChallenge=SHA256(verifier)
 * 서버: 카카오·애플 로그인 → jeongsan://auth?ticket=… 로 앱을 연다
 * 앱: POST /auth/app/exchange {ticket, codeVerifier} → Bearer 토큰 저장
 * ```
 *
 * PKCE(RFC 7636) — 다른 앱이 `jeongsan://` 주소를 가로채 티켓을 훔쳐도, verifier 는 이 앱 메모리에만 있어 토큰으로 바꿀 수 없다.
 */
object AppAuth {
    /** 로그인 중인 verifier. 브라우저에 다녀오는 동안만 있다 */
    private var verifier: String? = null

    /** 로그인을 시작한다 — 브라우저로 열 주소를 돌려준다 */
    fun start(client: ApiClient, provider: Provider): String {
        // generateNonce 는 플랫폼 보안 난수(JVM SecureRandom 등) 16바이트의 hex — 두 번 이어 64자. RFC 7636 허용 문자 안이다
        val v = generateNonce() + generateNonce()
        verifier = v
        return client.loginUrl(provider, Pkce.challenge(v))
    }

    /** 브라우저에서 돌아온 주소. 화면(App)이 지켜보다가 [finish]로 처리한다 */
    var returned by mutableStateOf<String?>(null)
        private set

    /** 플랫폼(Android onNewIntent·iOS onOpenURL)이 부른다. 로그인 주소가 아니면 무시 */
    fun handle(url: String) {
        if (ticketOf(url) != null) returned = url
    }

    /** 티켓을 토큰으로 바꾼다. 성공하면 null, 실패하면 보여줄 문구 */
    suspend fun finish(client: ApiClient, url: String): String? {
        returned = null
        val ticket = ticketOf(url) ?: return "로그인 주소가 올바르지 않아요"
        val v = verifier ?: return "로그인을 처음부터 다시 해주세요" // 앱이 브라우저에 있는 동안 종료됐다
        verifier = null
        return try {
            client.exchangeTicket(ticket, v)
            null
        } catch (e: ApiError) {
            V3Gateway.messageOf(e)
        } catch (e: Exception) {
            // 응답 모양이 다르거나(서버 배포 차이) 읽다가 끊김 — 화면을 죽이지 않고 다시 하게 한다
            "로그인하지 못했어요. 다시 시도해주세요"
        }
    }

    /** `jeongsan://auth?ticket=…` 에서 티켓만 */
    fun ticketOf(url: String): String? {
        if (!url.startsWith("jeongsan://auth")) return null
        return url.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("ticket=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }
    }

    enum class Provider(val path: String) { KAKAO("kakao"), APPLE("apple") }
}

/** PKCE S256 — `BASE64URL(SHA256(verifier))`, 패딩 없음 */
object Pkce {
    @OptIn(ExperimentalEncodingApi::class)
    fun challenge(verifier: String): String =
        Base64.UrlSafe.encode(Sha256.digest(verifier.encodeToByteArray())).trimEnd('=')
}

/**
 * SHA-256(FIPS 180-4). Kotlin 공통 코드에는 해시가 없어 직접 둔다 — PKCE 한 곳에서만 쓴다.
 * 맞는지는 표준 시험값("abc")과 RFC 7636 부록 B 예시로 확인한다(AppAuthTest).
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object Sha256 {
    private val K = uintArrayOf(
        0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
        0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
        0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
        0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
        0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
        0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
        0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
        0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u,
    ).map { it.toInt() }

    fun digest(input: ByteArray): ByteArray {
        val h = uintArrayOf(0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au, 0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u).map { it.toInt() }.toIntArray()
        val bitLen = input.size.toLong() * 8
        val padLen = ((input.size + 9 + 63) / 64) * 64
        val msg = ByteArray(padLen)
        input.copyInto(msg)
        msg[input.size] = 0x80.toByte()
        for (i in 0 until 8) msg[padLen - 1 - i] = (bitLen ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (chunk in 0 until padLen / 64) {
            for (i in 0 until 16) {
                val o = chunk * 64 + i * 4
                w[i] = (msg[o].toInt() and 0xff shl 24) or (msg[o + 1].toInt() and 0xff shl 16) or
                    (msg[o + 2].toInt() and 0xff shl 8) or (msg[o + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 64) {
                val t1 = hh + (e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)) + ((e and f) xor (e.inv() and g)) + K[i] + w[i]
                val t2 = (a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)) + ((a and b) xor (a and c) xor (b and c))
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return ByteArray(32) { i -> (h[i / 4] ushr (24 - 8 * (i % 4))).toByte() }
    }
}
