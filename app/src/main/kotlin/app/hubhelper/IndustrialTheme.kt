package app.hubhelper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

object HubColors {
    val Charcoal = Color(0xFF111416)
    val Graphite = Color(0xFF1A1E20)
    val Steel = Color(0xFF252B2E)
    val Border = Color(0xFF4A5154)
    val WarmWhite = Color(0xFFE8E4DB)
    val Muted = Color(0xFFA8ADB0)
    val Amber = Color(0xFFF4A11A)
    val Green = Color(0xFF70C66A)
    val Blue = Color(0xFF4EA4DB)
    val Teal = Color(0xFF3DB8AA)
    val Danger = Color(0xFFE25B52)
}

private val IndustrialDarkColors = darkColorScheme(
    primary = HubColors.Amber,
    onPrimary = HubColors.Charcoal,
    secondary = HubColors.Teal,
    tertiary = HubColors.Blue,
    background = HubColors.Charcoal,
    onBackground = HubColors.WarmWhite,
    surface = HubColors.Graphite,
    onSurface = HubColors.WarmWhite,
    surfaceVariant = HubColors.Steel,
    onSurfaceVariant = HubColors.Muted,
    outline = HubColors.Border,
    error = HubColors.Danger,
)

private val IndustrialLightColors = lightColorScheme(
    primary = Color(0xFF9B5B00),
    onPrimary = Color.White,
    secondary = Color(0xFF006B62),
    tertiary = Color(0xFF176A99),
    background = Color(0xFFF2F0EA),
    onBackground = Color(0xFF1D2022),
    surface = Color(0xFFF9F7F1),
    onSurface = Color(0xFF1D2022),
    surfaceVariant = Color(0xFFE2E0D9),
    onSurfaceVariant = Color(0xFF555A5D),
    outline = Color(0xFF747A7D),
    error = Color(0xFFB3261E),
)

private val IndustrialDisplayFont = FontFamily(
    Font(R.font.doto_black, weight = FontWeight.Black),
)

private val IndustrialTypography = Typography(
    headlineLarge = TextStyle(fontFamily = IndustrialDisplayFont, fontWeight = FontWeight.Black, fontSize = 48.sp),
    headlineMedium = TextStyle(fontFamily = IndustrialDisplayFont, fontWeight = FontWeight.Black, fontSize = 30.sp),
    headlineSmall = TextStyle(fontFamily = IndustrialDisplayFont, fontWeight = FontWeight.Black, fontSize = 23.sp),
    titleLarge = TextStyle(fontFamily = IndustrialDisplayFont, fontWeight = FontWeight.Black, fontSize = 21.sp),
    titleMedium = TextStyle(fontFamily = IndustrialDisplayFont, fontWeight = FontWeight.Black, fontSize = 17.sp),
    labelLarge = TextStyle(fontFamily = IndustrialDisplayFont, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontFamily = IndustrialDisplayFont, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.4.sp),
)

@Composable
fun HubHelperTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkMode) IndustrialDarkColors else IndustrialLightColors,
        typography = IndustrialTypography,
        content = content,
    )
}

@Composable
fun IndustrialPanel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (accent != null) {
                Box(
                    Modifier
                        .background(accent)
                        .fillMaxHeight()
                        .width(3.dp),
                )
            }
            Column(Modifier.padding(16.dp).weight(1f), content = content)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelLarge, color = color)
}

@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f)),
        shape = CutCornerShape(5.dp),
    ) {
        Text(
            text.uppercase(),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
fun MetricValue(value: String, unit: String? = null, color: Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        unit?.let { Text("  $it", style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(bottom = 4.dp)) }
    }
}

@Composable
fun ProvenanceBadge(text: String, modifier: Modifier = Modifier) {
    StatusBadge(text, MaterialTheme.colorScheme.onSurfaceVariant, modifier)
}
