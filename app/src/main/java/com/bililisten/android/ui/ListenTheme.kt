package com.bililisten.android.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.graphics.Color
import com.bililisten.android.data.AppSkin

internal val ListenColorScheme = darkColorScheme(
    primary = Color(0xFF82DDC3),
    onPrimary = Color(0xFF082C24),
    primaryContainer = Color(0xFF263F3D),
    onPrimaryContainer = Color(0xFFB9F5E2),
    secondary = Color(0xFFB3C5D1),
    onSecondary = Color(0xFF16252F),
    secondaryContainer = Color(0xFF30474B),
    onSecondaryContainer = Color(0xFFEAF0F2),
    tertiary = Color(0xFF82DDC3),
    background = Color(0xFF0E1419),
    onBackground = Color(0xFFEAF0F2),
    surface = Color(0xFF171E24),
    onSurface = Color(0xFFEAF0F2),
    surfaceVariant = Color(0xFF222D34),
    onSurfaceVariant = Color(0xFF9CAAAF),
    surfaceDim = Color(0xFF0B1014),
    surfaceBright = Color(0xFF14242A),
    surfaceContainerLowest = Color(0xFF0E1419),
    surfaceContainerLow = Color(0xFF1B272E),
    surfaceContainer = Color(0xFF222D34),
    surfaceContainerHigh = Color(0xFF28363D),
    surfaceContainerHighest = Color(0xFF30474B),
    surfaceTint = Color(0xFF82DDC3),
    outline = Color(0xFF45545D),
    outlineVariant = Color(0xFF35444D),
)

private val MidnightBlueColorScheme = darkColorScheme(
    primary = Color(0xFF80B7FF),
    onPrimary = Color(0xFF071E3B),
    primaryContainer = Color(0xFF1D3554),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFFB6C8E2),
    onSecondary = Color(0xFF13243A),
    secondaryContainer = Color(0xFF283F5D),
    onSecondaryContainer = Color(0xFFEAF1FC),
    tertiary = Color(0xFF80B7FF),
    background = Color(0xFF080D16),
    onBackground = Color(0xFFEAF1FC),
    surface = Color(0xFF111B2A),
    onSurface = Color(0xFFEAF1FC),
    surfaceVariant = Color(0xFF1C2B40),
    onSurfaceVariant = Color(0xFF9AAEC9),
    surfaceDim = Color(0xFF060A11),
    surfaceBright = Color(0xFF14253E),
    surfaceContainerLowest = Color(0xFF080D16),
    surfaceContainerLow = Color(0xFF142136),
    surfaceContainer = Color(0xFF1C2B40),
    surfaceContainerHigh = Color(0xFF23354D),
    surfaceContainerHighest = Color(0xFF283F5D),
    surfaceTint = Color(0xFF80B7FF),
    outline = Color(0xFF465F7D),
    outlineVariant = Color(0xFF304660),
)

internal fun listenColorScheme(skin: AppSkin): ColorScheme = when (skin) {
    AppSkin.MINT_NIGHT -> ListenColorScheme
    AppSkin.MIDNIGHT_BLUE -> MidnightBlueColorScheme
}

/** Keep selected colors, while removing the light press overlay from both custom and Material controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListenTheme(skin: AppSkin, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = listenColorScheme(skin)) {
        CompositionLocalProvider(LocalIndication provides QuietIndication, LocalRippleConfiguration provides null,
            content = content)
    }
}

private object QuietIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() = drawContent()
    }
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0
}
