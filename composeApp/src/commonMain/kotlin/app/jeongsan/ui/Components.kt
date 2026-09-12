package app.jeongsan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 정산어택 공통 UI 조각.
 * 웹 목업(`profile/src/jeongsan/ui.tsx`, `jeongsan.css`)의 부품을 Compose 로 옮긴 것 —
 * 클래스 이름을 그대로 베끼는 게 아니라, **각진 모서리 + 솔리드 오프셋 그림자**라는
 * 웹의 8비트 규칙을 Compose 관용구로 다시 구현한다.
 */

/**
 * 어두운 글자(ink/ink2) 뒤에 까는 밝은 헤일로. 로그인 화면의 흰 글자에는 **어두운**
 * text-shadow 를 썼지만, 여기는 반대다 — 강변 배경은 대부분 어두운 밤하늘/물이라
 * 어두운 글자가 어두운 배경 위에서 묻힌다. 등산로 표지판이 사진 배경 위에서도
 * 읽히도록 글자에 밝은 테두리를 두르는 것과 같은 원리다 — 배경이 밝든 어둡든
 * 이 헤일로 하나로 대비가 유지된다.
 */
private val InkHalo = Shadow(color = Color.White.copy(alpha = 0.9f), offset = Offset.Zero, blurRadius = 5f)

