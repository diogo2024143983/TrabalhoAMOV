package pt.isec.amov.cityfix.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CityFixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RegalGold,
            onPrimary = PureBlack,
            primaryContainer = GoldContainer,
            onPrimaryContainer = GoldOnContainer,
            secondary = Platinum,
            onSecondary = PureBlack,
            secondaryContainer = DeepNavyLight,
            onSecondaryContainer = PureWhite,
            error = RedUrgent,
            errorContainer = Color(0xFF7F1D1D),
            onErrorContainer = Color(0xFFFEE2E2),
            background = PureBlack,
            onBackground = PureWhite,
            surface = DeepNavy,
            onSurface = PureWhite,
            surfaceVariant = DeepNavyLight,
            onSurfaceVariant = MutedSilver,
            outline = NavyOutline,
            outlineVariant = NavyOutline
        ),
        shapes = Shapes(
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(10.dp),
            large = RoundedCornerShape(12.dp)
        ),
        typography = Typography().copy(
            headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
}