package com.tradingview.binancefutures.presentation.ui.drawing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tradingview.binancefutures.domain.model.DrawingType
import com.tradingview.binancefutures.presentation.ui.theme.*

@Composable
fun DrawingToolbarCompose(
    activeDrawingTool: DrawingType?,
    onSelectTool: (DrawingType?) -> Unit,
    onClearAll: () -> Unit,
    drawingsCount: Int,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = isExpanded || activeDrawingTool != null,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it })
        ) {
            Surface(
                color = TVSurface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TVBorder),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(44.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Cursor / Deselect
                    IconButton(
                        onClick = { onSelectTool(null) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Cursor",
                            tint = if (activeDrawingTool == null) TVPrimaryBlue else TVTextSecondary
                        )
                    }

                    Divider(color = TVBorder, thickness = 1.dp, modifier = Modifier.padding(horizontal = 6.dp))

                    // Trendline
                    IconButton(
                        onClick = {
                            onSelectTool(if (activeDrawingTool == DrawingType.TRENDLINE) null else DrawingType.TRENDLINE)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Trendline",
                            tint = if (activeDrawingTool == DrawingType.TRENDLINE) TVPrimaryBlue else TVTextSecondary
                        )
                    }

                    // Horizontal Line
                    IconButton(
                        onClick = {
                            onSelectTool(if (activeDrawingTool == DrawingType.HORIZONTAL_LINE) null else DrawingType.HORIZONTAL_LINE)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HorizontalRule,
                            contentDescription = "Horizontal Line",
                            tint = if (activeDrawingTool == DrawingType.HORIZONTAL_LINE) TVPrimaryBlue else TVTextSecondary
                        )
                    }

                    // Rectangle
                    IconButton(
                        onClick = {
                            onSelectTool(if (activeDrawingTool == DrawingType.RECTANGLE) null else DrawingType.RECTANGLE)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropSquare,
                            contentDescription = "Rectangle",
                            tint = if (activeDrawingTool == DrawingType.RECTANGLE) TVPrimaryBlue else TVTextSecondary
                        )
                    }

                    // Price Range %
                    IconButton(
                        onClick = {
                            onSelectTool(if (activeDrawingTool == DrawingType.PRICE_RANGE) null else DrawingType.PRICE_RANGE)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Percent,
                            contentDescription = "Price Range",
                            tint = if (activeDrawingTool == DrawingType.PRICE_RANGE) TVPrimaryBlue else TVTextSecondary
                        )
                    }

                    if (drawingsCount > 0) {
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = onClearAll,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear All Drawings",
                                tint = TVBearishRed
                            )
                        }
                    }
                }
            }
        }

        // Toggle Handle Tab
        Surface(
            onClick = { isExpanded = !isExpanded },
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            color = TVSurface.copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, TVBorder),
            modifier = Modifier
                .width(18.dp)
                .height(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                    contentDescription = "Toggle drawing panel",
                    tint = TVTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
