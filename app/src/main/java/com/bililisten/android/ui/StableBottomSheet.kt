package com.bililisten.android.ui

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity

/** Stop upward residual motion at an expanded sheet's edge; downward dismissal still belongs to the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StableBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val boundary = remember(sheetState) {
        object : NestedScrollConnection {
            fun expanded() = sheetState.currentValue == SheetValue.Expanded && sheetState.targetValue == SheetValue.Expanded
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (expanded()) Offset(0f, available.y.coerceAtMost(0f)) else Offset.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if (expanded()) Velocity(0f, available.y.coerceAtMost(0f)) else Velocity.Zero
        }
    }
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        ModalBottomSheet(onDismissRequest, modifier, sheetState = sheetState, sheetMaxWidth = sheetMaxWidth,
            containerColor = containerColor, contentColor = contentColor, dragHandle = dragHandle) {
            Column(Modifier.fillMaxWidth().nestedScroll(boundary), content = content)
        }
    }
}
