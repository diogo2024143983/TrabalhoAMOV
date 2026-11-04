package pt.isec.amov.cityfix.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun CityFixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = MunicipalBlue,
            secondary = TealAccent,
            error = RedUrgent
        ),
        content = content
    )
}
