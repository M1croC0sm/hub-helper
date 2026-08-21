package app.hubhelper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate

enum class HubTheme(val displayName: String) {
    INDUSTRIAL("Industrial Instrument"),
    CLEAR_EASY("Clear & Easy"),
    SOFT_FRIENDLY("Soft & Friendly"),
}

enum class ThemeMode(val displayName: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
}

@Immutable
data class HubThemeTokens(
    val theme: HubTheme,
    val panelShape: CornerBasedShape,
    val badgeShape: CornerBasedShape,
    val panelBorderWidth: Dp,
    val panelPadding: Dp,
    val screenPadding: Dp,
    val contentSpacing: Dp,
    val uppercaseLabels: Boolean,
    val compact: Boolean,
    val good: Color,
    val attention: Color,
    val pto: Color,
    val sick: Color,
    val document: Color,
    val metricLarge: TextStyle,
    val metricMedium: TextStyle,
)

private val LocalHubTokens = staticCompositionLocalOf<HubThemeTokens> {
    error("Hub theme tokens are not available")
}

object HubThemeDesign {
    val tokens: HubThemeTokens
        @Composable @ReadOnlyComposable get() = LocalHubTokens.current
}

private val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_regular, FontWeight.Normal),
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

private val Roboto = FontFamily.SansSerif
private val RobotoMono = FontFamily(Font(R.font.roboto_mono_medium, FontWeight.Medium))
private val NunitoSans = FontFamily(
    Font(R.font.nunito_sans_regular, FontWeight.Normal),
    Font(R.font.nunito_sans_medium, FontWeight.Medium),
    Font(R.font.nunito_sans_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_sans_bold, FontWeight.Bold),
)

private val IndustrialDark = darkColorScheme(
    primary = Color(0xFFFF9F0A),
    onPrimary = Color(0xFF171A1C),
    secondary = Color(0xFF3DB8AA),
    tertiary = Color(0xFF4E9BD8),
    background = Color(0xFF101719),
    onBackground = Color(0xFFE8E5DE),
    surface = Color(0xFF161E20),
    onSurface = Color(0xFFE8E5DE),
    surfaceVariant = Color(0xFF20292C),
    onSurfaceVariant = Color(0xFFAFB5B5),
    outline = Color(0xFF4A5558),
    error = Color(0xFFE25B52),
)

private val IndustrialLight = lightColorScheme(
    primary = Color(0xFFC66F00),
    onPrimary = Color.White,
    secondary = Color(0xFF087E73),
    tertiary = Color(0xFF236E9E),
    background = Color(0xFFF0EFEB),
    onBackground = Color(0xFF202426),
    surface = Color(0xFFF8F7F3),
    onSurface = Color(0xFF202426),
    surfaceVariant = Color(0xFFE7E6E1),
    onSurfaceVariant = Color(0xFF5A6265),
    outline = Color(0xFF8A9091),
    error = Color(0xFFB3261E),
)

private val ClearDark = darkColorScheme(
    primary = Color(0xFF8CB8F4),
    onPrimary = Color(0xFF082A53),
    secondary = Color(0xFF71D58A),
    tertiary = Color(0xFFFFC15A),
    background = Color(0xFF101820),
    onBackground = Color(0xFFF4F7FA),
    surface = Color(0xFF18242E),
    onSurface = Color(0xFFF4F7FA),
    surfaceVariant = Color(0xFF22313C),
    onSurfaceVariant = Color(0xFFC6D0D8),
    outline = Color(0xFF50616D),
    error = Color(0xFFFF6B5F),
)

private val ClearLight = lightColorScheme(
    primary = Color(0xFF315F9F),
    onPrimary = Color.White,
    secondary = Color(0xFF27804A),
    tertiary = Color(0xFFB36900),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF111A35),
    surface = Color.White,
    onSurface = Color(0xFF111A35),
    surfaceVariant = Color(0xFFF0F4F7),
    onSurfaceVariant = Color(0xFF4B5868),
    outline = Color(0xFFC7D0D8),
    error = Color(0xFFC73B31),
)

private val SoftDark = darkColorScheme(
    primary = Color(0xFFF18BB8),
    onPrimary = Color(0xFF4B1730),
    secondary = Color(0xFF67C9CE),
    tertiary = Color(0xFFB8A6EA),
    background = Color(0xFF1E1B36),
    onBackground = Color(0xFFFFF7FB),
    surface = Color(0xFF292443),
    onSurface = Color(0xFFFFF7FB),
    surfaceVariant = Color(0xFF373052),
    onSurfaceVariant = Color(0xFFD8CCE0),
    outline = Color(0xFF5D5375),
    error = Color(0xFFFF766E),
)

private val SoftLight = lightColorScheme(
    primary = Color(0xFFC94F87),
    onPrimary = Color.White,
    secondary = Color(0xFF328F96),
    tertiary = Color(0xFF7663B5),
    background = Color(0xFFFFF9FC),
    onBackground = Color(0xFF292342),
    surface = Color.White,
    onSurface = Color(0xFF292342),
    surfaceVariant = Color(0xFFF8EDF4),
    onSurfaceVariant = Color(0xFF655B70),
    outline = Color(0xFFE6CEDC),
    error = Color(0xFFB73745),
)

private val IndustrialTypography = Typography(
    headlineLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp),
    headlineMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.8.sp),
    labelMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.6.sp),
    bodyLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
)

private val ClearTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp),
    headlineMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp),
    titleLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
)

