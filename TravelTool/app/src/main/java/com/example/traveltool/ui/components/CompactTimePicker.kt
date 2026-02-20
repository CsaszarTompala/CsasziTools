package com.example.traveltool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.ui.theme.DraculaCurrent
import com.example.traveltool.ui.theme.DraculaForeground
import com.example.traveltool.ui.theme.DraculaPurple
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Inline scroll-wheel time picker (like the Android alarm clock).
 *
 * Two side-by-side wheels for hours (0-23) and minutes (0-59).
 * The item in the centre is the selected value; scrolling snaps to
 * the nearest item so the centre always holds a valid choice.
 */
@Composable
fun CompactTimePicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = DraculaCurrent,
    textColor: Color = DraculaForeground,
    accentColor: Color = DraculaPurple,
) {
    Row(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Hour wheel
        WheelPicker(
            value = hour,
            range = 0..23,
            onValueChange = onHourChange,
            textColor = textColor,
            accentColor = accentColor,
            bgColor = containerColor,
            modifier = Modifier.width(68.dp),
        )

        // Separator
        Text(
            text = ":",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.padding(horizontal = 2.dp),
        )

        // Minute wheel
        WheelPicker(
            value = minute,
            range = 0..59,
            onValueChange = onMinuteChange,
            textColor = textColor,
            accentColor = accentColor,
            bgColor = containerColor,
            modifier = Modifier.width(68.dp),
        )
    }
}

// ─── Single wheel ───────────────────────────────────────────────────

/** Number of "padding" (invisible) items above & below so the real first/last
 *  item can sit in the centre slot. */
private const val PADDING_ITEMS = 1

/** Height of every row in the wheel. */
private val ITEM_HEIGHT: Dp = 40.dp

/** How many items are visible at a time (always odd so there's a centre). */
private const val VISIBLE_COUNT = 3  // 1 above · selected · 1 below

@Composable
private fun WheelPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    textColor: Color,
    accentColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
) {
    val items = range.toList()
    val density = LocalDensity.current
    val itemHeightPx = remember(density) { with(density) { ITEM_HEIGHT.toPx() } }

    // The list has PADDING_ITEMS spacer items at index 0 (and bottom).
    // For value V, the real item is at list index V - range.first + PADDING_ITEMS.
    // To place V in the centre, firstVisibleItemIndex should be V - range.first
    // (because centre = firstVisible + PADDING_ITEMS, and item at list index
    //  V-range.first+PADDING_ITEMS is items[V-range.first] = V).
    val initialIndex = (value - range.first).coerceIn(0, items.size - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()

    // Track the value derived from the scroll position.
    // Centre item index in the full list = firstVisibleItemIndex + PADDING_ITEMS.
    // That item in the items array = centreListIndex - PADDING_ITEMS.
    // Combined: centre value = items[firstVisibleItemIndex].
    var lastEmitted by remember { mutableIntStateOf(value) }

    // Use snapshotFlow to reliably detect when scrolling stops and snap.
    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }.collect { (scrolling, firstVisible, offset) ->
            if (!scrolling) {
                // Determine which item is closest to the centre.
                // If the offset exceeds half an item height, the next item is closer.
                val snappedIndex = if (offset > (itemHeightPx * 0.5f).roundToInt()) {
                    firstVisible + 1
                } else {
                    firstVisible
                }
                val clamped = snappedIndex.coerceIn(0, items.size - 1)

                // Snap the list cleanly.
                if (clamped != firstVisible || offset != 0) {
                    listState.animateScrollToItem(clamped)
                }

                // Emit the new value.
                val realValue = items[clamped]
                if (realValue != lastEmitted) {
                    lastEmitted = realValue
                    onValueChange(realValue)
                }
            }
        }
    }

    // External value changed (e.g. from a reset) → scroll to it.
    LaunchedEffect(value) {
        if (value != lastEmitted) {
            lastEmitted = value
            val target = (value - range.first).coerceIn(0, items.size - 1)
            listState.animateScrollToItem(target)
        }
    }

    val totalHeight = ITEM_HEIGHT * VISIBLE_COUNT

    Box(
        modifier = modifier
            .height(totalHeight)
            .background(accentColor.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .fadingEdges(bgColor),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Padding items at top (so first real value can sit in centre)
            items(PADDING_ITEMS) {
                Spacer(Modifier.height(ITEM_HEIGHT))
            }

            // Real items
            items(items.size) { index ->
                val itemValue = items[index]
                // Centre item = items[firstVisibleItemIndex]
                val centreItemsIndex = listState.firstVisibleItemIndex.coerceIn(0, items.size - 1)
                val distFromCentre = abs(index - centreItemsIndex)

                val isCentre = distFromCentre == 0
                val alphaValue = when (distFromCentre) {
                    0 -> 1f
                    1 -> 0.45f
                    else -> 0.2f
                }
                val fontSize = if (isCentre) 26.sp else 18.sp
                val weight = if (isCentre) FontWeight.Bold else FontWeight.Normal
                val color = if (isCentre) accentColor else textColor

                Box(
                    modifier = Modifier
                        .height(ITEM_HEIGHT)
                        .fillMaxWidth()
                        .alpha(alphaValue),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = String.format("%02d", itemValue),
                        fontSize = fontSize,
                        fontWeight = weight,
                        color = color,
                    )
                }
            }

            // Padding items at bottom
            items(PADDING_ITEMS) {
                Spacer(Modifier.height(ITEM_HEIGHT))
            }
        }

        // Highlight band behind the centre slot
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT)
                .align(Alignment.Center)
                .background(accentColor.copy(alpha = 0.10f), RoundedCornerShape(6.dp)),
        )
    }
}

// ─── Fading-edge modifier ───────────────────────────────────────────

/**
 * Draws vertical fading edges at top and bottom so items scroll in/out
 * smoothly, mimicking the native Android number picker.
 */
private fun Modifier.fadingEdges(
    bgColor: Color,
    edgeHeight: Dp = 20.dp,
): Modifier = this.drawWithContent {
    drawContent()
    val h = edgeHeight.toPx()
    // Top fade
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(bgColor, Color.Transparent),
            startY = 0f,
            endY = h,
        ),
        size = size.copy(height = h),
    )
    // Bottom fade
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, bgColor),
            startY = size.height - h,
            endY = size.height,
        ),
        size = size.copy(height = h),
        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h),
    )
}
