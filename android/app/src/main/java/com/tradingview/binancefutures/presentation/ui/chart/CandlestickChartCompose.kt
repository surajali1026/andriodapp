package com.tradingview.binancefutures.presentation.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradingview.binancefutures.domain.model.*
import com.tradingview.binancefutures.presentation.ui.theme.*
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
    if (l2 == 0f) {
        val dx = px - x1
        val dy = py - y1
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
    val t = (((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2).coerceIn(0f, 1f)
    val projX = x1 + t * (x2 - x1)
    val projY = y1 + t * (y2 - y1)
    val dx = px - projX
    val dy = py - projY
    return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun CandlestickChartCompose(
    candles: List<Candle>,
    timeframe: Timeframe,
    symbol: String,
    priceType: PriceType,
    drawings: List<Drawing>,
    activeDrawingTool: DrawingType?,
    selectedDrawingId: String?,
    alerts: List<PriceAlert> = emptyList(),
    serverTimeOffset: Long = 0L,
    onSelectDrawing: (String?) -> Unit,
    onAddDrawing: (Drawing) -> Unit,
    onUpdateDrawing: (Drawing) -> Unit,
    onDeleteDrawing: (String) -> Unit = {},
    onSwipeNextCoin: () -> Unit = {},
    onSwipePreviousCoin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var candleWidth by remember { mutableFloatStateOf(24f) }
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var priceScaleMultiplier by remember { mutableFloatStateOf(1f) }
    var pricePanOffset by remember { mutableFloatStateOf(0f) }
    var crosshairPosition by remember { mutableStateOf<Offset?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var lastPriceScaleTap by remember { mutableLongStateOf(0L) }

    // Live clock ticker for real-time candle countdown
    var currentClockTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentClockTime = System.currentTimeMillis()
        }
    }

    // Live Drawing in progress
    var liveDragStart by remember { mutableStateOf<Offset?>(null) }
    var liveDragCurrent by remember { mutableStateOf<Offset?>(null) }

    val textMeasurer = rememberTextMeasurer()
    val selectedDrawing = drawings.firstOrNull { it.id == selectedDrawingId }

    // Continuous Sub-Pixel Coordinate Conversion Helpers
    val rightMarginBars = 8f
    val lastBarIndex = if (candles.isNotEmpty()) (candles.size - 1).toFloat() else 0f

    fun calculateBounds(chartWidth: Float): Pair<Float, Float> {
        if (candles.isEmpty() || chartWidth <= 0f) return Pair(0f, 100f)
        val maxVisibleIdx = kotlin.math.ceil(lastBarIndex + rightMarginBars - scrollOffset).toInt() + 2
        val safeEnd = maxVisibleIdx.coerceIn(0, candles.size - 1)
        val minVisibleIdx = kotlin.math.floor(lastBarIndex + rightMarginBars - scrollOffset - (chartWidth / candleWidth)).toInt() - 2
        val safeSt = minVisibleIdx.coerceIn(0, safeEnd)
        val visibleCandles = if (safeSt <= safeEnd && safeEnd < candles.size) {
            candles.subList(safeSt, safeEnd + 1)
        } else emptyList()
        val minP = visibleCandles.minOfOrNull { it.low } ?: candles.lastOrNull()?.low ?: 0f
        val maxP = visibleCandles.maxOfOrNull { it.high } ?: candles.lastOrNull()?.high ?: 100f
        return Pair(minP, maxP)
    }

    BoxWithConstraints(
        modifier = modifier
            .background(TVBackground)
            .fillMaxSize()
    ) {
        val chartWidth = max(constraints.maxWidth.toFloat() - 140f, 100f)
        val chartHeight = max(constraints.maxHeight.toFloat() - 40f, 100f)

        val (rawMinPrice, rawMaxPrice) = calculateBounds(chartWidth)
        val validMultiplier = if (priceScaleMultiplier.isNaN() || priceScaleMultiplier <= 0f) 1f else priceScaleMultiplier.coerceIn(0.1f, 10f)
        val validPanOffset = if (pricePanOffset.isNaN()) 0f else pricePanOffset
        val priceRange = max(rawMaxPrice - rawMinPrice, max(rawMaxPrice * 0.001f, 1f)) * 1.1f / validMultiplier
        val midPrice = ((rawMaxPrice + rawMinPrice) / 2f) + validPanOffset
        val displayMin = midPrice - priceRange / 2f

        fun priceToY(price: Float): Float {
            if (price.isNaN() || priceRange <= 0f || priceRange.isNaN()) return chartHeight / 2f
            val y = chartHeight - ((price - displayMin) / priceRange) * chartHeight
            return if (y.isNaN() || y.isInfinite()) chartHeight / 2f else y
        }

        fun yToPrice(y: Float): Float {
            if (y.isNaN() || priceRange.isNaN()) return displayMin
            val p = displayMin + ((chartHeight - y) / chartHeight) * priceRange
            return if (p.isNaN() || p.isInfinite()) displayMin else p
        }

        fun indexToX(idx: Int): Float {
            val safeWidth = if (candleWidth.isNaN() || candleWidth <= 0f) 24f else candleWidth
            val safeScroll = if (scrollOffset.isNaN()) 0f else scrollOffset
            val barIndexFromRight = (lastBarIndex - idx.toFloat()) + rightMarginBars - safeScroll
            val x = chartWidth - barIndexFromRight * safeWidth
            return if (x.isNaN() || x.isInfinite()) chartWidth / 2f else x
        }

        fun timeToX(time: Long): Float {
            if (candles.isEmpty()) return chartWidth / 2f
            val lastCandle = candles.lastOrNull() ?: return chartWidth / 2f
            val timeDelta = time - lastCandle.time
            val duration = max(timeframe.durationMs, 1000L)
            val barsFromLast = timeDelta.toDouble() / duration.toDouble()
            val safeWidth = if (candleWidth.isNaN() || candleWidth <= 0f) 24f else candleWidth
            val safeScroll = if (scrollOffset.isNaN()) 0f else scrollOffset
            val barIndexFromRight = -barsFromLast.toFloat() + rightMarginBars - safeScroll
            val x = chartWidth - barIndexFromRight * safeWidth
            return if (x.isNaN() || x.isInfinite()) chartWidth / 2f else x
        }

        fun xToTime(x: Float): Long {
            val safeWidth = if (candleWidth.isNaN() || candleWidth <= 0f) 24f else candleWidth
            val safeScroll = if (scrollOffset.isNaN()) 0f else scrollOffset
            if (candles.isEmpty()) return System.currentTimeMillis()
            val lastCandle = candles.lastOrNull() ?: return System.currentTimeMillis()
            val barIndexFromRight = (chartWidth - x) / safeWidth
            val barsFromLast = rightMarginBars - safeScroll - barIndexFromRight
            val duration = max(timeframe.durationMs, 1000L)
            return (lastCandle.time + (barsFromLast * duration).toLong())
        }

        fun isTouchNearDrawing(offset: Offset, d: Drawing): Boolean {
            return when (d.type) {
                DrawingType.HORIZONTAL_LINE -> {
                    val p = d.points.firstOrNull() ?: return false
                    abs(offset.y - priceToY(p.price)) < 36f
                }
                DrawingType.TRENDLINE -> {
                    if (d.points.size < 2) false
                    else {
                        val x1 = timeToX(d.points[0].time)
                        val y1 = priceToY(d.points[0].price)
                        val x2 = timeToX(d.points[1].time)
                        val y2 = priceToY(d.points[1].price)
                        distanceToSegment(offset.x, offset.y, x1, y1, x2, y2) < 36f
                    }
                }
                DrawingType.RECTANGLE, DrawingType.PRICE_RANGE -> {
                    if (d.points.size < 2) false
                    else {
                        val x1 = timeToX(d.points[0].time)
                        val y1 = priceToY(d.points[0].price)
                        val x2 = timeToX(d.points[1].time)
                        val y2 = priceToY(d.points[1].price)
                        val left = min(x1, x2) - 20f
                        val right = max(x1, x2) + 20f
                        val top = min(y1, y2) - 20f
                        val bottom = max(y1, y2) + 20f
                        offset.x in left..right && offset.y in top..bottom
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeDrawingTool, selectedDrawingId, selectedDrawing?.isLocked) {
                    if (activeDrawingTool != null) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                liveDragStart = offset
                                liveDragCurrent = offset
                                if (activeDrawingTool == DrawingType.HORIZONTAL_LINE) {
                                    val targetPrice = yToPrice(offset.y)
                                    val targetTime = xToTime(offset.x)
                                    onAddDrawing(
                                        Drawing(
                                            id = UUID.randomUUID().toString(),
                                            type = DrawingType.HORIZONTAL_LINE,
                                            points = listOf(ChartPoint(targetTime, targetPrice))
                                        )
                                    )
                                    liveDragStart = null
                                    liveDragCurrent = null
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                liveDragCurrent = change.position
                            },
                            onDragEnd = {
                                if (liveDragStart != null && liveDragCurrent != null && activeDrawingTool != DrawingType.HORIZONTAL_LINE) {
                                    val p1 = ChartPoint(xToTime(liveDragStart!!.x), yToPrice(liveDragStart!!.y))
                                    val p2 = ChartPoint(xToTime(liveDragCurrent!!.x), yToPrice(liveDragCurrent!!.y))
                                    onAddDrawing(
                                        Drawing(
                                            id = UUID.randomUUID().toString(),
                                            type = activeDrawingTool,
                                            points = listOf(p1, p2)
                                        )
                                    )
                                }
                                liveDragStart = null
                                liveDragCurrent = null
                            },
                            onDragCancel = {
                                liveDragStart = null
                                liveDragCurrent = null
                            }
                        )
                    } else {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                flingJob?.cancel()

                                val startTime = System.currentTimeMillis()
                                val startPos = down.position
                                var lastPos = startPos
                                var totalMovement = 0f
                                var isPinching = false
                                var isCrosshairDragging = false
                                var previousDistance = 0f
                                var currentVelocity = 0f
                                var lastVelocityTime = startTime

                                val isMovingDrawing = selectedDrawing != null && !selectedDrawing.isLocked && isTouchNearDrawing(startPos, selectedDrawing)

                                do {
                                    val event = awaitPointerEvent()
                                    val activePointers = event.changes.filter { it.pressed }
                                    val currentTime = System.currentTimeMillis()

                                    if (activePointers.size == 1) {
                                        val change = activePointers.first()
                                        val currentPos = change.position
                                        val pan = currentPos - lastPos
                                        val dist = (currentPos - startPos).getDistance()
                                        if (dist > totalMovement) totalMovement = dist

                                        // Long-press then drag to inspect individual candles without moving the chart.
                                        if (!isMovingDrawing && startPos.x <= chartWidth &&
                                            currentTime - startTime >= 350L && totalMovement < 16f
                                        ) {
                                            isCrosshairDragging = true
                                        }
                                        if (isCrosshairDragging) {
                                            crosshairPosition = currentPos
                                            change.consume()
                                        }

                                        val dt = (currentTime - lastVelocityTime).coerceAtLeast(1L)
                                        val instVel = (pan.x / dt.toFloat()) * 1000f
                                        if (!instVel.isNaN() && !instVel.isInfinite()) {
                                            currentVelocity = (currentVelocity * 0.35f + instVel * 0.65f).coerceIn(-6000f, 6000f)
                                        }
                                        lastVelocityTime = currentTime

                                        if (isCrosshairDragging) {
                                            // Crosshair owns this gesture; do not pan the candle series.
                                        } else if (isMovingDrawing && selectedDrawing != null) {
                                            val deltaTime = xToTime(currentPos.x) - xToTime(lastPos.x)
                                            val deltaPrice = yToPrice(currentPos.y) - yToPrice(lastPos.y)
                                            val updated = selectedDrawing.points.map { pt ->
                                                ChartPoint(pt.time + deltaTime, pt.price + deltaPrice)
                                            }
                                            onUpdateDrawing(selectedDrawing.copy(points = updated))
                                            change.consume()
                                        } else if (startPos.x > chartWidth) {
                                            // Dragging the price axis scales around the exact price under the finger.
                                            if (pan.y != 0f && !pan.y.isNaN() && chartHeight > 0f) {
                                                val anchorY = currentPos.y.coerceIn(0f, chartHeight)
                                                val anchorPrice = yToPrice(anchorY)
                                                val scaleFactor = (1f - pan.y * 0.006f).coerceIn(0.8f, 1.2f)
                                                val nextMultiplier = (priceScaleMultiplier * scaleFactor).coerceIn(0.1f, 10f)
                                                val nextRange = max(rawMaxPrice - rawMinPrice, max(rawMaxPrice * 0.001f, 1f)) * 1.1f / nextMultiplier
                                                val nextDisplayMin = anchorPrice - ((chartHeight - anchorY) / chartHeight) * nextRange
                                                val nextMidPrice = nextDisplayMin + nextRange / 2f
                                                priceScaleMultiplier = nextMultiplier
                                                pricePanOffset = nextMidPrice - ((rawMaxPrice + rawMinPrice) / 2f)
                                            }
                                            change.consume()
                                        } else {
                                            if (totalMovement > 6f && !pan.x.isNaN()) {
                                                val minScroll = if (candles.size > 5) -(candles.size - 5).toFloat() else 0f
                                                val maxScroll = 50f
                                                val safeWidth = if (candleWidth.isNaN() || candleWidth <= 0f) 24f else candleWidth
                                                val deltaBars = pan.x / safeWidth
                                                if (!deltaBars.isNaN()) {
                                                    scrollOffset = (scrollOffset + deltaBars).coerceIn(minScroll, maxScroll)
                                                }
                                                if (chartHeight > 0f && !pan.y.isNaN() && !priceRange.isNaN() && !priceRange.isInfinite()) {
                                                    pricePanOffset += (pan.y / chartHeight) * priceRange
                                                }
                                                change.consume()
                                            }
                                        }
                                        lastPos = currentPos
                                    } else if (activePointers.size >= 2) {
                                        isPinching = true
                                        val p0 = activePointers[0].position
                                        val p1 = activePointers[1].position
                                        val currentDistance = (p0 - p1).getDistance()
                                        val centroid = (p0 + p1) / 2f

                                        if (previousDistance > 0f && currentDistance > 0f) {
                                            val zoomRatio = currentDistance / previousDistance
                                            if (!zoomRatio.isNaN() && !zoomRatio.isInfinite() && zoomRatio > 0f) {
                                                if (centroid.x > chartWidth) {
                                                    // Pinching directly on the price axis zooms price, not candle spacing.
                                                    val anchorY = centroid.y.coerceIn(0f, chartHeight)
                                                    val anchorPrice = yToPrice(anchorY)
                                                    val nextMultiplier = (priceScaleMultiplier * zoomRatio).coerceIn(0.1f, 10f)
                                                    val nextRange = max(rawMaxPrice - rawMinPrice, max(rawMaxPrice * 0.001f, 1f)) * 1.1f / nextMultiplier
                                                    val nextDisplayMin = anchorPrice - ((chartHeight - anchorY) / chartHeight) * nextRange
                                                    val nextMidPrice = nextDisplayMin + nextRange / 2f
                                                    priceScaleMultiplier = nextMultiplier
                                                    pricePanOffset = nextMidPrice - ((rawMaxPrice + rawMinPrice) / 2f)
                                                } else {
                                                    // Keep the candle under the pinch centroid stationary while zooming.
                                                    val oldWidth = candleWidth.coerceIn(4f, 120f)
                                                    val newWidth = (oldWidth * zoomRatio).coerceIn(4f, 120f)
                                                    if (newWidth != oldWidth) {
                                                        val anchorCorrection = (chartWidth - centroid.x) * (1f / oldWidth - 1f / newWidth)
                                                        candleWidth = newWidth
                                                        val minScroll = if (candles.size > 5) -(candles.size - 5).toFloat() else 0f
                                                        scrollOffset = (scrollOffset + anchorCorrection).coerceIn(minScroll, 50f)
                                                    }
                                                }
                                            }

                                            // Two-finger translation pans the chart without changing the zoom anchor.
                                            if (centroid.x <= chartWidth) {
                                                val pan = centroid - lastPos
                                                val minScroll = if (candles.size > 5) -(candles.size - 5).toFloat() else 0f
                                                val safeWidth = candleWidth.coerceIn(4f, 120f)
                                                if (!pan.x.isNaN()) {
                                                    val deltaBars = pan.x / safeWidth
                                                    if (!deltaBars.isNaN()) {
                                                        scrollOffset = (scrollOffset + deltaBars).coerceIn(minScroll, 50f)
                                                    }
                                                }
                                            }
                                        }
                                        previousDistance = currentDistance
                                        lastPos = centroid
                                        activePointers.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })

                                val duration = System.currentTimeMillis() - startTime

                                if (!isCrosshairDragging && duration < 300L && totalMovement < 16f && !isPinching) {
                                    if (startPos.x > chartWidth) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastPriceScaleTap < 350L) {
                                            priceScaleMultiplier = 1f
                                            pricePanOffset = 0f
                                            lastPriceScaleTap = 0L
                                        } else {
                                            lastPriceScaleTap = now
                                        }
                                    } else {
                                        crosshairPosition = startPos
                                        val clicked = drawings.reversed().firstOrNull { d -> isTouchNearDrawing(startPos, d) }
                                        onSelectDrawing(clicked?.id)
                                    }
                                } else if (!isPinching && startPos.x <= chartWidth && kotlin.math.abs(currentVelocity) > 120f) {
                                    flingJob = coroutineScope.launch {
                                        var v = if (currentVelocity.isNaN()) 0f else currentVelocity.coerceIn(-6000f, 6000f)
                                        val minScroll = if (candles.size > 5) -(candles.size - 5).toFloat() else 0f
                                        val maxScroll = 50f
                                        while (kotlin.math.abs(v) > 20f) {
                                            val safeWidth = if (candleWidth.isNaN() || candleWidth <= 0f) 24f else candleWidth
                                            val deltaBars = (v * 0.016f) / safeWidth
                                            if (deltaBars.isNaN()) break
                                            val nextScroll = (scrollOffset + deltaBars).coerceIn(minScroll, maxScroll)
                                            if (nextScroll == scrollOffset) break
                                            scrollOffset = nextScroll
                                            v *= 0.92f
                                            delay(16L)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (candles.isEmpty()) return@Canvas

                val maxVisibleIdx = kotlin.math.ceil(lastBarIndex + rightMarginBars - scrollOffset).toInt() + 2
                val safeEndIndex = maxVisibleIdx.coerceIn(0, (candles.size - 1).coerceAtLeast(0))
                val minVisibleIdx = kotlin.math.floor(lastBarIndex + rightMarginBars - scrollOffset - (chartWidth / candleWidth)).toInt() - 2
                val safeStart = minVisibleIdx.coerceIn(0, safeEndIndex)

                // 1. Grid Horizontal Lines & Price Labels
                val numSteps = 6
                for (i in 0..numSteps) {
                    val p = displayMin + (priceRange / numSteps) * i
                    val y = priceToY(p)
                    drawLine(
                        color = TVBorder,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = formatMarketPrice(p),
                        topLeft = Offset(chartWidth + 8f, y - 10f),
                        style = TextStyle(color = TVTextSecondary, fontSize = 10.sp)
                    )
                }

                // 2. Candlesticks & Drawings Clipped Within Chart Space
                clipRect(left = 0f, top = 0f, right = chartWidth, bottom = chartHeight) {
                    // Draw Candlesticks
                    for (i in safeStart..safeEndIndex) {
                        val c = candles[i]
                        val x = indexToX(i)
                        val isBull = c.close >= c.open
                        val color = if (isBull) TVBullishGreen else TVBearishRed

                        val openY = priceToY(c.open)
                        val closeY = priceToY(c.close)
                        val highY = priceToY(c.high)
                        val lowY = priceToY(c.low)

                        val bodyTop = min(openY, closeY)
                        val bodyHeight = max(abs(closeY - openY), 2f)
                        val barW = max(candleWidth * 0.7f, 2f)

                        // Wick
                        drawLine(
                            color = color,
                            start = Offset(x, highY),
                            end = Offset(x, lowY),
                            strokeWidth = 2f
                        )

                        // Body
                        drawRect(
                            color = color,
                            topLeft = Offset(x - barW / 2f, bodyTop),
                            size = Size(barW, bodyHeight)
                        )
                    }

                    // Draw Active Alerts Dotted Lines
                    val activeAlerts = alerts.filter { it.symbol == symbol && it.isEnabled && !it.isTriggered }
                    activeAlerts.forEach { alert ->
                        val alertY = priceToY(alert.targetPrice)
                        if (alertY in 0f..chartHeight) {
                            drawLine(
                                color = Color(0xFFFF9800),
                                start = Offset(0f, alertY),
                                end = Offset(chartWidth, alertY),
                                strokeWidth = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                            )
                        }
                    }

                    // Draw Saved Drawings Anchored to Real Coordinates
                    drawings.forEach { drawing ->
                        val isSelected = drawing.id == selectedDrawingId
                        val strokeW = if (isSelected) drawing.strokeWidth + 2f else drawing.strokeWidth
                        val dColor = Color(drawing.color)

                        when (drawing.type) {
                            DrawingType.HORIZONTAL_LINE -> {
                                val p = drawing.points.firstOrNull() ?: return@forEach
                                val y = priceToY(p.price)
                                drawLine(
                                    color = dColor,
                                    start = Offset(0f, y),
                                    end = Offset(chartWidth, y),
                                    strokeWidth = strokeW,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                )
                                if (isSelected) {
                                    drawCircle(color = Color.White, radius = 6f, center = Offset(chartWidth * 0.5f, y))
                                    drawCircle(color = dColor, radius = 4f, center = Offset(chartWidth * 0.5f, y))
                                }
                            }
                            DrawingType.TRENDLINE -> {
                                if (drawing.points.size >= 2) {
                                    val x1 = timeToX(drawing.points[0].time)
                                    val y1 = priceToY(drawing.points[0].price)
                                    val x2 = timeToX(drawing.points[1].time)
                                    val y2 = priceToY(drawing.points[1].price)
                                    drawLine(
                                        color = dColor,
                                        start = Offset(x1, y1),
                                        end = Offset(x2, y2),
                                        strokeWidth = strokeW
                                    )
                                    if (isSelected) {
                                        drawCircle(color = Color.White, radius = 7f, center = Offset(x1, y1))
                                        drawCircle(color = dColor, radius = 4f, center = Offset(x1, y1))
                                        drawCircle(color = Color.White, radius = 7f, center = Offset(x2, y2))
                                        drawCircle(color = dColor, radius = 4f, center = Offset(x2, y2))
                                    }
                                }
                            }
                            DrawingType.RECTANGLE -> {
                                if (drawing.points.size >= 2) {
                                    val x1 = timeToX(drawing.points[0].time)
                                    val y1 = priceToY(drawing.points[0].price)
                                    val x2 = timeToX(drawing.points[1].time)
                                    val y2 = priceToY(drawing.points[1].price)

                                    val left = min(x1, x2)
                                    val top = min(y1, y2)
                                    val w = max(abs(x2 - x1), 4f)
                                    val h = max(abs(y2 - y1), 4f)

                                    drawRect(
                                        color = dColor.copy(alpha = 0.25f),
                                        topLeft = Offset(left, top),
                                        size = Size(w, h)
                                    )
                                    drawRect(
                                        color = dColor,
                                        topLeft = Offset(left, top),
                                        size = Size(w, h),
                                        style = Stroke(width = strokeW)
                                    )
                                    if (isSelected) {
                                        drawCircle(color = Color.White, radius = 6f, center = Offset(x1, y1))
                                        drawCircle(color = dColor, radius = 4f, center = Offset(x1, y1))
                                        drawCircle(color = Color.White, radius = 6f, center = Offset(x2, y2))
                                        drawCircle(color = dColor, radius = 4f, center = Offset(x2, y2))
                                    }
                                }
                            }
                            DrawingType.PRICE_RANGE -> {
                                if (drawing.points.size >= 2) {
                                    val x1 = timeToX(drawing.points[0].time)
                                    val y1 = priceToY(drawing.points[0].price)
                                    val x2 = timeToX(drawing.points[1].time)
                                    val y2 = priceToY(drawing.points[1].price)

                                    val left = min(x1, x2)
                                    val top = min(y1, y2)
                                    val w = max(abs(x2 - x1), 30f)
                                    val h = max(abs(y2 - y1), 4f)

                                    val diff = drawing.points[1].price - drawing.points[0].price
                                    val isGain = diff >= 0f
                                    val rangeColor = if (isGain) TVBullishGreen else TVBearishRed

                                    drawRect(
                                        color = rangeColor.copy(alpha = 0.25f),
                                        topLeft = Offset(left, top),
                                        size = Size(w, h)
                                    )
                                    drawRect(
                                        color = rangeColor,
                                        topLeft = Offset(left, top),
                                        size = Size(w, h),
                                        style = Stroke(width = strokeW)
                                    )

                                    val pct = if (drawing.points[0].price != 0f) (diff / drawing.points[0].price) * 100f else 0f
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = String.format(Locale.US, "%s%.2f%%", if (isGain) "+" else "", pct),
                                        topLeft = Offset(left + 6f, top + 4f),
                                        style = TextStyle(color = rangeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    )

                                    if (isSelected) {
                                        drawCircle(color = Color.White, radius = 6f, center = Offset(x1, y1))
                                        drawCircle(color = rangeColor, radius = 4f, center = Offset(x1, y1))
                                        drawCircle(color = Color.White, radius = 6f, center = Offset(x2, y2))
                                        drawCircle(color = rangeColor, radius = 4f, center = Offset(x2, y2))
                                    }
                                }
                            }
                        }
                    }

                    // Live In-Progress Drawing Preview
                    if (liveDragStart != null && liveDragCurrent != null) {
                        val start = liveDragStart!!
                        val curr = liveDragCurrent!!

                        when (activeDrawingTool) {
                            DrawingType.TRENDLINE -> {
                                drawLine(
                                    color = TVPrimaryBlue,
                                    start = start,
                                    end = curr,
                                    strokeWidth = 3f
                                )
                            }
                            DrawingType.RECTANGLE -> {
                                val left = min(start.x, curr.x)
                                val top = min(start.y, curr.y)
                                val w = max(abs(curr.x - start.x), 4f)
                                val h = max(abs(curr.y - start.y), 4f)
                                drawRect(
                                    color = TVPrimaryBlue.copy(alpha = 0.25f),
                                    topLeft = Offset(left, top),
                                    size = Size(w, h)
                                )
                                drawRect(
                                    color = TVPrimaryBlue,
                                    topLeft = Offset(left, top),
                                    size = Size(w, h),
                                    style = Stroke(width = 2f)
                                )
                            }
                            DrawingType.PRICE_RANGE -> {
                                val left = min(start.x, curr.x)
                                val top = min(start.y, curr.y)
                                val w = max(abs(curr.x - start.x), 30f)
                                val h = max(abs(curr.y - start.y), 4f)
                                val isGain = curr.y <= start.y
                                val cColor = if (isGain) TVBullishGreen else TVBearishRed
                                drawRect(
                                    color = cColor.copy(alpha = 0.3f),
                                    topLeft = Offset(left, top),
                                    size = Size(w, h)
                                )
                                drawRect(
                                    color = cColor,
                                    topLeft = Offset(left, top),
                                    size = Size(w, h),
                                    style = Stroke(width = 2f)
                                )
                            }
                            else -> Unit
                        }
                    }
                }

                // 3. Right-Side Price Scale Axis
                val activeAlerts = alerts.filter { it.symbol == symbol && it.isEnabled && !it.isTriggered }
                activeAlerts.forEach { alert ->
                    val alertY = priceToY(alert.targetPrice)
                    if (alertY in 0f..chartHeight) {
                        val alertColor = Color(0xFFFF9800)
                        drawRect(
                            color = alertColor,
                            topLeft = Offset(chartWidth, alertY - 11f),
                            size = Size(140f, 22f)
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = formatMarketPrice(alert.targetPrice),
                            topLeft = Offset(chartWidth + 10f, alertY - 7f),
                            style = TextStyle(color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Live Price Tag on Right Axis
                val last = candles.lastOrNull()
                if (last != null) {
                    val lastY = priceToY(last.close)
                    val currentBull = last.close >= last.open
                    val currentTagColor = if (currentBull) TVBullishGreen else TVBearishRed

                    drawLine(
                        color = currentTagColor,
                        start = Offset(0f, lastY),
                        end = Offset(chartWidth, lastY),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )

                    drawRect(
                        color = currentTagColor,
                        topLeft = Offset(chartWidth, lastY - 14f),
                        size = Size(140f, 26f)
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = formatMarketPrice(last.close),
                        topLeft = Offset(chartWidth + 10f, lastY - 8f),
                        style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )

                    // Real-Time Bar Countdown Timer Under Price Tag
                    val candleEndTime = last.time + timeframe.durationMs
                    val effectiveTime = currentClockTime + serverTimeOffset
                    val remainingSec = max(0L, (candleEndTime - effectiveTime) / 1000L)
                    val countdownStr = if (remainingSec >= 3600L) {
                        val h = remainingSec / 3600
                        val m = (remainingSec % 3600) / 60
                        val s = remainingSec % 60
                        String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
                    } else {
                        val m = remainingSec / 60
                        val s = remainingSec % 60
                        String.format(Locale.US, "%02d:%02d", m, s)
                    }

                    drawRect(
                        color = TVSurface.copy(alpha = 0.95f),
                        topLeft = Offset(chartWidth, lastY + 13f),
                        size = Size(140f, 20f)
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = countdownStr,
                        topLeft = Offset(chartWidth + 10f, lastY + 16f),
                        style = TextStyle(color = TVTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    )
                }

                // Crosshair
                crosshairPosition?.let { pos ->
                    if (pos.x in 0f..chartWidth && pos.y in 0f..chartHeight) {
                        drawLine(
                            color = Color.Gray,
                            start = Offset(pos.x, 0f),
                            end = Offset(pos.x, chartHeight),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                        drawLine(
                            color = Color.Gray,
                            start = Offset(0f, pos.y),
                            end = Offset(chartWidth, pos.y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                    }
                }
            }

            // Top-Right Auto-Scale / Reset Button
            Surface(
                onClick = {
                    priceScaleMultiplier = 1f
                    pricePanOffset = 0f
                },
                shape = RoundedCornerShape(4.dp),
                color = TVCard.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, TVBorder),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
            ) {
                Text(
                    text = if (priceScaleMultiplier != 1f || pricePanOffset != 0f) "AUTO ●" else "AUTO",
                    color = if (priceScaleMultiplier != 1f || pricePanOffset != 0f) TVPrimaryBlue else TVTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Top HUD Header: show the candle under the crosshair, otherwise the live candle.
            val crosshairCandle = crosshairPosition
                ?.takeIf { it.x in 0f..chartWidth && it.y in 0f..chartHeight && candles.isNotEmpty() }
                ?.let { pos ->
                    val index = (lastBarIndex + rightMarginBars - scrollOffset -
                        ((chartWidth - pos.x) / candleWidth.coerceAtLeast(1f))).roundToInt()
                    candles.getOrNull(index.coerceIn(0, candles.lastIndex))
                }
            candles.lastOrNull()?.let { liveCandle ->
                val displayCandle = crosshairCandle ?: liveCandle
                val candleEndTime = liveCandle.time + timeframe.durationMs
                val effectiveTime = currentClockTime + serverTimeOffset
                val remainingSec = max(0L, (candleEndTime - effectiveTime) / 1000L)
                val countdownStr = if (remainingSec >= 3600L) {
                    val h = remainingSec / 3600
                    val m = (remainingSec % 3600) / 60
                    val s = remainingSec % 60
                    String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
                } else {
                    val m = remainingSec / 60
                    val s = remainingSec % 60
                    String.format(Locale.US, "%02d:%02d", m, s)
                }

                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("$symbol · ${timeframe.displayName}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("O: ${formatMarketPrice(displayCandle.open)}", color = TVTextSecondary, fontSize = 10.sp)
                    Text("H: ${formatMarketPrice(displayCandle.high)}", color = TVTextSecondary, fontSize = 10.sp)
                    Text("L: ${formatMarketPrice(displayCandle.low)}", color = TVTextSecondary, fontSize = 10.sp)
                    Text("C: ${formatMarketPrice(displayCandle.close)}", color = if (displayCandle.close >= displayCandle.open) TVBullishGreen else TVBearishRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = TVCard.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "⏱ $countdownStr",
                            color = TVPrimaryBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // FLOATING CONTEXTUAL TOOLBAR FOR SELECTED DRAWING (Lock, Color, Delete, Close)
            selectedDrawing?.let { d ->
                Surface(
                    color = TVSurface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TVBorder),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Lock / Unlock Toggle
                        IconButton(
                            onClick = {
                                onUpdateDrawing(d.copy(isLocked = !d.isLocked))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (d.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (d.isLocked) "Unlock" else "Lock",
                                tint = if (d.isLocked) Color(0xFFFF9800) else TVTextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Color Cycle
                        IconButton(
                            onClick = {
                                val palette = listOf(0xFF2962FF, 0xFF089981, 0xFFF23645, 0xFFFF9800, 0xFF9C27B0, 0xFFFFFFFF)
                                val nextIdx = (palette.indexOf(d.color) + 1) % palette.size
                                onUpdateDrawing(d.copy(color = palette[nextIdx]))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color(d.color), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                            )
                        }

                        // Large, clearly labelled delete action for the selected drawing.
                        Surface(
                            onClick = {
                                onDeleteDrawing(d.id)
                                onSelectDrawing(null)
                            },
                            color = TVBearishRed.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TVBearishRed.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Drawing", tint = TVBearishRed, modifier = Modifier.size(16.dp))
                                Text("Delete", color = TVBearishRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Close Toolbar / Deselect
                        IconButton(
                            onClick = { onSelectDrawing(null) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Deselect",
                                tint = TVTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Bottom TradingView Swipe Navigation Strip
            var totalDragX by remember { mutableFloatStateOf(0f) }
            Surface(
                color = TVSurface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .pointerInput(activeDrawingTool) {
                        if (activeDrawingTool == null) {
                            detectDragGestures(
                                onDragStart = { totalDragX = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragX += dragAmount.x
                                },
                                onDragEnd = {
                                    if (totalDragX < -60f) {
                                        onSwipeNextCoin()
                                    } else if (totalDragX > 60f) {
                                        onSwipePreviousCoin()
                                    }
                                    totalDragX = 0f
                                }
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSwipePreviousCoin,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Previous Coin", tint = TVTextSecondary)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, tint = TVPrimaryBlue, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Swipe to switch coin · $symbol",
                            color = TVTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(
                        onClick = onSwipeNextCoin,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next Coin", tint = TVTextSecondary)
                    }
                }
            }
        }
    }
}
