package com.bililisten.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Use the current window in dp, so display scaling and split-screen also get a usable layout. */
@Composable
internal fun isLandscapeWindow(): Boolean = with(LocalConfiguration.current) {
    screenWidthDp >= 600 && screenWidthDp > screenHeightDp
}

/** A horizontal phone keeps the list beside its options instead of underneath a tall header. */
@Composable
internal fun AdaptiveSheetBody(
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(max = 680.dp).navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (isLandscapeWindow() && maxWidth >= 560.dp) {
            val sidebarWidth = minOf(maxWidth * .38f, 320.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(sidebarWidth).fillMaxHeight()
                    .verticalScroll(rememberScrollState()).testTag("sheet_options"),
                    verticalArrangement = Arrangement.spacedBy(14.dp), content = header)
                Box(Modifier.weight(1f).fillMaxHeight().testTag("sheet_content"), content = content)
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                header()
                Box(Modifier.weight(1f).fillMaxWidth().testTag("sheet_content"), content = content)
            }
        }
    }
}