/** 화면 상단 바 — 뒤로가기 + 제목 + (선택) 상태 배지. 웹의 `Bar`. */
@Composable
fun JsBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    step: String? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Text(
                "‹",
                color = JsColor.ink3,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 8.dp),
            )
        }
        Text(
            title, style = TextStyle(shadow = InkHalo),
            color = JsColor.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
        )
        if (step != null) {
            Box(Modifier.weight(1f))
            Box(
                Modifier.background(JsColor.p50, RectangleShape).padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(step, color = JsColor.p600, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** 항목을 갈라 보여줄 때 붙이는 소제목. 웹의 `.js-lab`/`.js-gsec`. */
@Composable
fun SectionLabel(text: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 16.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, style = TextStyle(shadow = InkHalo),
            color = JsColor.ink2, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
        )
        if (count != null) {
            Text(
                "  $count",
                style = TextStyle(shadow = InkHalo),
                color = JsColor.p600,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** 주 행동 버튼 — 웹의 `.js-cta`. 화면당 하나, 다음 단계로 넘어가는 그 버튼만. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    RetroSurface(
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        background = JsColor.p600,
        border = JsColor.ink,
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** 보조 버튼 — 웹의 `.js-cta2`. 링크 복사·재발급처럼 부차적인 동작. */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    RetroSurface(
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        background = JsColor.surface,
        border = JsColor.ink,
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, color = JsColor.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 점선 추가 버튼 — 웹의 `.js-add`. "+ 새 모임" 처럼 새로 만드는 동작. */
@Composable
fun DashedAddButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(JsColor.p50, RectangleShape)
            .border(JsShape.borderWidth, JsColor.p500, RectangleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = JsColor.p600, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** 태그 색 종류. 웹의 `.js-tag`/`.js-gtype` 변형과 대응한다. */
enum class TagTone { NEUTRAL, FLASH, OK, DONE, WARN, WAIT, ACCENT, MUTED }

/** 작은 라벨 — "번개"/"주기", "수집 중"/"확정됨" 같은 상태 표시. */
@Composable
fun Tag(text: String, tone: TagTone = TagTone.NEUTRAL, modifier: Modifier = Modifier) {
    val (bg, fg, border) = when (tone) {
        TagTone.NEUTRAL -> Triple(JsColor.p50, JsColor.p600, JsColor.p500)
        TagTone.FLASH -> Triple(JsColor.accentBg, JsColor.accentStrong, JsColor.accent)
        TagTone.OK -> Triple(JsColor.okBg, JsColor.ok, JsColor.ok)
        TagTone.DONE -> Triple(JsColor.line, JsColor.ink2, JsColor.ink3)
        TagTone.WARN -> Triple(JsColor.warnBg, JsColor.warn, JsColor.warn)
        // "보냈다고 함" — 완료도 미완료도 아니다. warn 과 톤을 갈라야 "아직 안 냄"과 헷갈리지 않는다.
        TagTone.WAIT -> Triple(JsColor.waitBg, JsColor.wait, JsColor.wait)
        // "미입금" — 참여자가 봐야 하는 내 몫 금액과 같은 색으로 묶어 눈에 띄게 한다.
        TagTone.ACCENT -> Triple(JsColor.accentBg, JsColor.accentStrong, JsColor.accent)
        // "자동 · 수정"(총무 본인 행) — 조작이 필요 없는 정보라는 뜻으로 가장 차분한 톤.
        TagTone.MUTED -> Triple(JsColor.bg, JsColor.ink3, JsColor.line)
    }
    Box(
        modifier
            .wrapContentWidth()
            .background(bg, RectangleShape)
            .border(JsShape.borderWidth, border, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** 멤버 한 명을 나타내는 칩. 총무는 강조색으로. 웹의 `.js-chip`. */
@Composable
fun MemberChip(name: String, isOwner: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(if (isOwner) JsColor.p600 else JsColor.surface, RectangleShape)
            .border(JsShape.borderWidth, if (isOwner) JsColor.p600 else JsColor.line, RectangleShape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            if (isOwner) "$name · 총무" else name,
            color = if (isOwner) Color.White else JsColor.ink2,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 부가 설명. 웹의 `.js-hint`. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 6.dp), color = JsColor.ink3, fontSize = 12.sp, lineHeight = 18.sp)
}

/** 빈 목록 안내. 웹의 `.js-gempty`/`Empty`. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = JsColor.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            body,
            modifier = Modifier.padding(top = 8.dp),
            color = JsColor.ink3,
            fontSize = 12.5.sp,
            lineHeight = 20.sp,
        )
    }
}

/** 입력칸. 웹의 `.js-inp`. */
@Composable
fun JsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    singleLine: Boolean = true,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(JsColor.p600),
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFBFDFF), RectangleShape)
            .border(JsShape.borderWidth, JsColor.line, RectangleShape)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, color = JsColor.ink3, fontSize = 14.sp)
                }
                inner()
            }
        },
    )
}

/** 라디오형 선택 카드 — 모임 종류(번개/주기) 고르기처럼 설명이 붙는 선택지. */
@Composable
fun SelectableCard(
    title: String,
    description: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) JsColor.p50 else Color(0xFFFBFDFF), RectangleShape)
            .border(JsShape.borderWidth, if (selected) JsColor.p500 else JsColor.line, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        // 라디오 점 — 바깥 테두리 원 안에, 선택됐을 때만 작은 원을 채운다.
        Box(
            Modifier
                .padding(top = 2.dp, end = 10.dp)
                .size(16.dp)
                .border(2.dp, if (selected) JsColor.p500 else JsColor.line, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(8.dp).background(JsColor.p500, androidx.compose.foundation.shape.CircleShape))
            }
        }
        Column {
            Text(title, color = if (selected) JsColor.p700 else JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            // 설명 없이 단순 라디오 선택지로 쓸 때(예: 결제자 고르기)는 빈 줄을 남기지 않는다.
            if (description.isNotBlank()) {
                Text(
                    description,
                    modifier = Modifier.padding(top = 3.dp),
                    color = JsColor.ink3,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

/**
 * 화면 껍데기 — 세로 스크롤 + 공통 좌우 여백 + 강변 배경. 웹의 `Shell`.
 *
 * 웹은 `narrow`(폼·근거 화면)와 넓은 목록 화면을 데스크톱에서만 폭으로 구분했다.
 * 폰 화면은 항상 한 폭이라 그 구분이 의미 없다 — 그래서 옮기지 않는다.
 *
 * **배경은 콘텐츠보다 항상 아래다.** 카드(`JsCard`/`RetroSurface`)는 이미 불투명해서
 * 원래도 안전하다. 카드 밖의 맨 텍스트(`JsBar` 제목·`SectionLabel`)는 스크림이 아니라
 * **각자 밝은 헤일로**로 보호한다(`InkHalo`) — 처음엔 화면 배경색을 62% 로 깔아
 * 통째로 보호했는데, 그러면 배경 전체가 하얗게 안개 낀 것처럼 죽어서 장식이
 * 안 보이는 지경이 됐다. 텍스트만 개별로 지키고 배경은 옅게만 죽인다.
 */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        PixelRiverBackground(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(JsColor.bg.copy(alpha = 0.22f)))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            content = content,
        )
    }
}

/** 옅은 테두리 카드 — 차수·기타항목처럼 "정보 묶음 하나"를 담는다. 웹의 `Card`. */
@Composable
fun JsCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    right: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFFFBFDFF), RectangleShape)
            .border(JsShape.borderWidth, JsColor.line, RectangleShape)
            .padding(13.dp),
    ) {
        if (title != null) {
            Row(Modifier.padding(bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (right != null) {
                    Text(right, color = JsColor.p600, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        content()
    }
}

/** 키-값 한 줄. `sub` 면 들여쓰고 옅게 — 차수 안의 술 종류처럼 종속 정보. 웹의 `KV`. */
@Composable
fun KvRow(k: String, v: String? = null, sub: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = if (sub) 10.dp else 0.dp, top = 3.dp, bottom = 3.dp),
    ) {
        Text(
            k,
            modifier = Modifier.weight(1f),
            color = if (sub) JsColor.ink3 else JsColor.ink2,
            fontSize = if (sub) 12.sp else 13.sp,
        )
        if (v != null) {
            Text(v, color = JsColor.ink, fontSize = if (sub) 12.sp else 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 참여자 한 명의 이니셜 아바타. 총무(host)는 진한 남색, 미응답(off)은 회색. 웹의 `Avatar`. */
@Composable
fun JsAvatar(name: String, isHost: Boolean, off: Boolean, modifier: Modifier = Modifier) {
    val bg = if (isHost) JsColor.p700 else if (off) Color(0xFFEEF2F7) else JsColor.p100
    val fg = if (isHost) Color.White else if (off) JsColor.ink3 else JsColor.p600
    Box(
        modifier.size(32.dp).background(bg, RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1), color = fg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * 참여자 한 줄 — 아바타 + 이름(+총무 표시) + 보조 설명 + 오른쪽 슬롯.
 * 웹의 `PersonRow`. 수집 현황·명단·확정·결과 화면이 전부 이 한 줄을 반복해서 쓴다.
 */
@Composable
fun PersonRow(
    name: String,
    isHost: Boolean,
    modifier: Modifier = Modifier,
    off: Boolean = false,
    sub: String? = null,
    right: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JsAvatar(name, isHost, off)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    color = if (off) JsColor.ink3 else JsColor.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (isHost) {
                    Text(
                        "주최자",
                        modifier = Modifier.padding(start = 5.dp),
                        color = JsColor.ink3,
                        fontSize = 11.sp,
                    )
                }
            }
            if (sub != null) {
                Text(sub, modifier = Modifier.padding(top = 2.dp), color = JsColor.ink3, fontSize = 12.sp)
            }
        }
        if (right != null) right()
    }
}

/**
 * 진행률 바 — "N / M명 참여" 식으로 값을 보여주고 아래 막대를 채운다. 웹의 `Progress`.
 * `onEdit` 이 있으면 우상단에 작은 수정 버튼을 띄운다(예: 예상 인원 수정).
 */
@Composable
fun ProgressBar(
    value: Int,
    total: Int,
    unit: String,
    modifier: Modifier = Modifier,
    ok: Boolean = false,
    onEdit: (() -> Unit)? = null,
) {
    val pct = if (total > 0) (value.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$value / $total$unit",
                modifier = Modifier.weight(1f),
                color = JsColor.ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (onEdit != null) {
                Text(
                    "인원 수정",
                    color = JsColor.p600,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .background(Color.White, RectangleShape)
                        .border(1.dp, JsColor.p100, RectangleShape)
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).padding(top = 8.dp)
                .background(Color(0xFFE7EEF8), RectangleShape),
        ) {
            Box(
                Modifier.fillMaxWidth(pct).height(8.dp)
                    .background(if (ok) JsColor.ok else JsColor.p500, RectangleShape),
            )
        }
    }
}

/** 강조 배경 경고 박스 — 확정 전 인원 불일치 등, **개입이 필요한** 상황. 웹의 `WarnBox`. */
@Composable
fun WarnBox(
    title: String,
    modifier: Modifier = Modifier,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
    primaryActionIndex: Int = 0,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(JsColor.warnBg, RectangleShape)
            .border(JsShape.borderWidth, JsColor.warn, RectangleShape)
            .padding(13.dp),
    ) {
        Text(title, color = JsColor.warn, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
        Column(Modifier.padding(top = 8.dp)) { body() }
        if (actions.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEachIndexed { i, (label, onClick) ->
                    if (i == primaryActionIndex) {
                        PrimaryButton(label, modifier = Modifier.weight(1f), onClick = onClick)
                    } else {
                        SecondaryButton(label, modifier = Modifier.weight(1f), onClick = onClick)
                    }
                }
            }
        }
    }
}

/** 송금 한 줄 — "A → B  12,340원". `from` 이 없으면 받는 사람만 보여준다. 웹의 `TransferRow`. */
@Composable
fun TransferRow(to: String, amount: String, from: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (from != null) {
            Text(from, color = JsColor.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(" → ", color = JsColor.ink3, fontSize = 13.sp)
        }
        Text(to, color = JsColor.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Text(amount, color = JsColor.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** 반올림 고정 안내 — 확정 화면에 항상 붙는 문구. 웹의 `FixedNotice`, SPEC §6. */
@Composable
fun FixedNotice(unit: Int, modifier: Modifier = Modifier, extra: String? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(JsColor.p50, RectangleShape)
            .padding(11.dp),
    ) {
        Text(
            "${unit}원 단위로 올림 처리되며, 가장 많이 결제한 분의 부담을 조금이나마 덜기 위함입니다.",
            color = JsColor.ink2,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 18.sp,
        )
        if (extra != null) {
            Text(extra, modifier = Modifier.padding(top = 6.dp), color = JsColor.ink2, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

/** 작은 보조 버튼 — "확인 요청"/"제거"처럼 행 안에서 하나만 콕 집어 누르는 동작. 웹의 `.js-mini`. */
@Composable
fun MiniButton(text: String, modifier: Modifier = Modifier, tone: TagTone = TagTone.NEUTRAL, onClick: () -> Unit) {
    val fg = when (tone) {
        TagTone.WARN -> JsColor.warn
        else -> JsColor.ink2
    }
    Text(
        text,
        modifier = modifier
            .background(Color.White, RectangleShape)
            .border(JsShape.borderWidth, JsColor.line, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
    )
}
