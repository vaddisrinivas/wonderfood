package com.wonderfood.app.ui.main

import android.graphics.BitmapFactory
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wonderfood.app.theme.WonderFoodTheme

enum class DatabaseViewMode {
    GALLERY,
    LIST,
}

enum class BadgeConfidence(val label: String) {
    VERIFIED("verified"),
    LIKELY("likely"),
    ESTIMATED("estimated"),
    UNKNOWN("unknown"),
}

data class DatabaseChip(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

data class TimelineEntry(
    val icon: String,
    val title: String,
    val subtitle: String,
)

@Composable
fun PageHeader(
    image: String?,
    fallback: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    source: String? = null,
    confidence: BadgeConfidence? = null,
    onClose: (() -> Unit)? = null,
    primaryAction: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FoodImage(image = image, fallback = fallback, color = MaterialTheme.colorScheme.primaryContainer, size = 64.dp, contentDescription = "$title image")
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                source?.let { SourceBadge(it) }
                confidence?.let { ConfidenceBadge(it) }
            }
        }
        primaryAction?.invoke()
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close page")
            }
        }
    }
}

@Composable
fun PropertyRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    source: String? = null,
    confidence: BadgeConfidence? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Row(
        modifier = rowModifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "Unknown" }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        source?.let { SourceBadge(it) }
        confidence?.let { ConfidenceBadge(it) }
    }
}

@Composable
fun RelationRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { role = Role.Button }.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SourceBadge(source: String, modifier: Modifier = Modifier) {
    val label = when {
        "assistant" in source || "voice" in source -> "voice"
        "ai" in source || "chat" in source -> "AI"
        "receipt" in source -> "receipt"
        "seed" in source -> "seed"
        source.isBlank() -> "source?"
        else -> source.take(10)
    }
    Surface(modifier = modifier.semantics { contentDescription = "Source $label" }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun ConfidenceBadge(confidence: BadgeConfidence, modifier: Modifier = Modifier) {
    val color = when (confidence) {
        BadgeConfidence.VERIFIED -> MaterialTheme.colorScheme.primaryContainer
        BadgeConfidence.LIKELY -> MaterialTheme.colorScheme.secondaryContainer
        BadgeConfidence.ESTIMATED -> MaterialTheme.colorScheme.tertiaryContainer
        BadgeConfidence.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = modifier.semantics { contentDescription = "Confidence ${confidence.label}" },
        shape = RoundedCornerShape(8.dp),
        color = color,
    ) {
        Text(
            confidence.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
fun FoodImage(
    image: String?,
    fallback: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val bitmap = remember(image) {
        val value = image.orEmpty()
        if (value.isLocalImageUri()) {
            runCatching {
                context.contentResolver.openInputStream(value.toUri())?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        } else {
            null
        }
    }
    Surface(
        modifier = modifier.size(size).then(
            if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier,
        ),
        shape = RoundedCornerShape(8.dp),
        color = color,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(image.displayImageText(fallback), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

private fun String.isLocalImageUri(): Boolean =
    startsWith("content://") || startsWith("file://")

private fun String?.displayImageText(fallback: String): String {
    val value = orEmpty()
    return value
        .takeUnless { it.isLocalImageUri() || it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        .orEmpty()
        .ifBlank { fallback }
}

@Composable
fun DatabaseToolbar(
    icon: String,
    title: String,
    subtitle: String,
    query: String,
    placeholder: String,
    onQuery: (String) -> Unit,
    viewMode: DatabaseViewMode,
    onViewModeChange: (DatabaseViewMode) -> Unit,
    chips: List<DatabaseChip>,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FoodImage(image = icon, fallback = "🍽️", color = MaterialTheme.colorScheme.primaryContainer, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GalleryListToggle(selected = viewMode, onSelected = onViewModeChange)
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(8.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chips.size) { index ->
                    val chip = chips[index]
                    FilterChip(selected = chip.selected, onClick = chip.onClick, label = { Text(chip.label, maxLines = 1) })
                }
            }
        }
    }
}

@Composable
fun GalleryListToggle(
    selected: DatabaseViewMode,
    onSelected: (DatabaseViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onSelected(DatabaseViewMode.GALLERY) },
            modifier = Modifier.semantics {
                role = Role.RadioButton
                this.selected = selected == DatabaseViewMode.GALLERY
                contentDescription = "Gallery view"
            },
        ) {
            Icon(
                Icons.Rounded.GridView,
                contentDescription = null,
                tint = if (selected == DatabaseViewMode.GALLERY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onSelected(DatabaseViewMode.LIST) },
            modifier = Modifier.semantics {
                role = Role.RadioButton
                this.selected = selected == DatabaseViewMode.LIST
                contentDescription = "List view"
            },
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.List,
                contentDescription = null,
                tint = if (selected == DatabaseViewMode.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun FilterSheet(
    title: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
fun ActivityTimeline(entries: List<TimelineEntry>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { entry ->
            PropertyRow(label = entry.title, value = entry.subtitle, icon = entry.icon)
        }
    }
}

@Composable
fun MutationReviewSheet(
    title: String,
    body: String,
    confirmLabel: String,
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun UndoSnackbar(message: String, onUndo: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.inverseSurface) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
            TextButton(onClick = onUndo) { Text("Undo") }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss undo") }
        }
    }
}

@Composable
fun ConfirmActionButton(
    label: String,
    title: String,
    body: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier, shape = RoundedCornerShape(8.dp)) {
        Text(label)
    }
    MutationReviewSheet(
        title = title,
        body = body,
        confirmLabel = label,
        visible = open,
        onConfirm = {
            open = false
            onConfirm()
        },
        onDismiss = { open = false },
    )
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun DatabaseComponentsPreview() {
    WonderFoodTheme(dynamicColor = false, darkTheme = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DatabaseToolbar(
                icon = "🥬",
                title = "Kitchen files",
                subtitle = "42 visible",
                query = "",
                placeholder = "Search food",
                onQuery = {},
                viewMode = DatabaseViewMode.GALLERY,
                onViewModeChange = {},
                chips = listOf(
                    DatabaseChip("All zones", true, {}),
                    DatabaseChip("Fridge", false, {}),
                    DatabaseChip("Sort recent", true, {}),
                    DatabaseChip("Low", false, {}),
                ),
            )
            PageHeader(image = null, fallback = "🥬", title = "Greek yogurt", subtitle = "Food page", source = "receipt", confidence = BadgeConfidence.LIKELY)
            PropertyRow(label = "Protein", value = "18g per serving", icon = "💪", source = "label", confidence = BadgeConfidence.VERIFIED)
            RelationRow(icon = "📖", title = "Breakfast bowl", subtitle = "Uses yogurt and berries", onClick = {})
            ActivityTimeline(listOf(TimelineEntry("🧾", "Bought", "Today from receipt")))
        }
    }
}
