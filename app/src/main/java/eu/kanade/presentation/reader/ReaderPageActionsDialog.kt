package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.material.padding

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onTranslate: () -> Unit,
    onBoundingBoxes: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                title = "Translate",
                icon = Icons.Outlined.Translate,
                onClick = {
                    onTranslate()
                    onDismissRequest()
                },
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                title = "Bounding Boxes",
                icon = Icons.Outlined.CropFree,
                onClick = {
                    onBoundingBoxes()
                    onDismissRequest()
                },
            )
        }
    }
}
