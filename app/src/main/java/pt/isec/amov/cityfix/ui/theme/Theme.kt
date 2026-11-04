package pt.isec.amov.cityfix.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

@Composable
fun CityFixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = MunicipalBlue,
            secondary = TealAccent,
            error = RedUrgent,
            background = BackgroundLight,
            surface = SurfaceLight,
            surfaceVariant = Color(0xFFDCE5F0),
            primaryContainer = Color(0xFFD4E3F7),
            secondaryContainer = Color(0xFFD3ECE8)
        ),
        shapes = Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp)
        ),
        typography = Typography().copy(
            headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
}
