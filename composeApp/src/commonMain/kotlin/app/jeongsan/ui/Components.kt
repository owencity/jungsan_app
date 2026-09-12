package app.jeongsan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 정산어택 공통 UI 조각.
 * 웹 목업(`profile/src/jeongsan/ui.tsx`, `jeongsan.css`)의 부품을 Compose 로 옮긴 것 —
 * 클래스 이름을 그대로 베끼는 게 아니라, **각진 모서리 + 솔리드 오프셋 그림자**라는
 * 웹의 8비트 규칙을 Compose 관용구로 다시 구현한다.
 */

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
        Text(title, color = JsColor.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
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
        Text(text, color = JsColor.ink2, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        if (count != null) {
            Text(
                "  $count",
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
    onClick: () -> Unit,
) {
    RetroSurface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
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
enum class TagTone { NEUTRAL, FLASH, OK, DONE, WARN }

/** 작은 라벨 — "번개"/"주기", "수집 중"/"확정됨" 같은 상태 표시. */
@Composable
fun Tag(text: String, tone: TagTone = TagTone.NEUTRAL, modifier: Modifier = Modifier) {
    val (bg, fg, border) = when (tone) {
        TagTone.NEUTRAL -> Triple(JsColor.p50, JsColor.p600, JsColor.p500)
        TagTone.FLASH -> Triple(JsColor.accentBg, JsColor.accentStrong, JsColor.accent)
        TagTone.OK -> Triple(JsColor.okBg, JsColor.ok, JsColor.ok)
        TagTone.DONE -> Triple(JsColor.line, JsColor.ink2, JsColor.ink3)
        TagTone.WARN -> Triple(JsColor.warnBg, JsColor.warn, JsColor.warn)
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

/**
 * 화면 껍데기 — 세로 스크롤 + 공통 좌우 여백. 웹의 `Shell`.
 *
 * 웹은 `narrow`(폼·근거 화면)와 넓은 목록 화면을 데스크톱에서만 폭으로 구분했다.
 * 폰 화면은 항상 한 폭이라 그 구분이 의미 없다 — 그래서 옮기지 않는다.
 */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(JsColor.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        content = content,
    )
}
