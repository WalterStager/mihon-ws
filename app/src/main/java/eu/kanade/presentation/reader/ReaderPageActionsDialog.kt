package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.material.padding

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onTranslate: (isVertical: Boolean) -> Unit,
    onBoundingBoxes: (isVertical: Boolean) -> Unit,
    onBoundingBoxesNoMerge: (isVertical: Boolean) -> Unit,
) {
    var isVertical by remember { mutableStateOf(true) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Vertical", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isVertical, onCheckedChange = { isVertical = it })
                }
            }
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Translate",
                    icon = Icons.Outlined.Translate,
                    onClick = {
                        onTranslate(isVertical)
                        onDismissRequest()
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Bounding Boxes",
                    icon = Icons.Outlined.CropFree,
                    onClick = {
                        onBoundingBoxes(isVertical)
                        onDismissRequest()
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = "No merge",
                    icon = Icons.Outlined.CropFree,
                    onClick = {
                        onBoundingBoxesNoMerge(isVertical)
                        onDismissRequest()
                    },
                )
            }
        }
    }
}