private val SoftTypography = Typography(
    headlineLarge = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp),
    headlineMedium = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.Bold, fontSize = 31.sp, lineHeight = 37.sp),
    headlineSmall = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
)

private fun tokens(theme: HubTheme): HubThemeTokens = when (theme) {
    HubTheme.INDUSTRIAL -> HubThemeTokens(
        theme = theme,
        panelShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
        badgeShape = CutCornerShape(4.dp),
        panelBorderWidth = 1.dp,
        panelPadding = 14.dp,
        screenPadding = 14.dp,
        contentSpacing = 9.dp,
        uppercaseLabels = true,
        compact = true,
        good = Color(0xFF66C66A),
        attention = Color(0xFFFF9F0A),
        pto = Color(0xFF4E9BD8),
        sick = Color(0xFF3DB8AA),
        document = Color(0xFFA274C9),
        metricLarge = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 46.sp, lineHeight = 50.sp),
        metricMedium = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 29.sp, lineHeight = 33.sp),
    )
    HubTheme.CLEAR_EASY -> HubThemeTokens(
        theme = theme,
        panelShape = RoundedCornerShape(12.dp),
        badgeShape = RoundedCornerShape(8.dp),
        panelBorderWidth = 1.dp,
        panelPadding = 16.dp,
        screenPadding = 16.dp,
        contentSpacing = 14.dp,
        uppercaseLabels = false,
        compact = false,
        good = Color(0xFF3FA45B),
        attention = Color(0xFFE6A62C),
        pto = Color(0xFF3D70B7),
        sick = Color(0xFF3C9B72),
        document = Color(0xFF8662B5),
        metricLarge = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 44.sp, lineHeight = 50.sp),
        metricMedium = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp),
    )
    HubTheme.SOFT_FRIENDLY -> HubThemeTokens(
        theme = theme,
        panelShape = RoundedCornerShape(17.dp),
        badgeShape = RoundedCornerShape(50),
        panelBorderWidth = 1.dp,
        panelPadding = 16.dp,
        screenPadding = 16.dp,
        contentSpacing = 13.dp,
        uppercaseLabels = false,
        compact = false,
        good = Color(0xFF48A66C),
        attention = Color(0xFFE3A743),
        pto = Color(0xFF4FAAB2),
        sick = Color(0xFF4FAAB2),
        document = Color(0xFF9D82D1),
        metricLarge = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 43.sp, lineHeight = 49.sp),
        metricMedium = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp),
    )
}

@Composable
fun HubHelperTheme(theme: HubTheme, darkMode: Boolean, content: @Composable () -> Unit) {
    val colorScheme = when (theme) {
        HubTheme.INDUSTRIAL -> if (darkMode) IndustrialDark else IndustrialLight
        HubTheme.CLEAR_EASY -> if (darkMode) ClearDark else ClearLight
        HubTheme.SOFT_FRIENDLY -> if (darkMode) SoftDark else SoftLight
    }
    val typography = when (theme) {
        HubTheme.INDUSTRIAL -> IndustrialTypography
        HubTheme.CLEAR_EASY -> ClearTypography
        HubTheme.SOFT_FRIENDLY -> SoftTypography
    }
    val shape = tokens(theme).panelShape
    androidx.compose.runtime.CompositionLocalProvider(LocalHubTokens provides tokens(theme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes(small = shape, medium = shape, large = shape),
            content = content,
        )
    }
}

@Composable
fun HubPanel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    decorative: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val design = HubThemeDesign.tokens
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = design.panelShape,
        border = BorderStroke(design.panelBorderWidth, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
        shadowElevation = if (design.theme == HubTheme.INDUSTRIAL) 0.dp else 1.dp,
    ) {
        Box {
            if (decorative && design.theme == HubTheme.SOFT_FRIENDLY) {
                SoftBotanicalAccent(Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            Row(Modifier.height(IntrinsicSize.Min)) {
                if (accent != null) {
                    Box(Modifier.background(accent).fillMaxHeight().width(if (design.compact) 3.dp else 4.dp))
                }
                Column(Modifier.padding(design.panelPadding).weight(1f), content = content)
            }
        }
    }
}

@Composable
private fun SoftBotanicalAccent(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
    Canvas(modifier.size(72.dp).rotate(-12f)) {
        drawLine(color, start = center.copy(y = size.height), end = center.copy(y = 4f), strokeWidth = 2.dp.toPx())
        drawOval(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.2f), size = androidx.compose.ui.geometry.Size(size.width * 0.38f, size.height * 0.2f))
        drawOval(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.43f), size = androidx.compose.ui.geometry.Size(size.width * 0.38f, size.height * 0.2f))
        drawOval(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.66f), size = androidx.compose.ui.geometry.Size(size.width * 0.34f, size.height * 0.18f))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val value = if (HubThemeDesign.tokens.uppercaseLabels) text.uppercase() else text
    Text(value, modifier = modifier, style = MaterialTheme.typography.labelLarge, color = color)
}

@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    val design = HubThemeDesign.tokens
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.58f)),
        shape = design.badgeShape,
    ) {
        Text(
            if (design.uppercaseLabels) text.uppercase() else text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
fun MetricValue(value: String, unit: String? = null, color: Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = HubThemeDesign.tokens.metricMedium, color = color)
        unit?.let {
            Text("  $it", style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
fun ProvenanceBadge(text: String, modifier: Modifier = Modifier) {
    StatusBadge(text, MaterialTheme.colorScheme.onSurfaceVariant, modifier)
}
