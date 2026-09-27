package app.hubhelper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity

@Composable
internal fun HubNavigationBar(
    selectedArea: MainArea,
    onSelect: (MainArea) -> Unit,
    onLog: () -> Unit,
) {
    val design = HubThemeDesign.tokens
    val areas = listOf(MainArea.HOME, MainArea.CALENDAR, MainArea.REFERENCE, MainArea.DOCUMENTS)
    NavigationBar {
        areas.take(2).forEach { area ->
            HubNavigationItem(area, selectedArea == area) { onSelect(area) }
        }
        NavigationBarItem(
            selected = false,
            onClick = onLog,
            icon = {
                Surface(
                    modifier = Modifier.size(46.dp).semantics { contentDescription = "Log an event" },
                    shape = if (design.theme == HubTheme.INDUSTRIAL) androidx.compose.foundation.shape.CutCornerShape(10.dp) else androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            label = { Text("LOG") },
        )
        areas.drop(2).forEach { area ->
            HubNavigationItem(area, selectedArea == area) { onSelect(area) }
        }
    }
}

@Composable
internal fun RowScope.HubNavigationItem(area: MainArea, selected: Boolean, onClick: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            HubNavIcon(
                area = area,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = area.label },
            )
        },
        label = {
            Text(
                when {
                    !largeFont -> area.shortLabel
                    area == MainArea.REFERENCE -> "Ref"
                    area == MainArea.DOCUMENTS -> "Docs"
                    else -> area.shortLabel
                },
            )
        },
    )
}

@Composable
internal fun HubNavIcon(area: MainArea, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        when (area) {
            MainArea.HOME -> {
                val roof = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.48f)
                    lineTo(size.width * 0.5f, size.height * 0.16f)
                    lineTo(size.width * 0.88f, size.height * 0.48f)
                }
                drawPath(roof, color, style = stroke)
                drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.44f), size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.42f), style = stroke)
            }
            MainArea.CALENDAR, MainArea.ATTENDANCE_DETAILS -> {
                drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.2f), size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.68f), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.38f), androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.38f), strokeWidth = 1.8.dp.toPx())
                listOf(0.33f, 0.5f, 0.67f).forEach { x ->
                    listOf(0.52f, 0.68f).forEach { y ->
                        drawCircle(color, radius = 1.3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y))
                    }
                }
            }
            MainArea.REFERENCE -> {
                val book = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.22f)
                    quadraticTo(size.width * 0.3f, size.height * 0.12f, size.width * 0.5f, size.height * 0.3f)
                    quadraticTo(size.width * 0.7f, size.height * 0.12f, size.width * 0.92f, size.height * 0.22f)
                    lineTo(size.width * 0.92f, size.height * 0.82f)
                    quadraticTo(size.width * 0.7f, size.height * 0.72f, size.width * 0.5f, size.height * 0.88f)
                    quadraticTo(size.width * 0.3f, size.height * 0.72f, size.width * 0.08f, size.height * 0.82f)
                    close()
                }
                drawPath(book, color, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.3f), androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.88f), strokeWidth = 1.8.dp.toPx())
            }
            MainArea.DOCUMENTS -> {
                val folder = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.28f)
                    lineTo(size.width * 0.4f, size.height * 0.28f)
                    lineTo(size.width * 0.5f, size.height * 0.4f)
                    lineTo(size.width * 0.92f, size.height * 0.4f)
                    lineTo(size.width * 0.84f, size.height * 0.82f)
                    lineTo(size.width * 0.08f, size.height * 0.82f)
                    close()
                }
                drawPath(folder, color, style = stroke)
            }
            else -> drawCircle(color, radius = 3.dp.toPx())
        }
    }
}
