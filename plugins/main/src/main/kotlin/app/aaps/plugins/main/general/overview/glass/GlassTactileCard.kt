package app.aaps.plugins.main.general.overview.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tactile 3D card shared by the Glass dialogs: face content over a darker
 * bottom edge that peeks out, reproducing the physical-key depth
 * from the GlycoCalm reference.
 */
@Composable
internal fun TactileCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    face: Brush,
    border: Color,
    borderWidth: Dp = 1.dp,
    edge: Color,
    edgeHeight: Dp = 3.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = edgeHeight)
                .clip(shape)
                .background(edge)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(face)
                .border(borderWidth, border, shape)
                .clickable(enabled = enabled) { onClick() },
            content = content,
            horizontalAlignment = Alignment.CenterHorizontally
        )
    }
}
