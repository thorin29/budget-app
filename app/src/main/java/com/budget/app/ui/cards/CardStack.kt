package com.budget.app.ui.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.budget.app.data.CardLogic
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.CardDto
import com.budget.app.data.remote.CardsResponse
import com.budget.app.ui.theme.Budget
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val FAN_DP = 14
private const val EASE_MS = 380

/**
 * The card stack: the summary in front, each card fanned out behind it in the
 * color picked on the web. Throw the front card off either side, or tap it,
 * and it tucks in at the back.
 */
@Composable
fun CardStack(cards: CardsResponse, modifier: Modifier = Modifier) {
    val c = Budget.colors
    var order by remember(cards) { mutableStateOf(CardLogic.faces(cards)) }
    val offsets = remember(cards) { CardLogic.faces(cards).associate { it.key to Animatable(0f) } }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val behind = min(order.size - 1, 3)
        val cardWidth = maxWidth - (FAN_DP * behind).dp
        val cardHeight = cardWidth / 1.586f
        val fanPx = with(density) { FAN_DP.dp.toPx() }
        val outPx = with(density) { cardWidth.toPx() } * 0.75f
        // Far enough that the thrown card leaves the screen entirely.
        val throwPx = with(density) { (maxWidth + cardWidth).toPx() }

        // Every card eases to its slot whenever the order changes.
        LaunchedEffect(order, fanPx) {
            order.forEachIndexed { depth, face ->
                launch { offsets.getValue(face.key).animateTo(fanPx * min(depth, 3), tween(EASE_MS, easing = FastOutSlowInEasing)) }
            }
        }

        // Throws the front card off the side it was swiped toward (right for a tap),
        // then it eases in behind the others from that side.
        fun sendFrontToBack(direction: Float = 1f) {
            val front = order.first()
            scope.launch {
                offsets.getValue(front.key).animateTo(throwPx * direction, tween(240, easing = FastOutSlowInEasing))
                order = CardLogic.next(order)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cardHeight + 4.dp)
                    .semantics { contentDescription = "Cards. Swipe or tap to see the next card." },
            ) {
                order.forEachIndexed { depth, face ->
                    key(face.key) {
                        val scale by animateFloatAsState(1f - 0.035f * min(depth, 3), tween(EASE_MS, easing = FastOutSlowInEasing), label = "scale")
                        val offset = offsets.getValue(face.key)
                        val front = depth == 0
                        Box(
                            Modifier
                                .zIndex((order.size - depth).toFloat())
                                .offset { IntOffset(offset.value.roundToInt(), 0) }
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    rotationZ = if (front) (offset.value / outPx).coerceIn(-2f, 2f) * 6f else 0f
                                    alpha = if (depth > 3) 0f else 1f
                                }
                                .size(cardWidth, cardHeight)
                                .shadow(if (depth <= 3) 8.dp else 0.dp, RoundedCornerShape(18.dp))
                                .clip(RoundedCornerShape(18.dp))
                                .then(
                                    if (front) {
                                        Modifier
                                            .pointerInput(order) {
                                                val tracker = VelocityTracker()
                                                detectHorizontalDragGestures(
                                                    onDragStart = { tracker.resetTracking() },
                                                    onDragEnd = {
                                                        val speed = tracker.calculateVelocity().x
                                                        val far = abs(offset.value) > outPx * 0.25f
                                                        val flung = abs(speed) > 900f
                                                        if (far || flung) {
                                                            // Go the way the card is moving; a quick flick wins over where it sits.
                                                            val toward = if (flung) speed else offset.value
                                                            sendFrontToBack(if (toward < 0f) -1f else 1f)
                                                        } else {
                                                            scope.launch { offset.animateTo(0f, tween(220)) }
                                                        }
                                                    },
                                                    onDragCancel = { scope.launch { offset.animateTo(0f, tween(220)) } },
                                                ) { change, amount ->
                                                    change.consume()
                                                    tracker.addPosition(change.uptimeMillis, change.position)
                                                    scope.launch { offset.snapTo(offset.value + amount) }
                                                }
                                            }
                                            .clickable { sendFrontToBack() }
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            if (face.card == null) SummaryFace(cards) else CardFace(face.card)
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CardLogic.faces(cards).forEach { f ->
                    val on = f.key == order.first().key
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (on) 16.dp else 6.dp)
                            .background(if (on) c.accent else c.line, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tally(items: List<Pair<String, Long>>, ink: Color, soft: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, cents) ->
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), color = soft, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, letterSpacing = 0.5.sp)
                Text(Money.format(cents).removeSuffix(".00"), color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SummaryFace(r: CardsResponse) {
    val c = Budget.colors
    val ink = c.onAccent
    val soft = c.onAccent.copy(alpha = 0.75f)
    Column(
        Modifier.fillMaxSize().background(c.accent).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("All cards", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${r.cards.size} ${if (r.cards.size == 1) "card" else "cards"}", color = soft, fontSize = 12.sp)
        }
        Column {
            Text(
                "To pay this cycle" + (r.nextDue?.let { " · next ${Dates.short(it) ?: it}" } ?: ""),
                color = soft, fontSize = 12.sp, maxLines = 1,
            )
            Text(Money.format(r.payThisCycleCents), color = ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Tally(
            listOf("Balance" to r.totalOwedCents, "Statements" to CardLogic.onStatements(r), "New" to CardLogic.sinceStatements(r)),
            ink, soft,
        )
    }
}

@Composable
private fun CardFace(card: CardDto) {
    val dark = card.color.ink == "dark"
    val ink = if (dark) Color(0xFF15181D) else Color.White
    val soft = if (dark) Color(0x99000000) else Color(0xB3FFFFFF)
    val (label, cents) = CardLogic.lead(card)
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(CardLogic.argb(card.color.from)), Color(CardLogic.argb(card.color.to)))))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(CardLogic.faceName(card.name), color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            card.lastDigits?.let { Text("•••• $it", color = soft, fontSize = 12.sp) }
        }
        Column {
            Text(label, color = soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Money.format(cents), color = ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        val s = card.status
        if (s == null) {
            Text("Add its closing and due days on the web, under Accounts.", color = soft, fontSize = 12.sp)
        } else {
            Column {
                Tally(listOf("Balance" to card.owedNowCents, "Statement" to s.dueCents, "New" to s.newChargesCents), ink, soft)
                CardLogic.promoLine(s)?.let {
                    Text(it, color = soft, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
