package studio.knit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import studio.knit.ui.theme.Knit

/** Extra space pages leave at the bottom (the tab bar, when visible). */
val LocalBottomInset = compositionLocalOf { 0.dp }

val CardShape = RoundedCornerShape(14.dp)
val FieldShape = RoundedCornerShape(10.dp)
val PagePadding = 16.dp

/**
 * Tap handling with a quiet press state (the content dims) instead of a ripple, which reads as
 * native on iOS and unobtrusive on Android. Guarantees a 44 dp minimum touch target via callers.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.55f else if (enabled) 1f else 0.38f)
    this.alpha(alpha).combinedClickable(
        interactionSource = source, indication = null, enabled = enabled, role = role,
        onLongClick = onLongClick, onClick = onClick,
    )
}

@Composable
fun KIcon(icon: ImageVector, tint: Color = Knit.colors.label, size: Dp = 22.dp, modifier: Modifier = Modifier, description: String? = null) {
    Icon(icon, description, modifier.size(size), tint)
}

@Composable
fun IconBtn(
    icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    tint: Color = Knit.colors.accent, enabled: Boolean = true, size: Dp = 24.dp,
) {
    Box(modifier.size(44.dp).pressable(enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(size), tint)
    }
}

@Composable
fun PrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null,
    enabled: Boolean = true, destructive: Boolean = false,
) {
    val bg = if (destructive) Knit.colors.error else Knit.colors.accent
    Row(
        modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).background(bg).pressable(enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { KIcon(icon, Knit.colors.onAccent, 20.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, style = Knit.type.headline, color = if (destructive) Color.White else Knit.colors.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tinted secondary action. */
@Composable
fun SoftButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null,
    enabled: Boolean = true, color: Color = Knit.colors.accent, compact: Boolean = false,
) {
    Row(
        modifier.heightIn(min = if (compact) 36.dp else 50.dp).clip(RoundedCornerShape(if (compact) 18.dp else 14.dp))
            .background(if (color == Knit.colors.accent) Knit.colors.accentSoft else Knit.colors.fill)
            .pressable(enabled, onClick = onClick).padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 6.dp else 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { KIcon(icon, color, if (compact) 17.dp else 20.dp); Spacer(Modifier.width(6.dp)) }
        Text(text, style = if (compact) Knit.type.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else Knit.type.headline, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TextBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Knit.colors.accent, enabled: Boolean = true, bold: Boolean = false) {
    Box(modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp).pressable(enabled, onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = if (bold) Knit.type.headline else Knit.type.body, color = color, maxLines = 1)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(Knit.colors.surface)
            .then(if (onClick != null) Modifier.pressable(onLongClick = onLongClick, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Small-caps style header above a group, as in iOS grouped lists. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = PagePadding + 4.dp, end = PagePadding, top = 22.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = Knit.type.overline, color = Knit.colors.secondaryLabel, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

@Composable
fun SectionFooter(text: String) {
    Text(text, style = Knit.type.footnote, color = Knit.colors.secondaryLabel, modifier = Modifier.padding(start = PagePadding + 4.dp, end = PagePadding + 4.dp, top = 7.dp))
}

/** A rounded group of rows on the grouped background. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(horizontal = PagePadding).fillMaxWidth().clip(CardShape).background(Knit.colors.surface), content = content)
}

@Composable
fun RowDivider(inset: Dp = 16.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.6.dp).background(Knit.colors.separator))
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    titleColor: Color = Knit.colors.label,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.pressable(onLongClick = onLongClick, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = Knit.type.body, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = Knit.type.footnote, color = Knit.colors.secondaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = Knit.type.body, color = Knit.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        if (chevron) { Spacer(Modifier.width(6.dp)); KIcon(Icons.ChevronRight, Knit.colors.tertiaryLabel, 16.dp) }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    ListRow(title, subtitle = subtitle, onClick = { onChange(!checked) }, trailing = {
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Knit.colors.accent, checkedThumbColor = Color.White,
                uncheckedTrackColor = Knit.colors.fill, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
            ),
        )
    })
}

/** iOS-style segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(9.dp)).background(Knit.colors.fill).padding(2.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Knit.colors.surfaceRaised else Color.Transparent)
            Box(
                Modifier.weight(1f).heightIn(min = 32.dp).clip(RoundedCornerShape(7.dp)).background(bg)
                    .pressable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 1.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = Knit.type.footnote.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
                    color = Knit.colors.label, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false,
                )
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    val bg by animateColorAsState(if (selected) Knit.colors.accent else Knit.colors.surface)
    Row(
        modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(17.dp)).background(bg).pressable(onClick = onClick).padding(horizontal = 13.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(6.dp)) }
        Text(text, style = Knit.type.subhead, color = if (selected) Knit.colors.onAccent else Knit.colors.label, maxLines = 1)
    }
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, style = Knit.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = color, maxLines = 1)
    }
}

/** A filled, rounded text field with an optional label above and unit behind. */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    suffix: String? = null,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    minLines: Int = 1,
    style: TextStyle = Knit.type.body,
    isError: Boolean = false,
) {
    Column(modifier) {
        if (label != null) {
            Text(label, style = Knit.type.footnote, color = Knit.colors.secondaryLabel, modifier = Modifier.padding(start = 2.dp, bottom = 5.dp))
        }
        Row(
            Modifier.fillMaxWidth().clip(FieldShape).background(Knit.colors.fill)
                .then(if (isError) Modifier.border(1.dp, Knit.colors.error, FieldShape) else Modifier)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            BasicTextField(
                value = value, onValueChange = onChange,
                modifier = Modifier.weight(1f).semantics { if (label != null) contentDescription = label },
                textStyle = style.copy(color = Knit.colors.label),
                singleLine = singleLine, minLines = minLines,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalization, imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
                cursorBrush = SolidColor(Knit.colors.accent),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = style, color = Knit.colors.tertiaryLabel, maxLines = if (singleLine) 1 else 3)
                        inner()
                    }
                },
            )
            if (suffix != null) {
                Spacer(Modifier.width(6.dp))
                Text(suffix, style = Knit.type.callout, color = Knit.colors.secondaryLabel)
            }
        }
    }
}

/** Decimal input that tolerates a comma as the decimal separator. */
@Composable
fun NumberField(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null,
    suffix: String? = null, placeholder: String = "0", decimal: Boolean = true,
) {
    Field(
        value, { new -> onChange(new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(9)) },
        modifier, label, placeholder, suffix, keyboard = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
    )
}

fun String.toDoubleLenient(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange, modifier: Modifier = Modifier, label: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (label != null) Text(label, style = Knit.type.body, color = Knit.colors.label, modifier = Modifier.weight(1f))
        Row(Modifier.clip(RoundedCornerShape(9.dp)).background(Knit.colors.fill), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Minus, "Decrease ${label ?: ""}", { onChange((value - 1).coerceIn(range)) }, tint = Knit.colors.label, enabled = value > range.first, size = 18.dp)
            Text(value.toString(), style = Knit.type.headline, color = Knit.colors.label, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
            IconBtn(Icons.Plus, "Increase ${label ?: ""}", { onChange((value + 1).coerceIn(range)) }, tint = Knit.colors.label, enabled = value < range.last, size = 18.dp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------------------------

@Composable
private fun TopBar(
    title: String, showTitle: Boolean, solid: Boolean, onBack: (() -> Unit)?, backLabel: String?,
    actions: @Composable RowScope.() -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val bg by animateColorAsState(if (solid) Knit.colors.background.copy(alpha = 0.96f) else Knit.colors.background.copy(alpha = 0f))
    val titleAlpha by animateFloatAsState(if (showTitle) 1f else 0f)
    Column(Modifier.fillMaxWidth().background(bg)) {
        Spacer(Modifier.height(insets.calculateTopPadding()))
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp)) {
            if (onBack != null) {
                Row(Modifier.align(Alignment.CenterStart).pressable(onClick = onBack).padding(end = 8.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.ChevronLeft, "Back", Modifier.padding(start = 6.dp).size(24.dp), Knit.colors.accent)
                    if (backLabel != null) Text(backLabel, style = Knit.type.body, color = Knit.colors.accent, maxLines = 1)
                }
            }
            Text(
                title, style = Knit.type.headline, color = Knit.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp).alpha(titleAlpha),
            )
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (solid) Box(Modifier.fillMaxWidth().height(0.6.dp).background(Knit.colors.separator))
    }
}

/**
 * A scrolling page with an iOS-style large title that collapses into the bar.
 */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    largeTitle: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val dir = LocalLayoutDirection.current
    val scrolled by remember(state) { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 24 } }
    val bottom = insets.calculateBottomPadding() + LocalBottomInset.current
    Box(Modifier.fillMaxSize().background(Knit.colors.background)) {
        LazyColumn(
            Modifier.fillMaxSize().imePadding(), state = state,
            contentPadding = PaddingValues(
                start = insets.calculateLeftPadding(dir), end = insets.calculateRightPadding(dir),
                top = insets.calculateTopPadding() + 48.dp, bottom = bottom + 28.dp,
            ),
        ) {
            if (largeTitle) item(key = "__title") {
                Text(title, style = Knit.type.largeTitle, color = Knit.colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = PagePadding + 4.dp, end = PagePadding, top = 2.dp, bottom = 6.dp).semantics { heading() })
            }
            content()
        }
        TopBar(title, showTitle = !largeTitle || scrolled, solid = scrolled, onBack = onBack, backLabel = backLabel, actions = actions)
        Box(Modifier.fillMaxSize().padding(bottom = bottom), content = overlay)
    }
}

/** A page whose content manages its own layout (editors, canvases). */
@Composable
fun FixedPage(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    background: Color = Knit.colors.background,
    content: @Composable ColumnScope.() -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val dir = LocalLayoutDirection.current
    Column(Modifier.fillMaxSize().background(background)) {
        TopBar(title, showTitle = true, solid = true, onBack = onBack, backLabel = null, actions = actions)
        Column(
            Modifier.fillMaxSize().padding(start = insets.calculateLeftPadding(dir), end = insets.calculateRightPadding(dir)),
            content = content,
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Knit.colors.accentSoft), contentAlignment = Alignment.Center) {
            KIcon(icon, Knit.colors.accent, 30.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = Knit.type.title3, color = Knit.colors.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = Knit.type.subhead, color = Knit.colors.secondaryLabel, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}

// ---------------------------------------------------------------------------------------------
// Sheets, dialogs, menus
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(
    title: String,
    onDismiss: () -> Unit,
    doneLabel: String = "Done",
    onDone: (() -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Knit.colors.background, dragHandle = null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(Knit.colors.tertiaryLabel.copy(alpha = 0.6f)))
            }
            Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
                Text(title, style = Knit.type.headline, color = Knit.colors.label, modifier = Modifier.align(Alignment.Center).padding(horizontal = 80.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextBtn(doneLabel, { (onDone ?: onDismiss)() }, Modifier.align(Alignment.CenterEnd), bold = true)
                if (onDone != null) TextBtn("Cancel", onDismiss, Modifier.align(Alignment.CenterStart))
            }
            Column(
                (if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).fillMaxWidth().padding(bottom = 24.dp),
                content = content,
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    destructive: Boolean = false, dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Knit.type.headline, color = Knit.colors.label) },
        text = { Text(message, style = Knit.type.subhead, color = Knit.colors.secondaryLabel) },
        confirmButton = { TextBtn(confirmLabel, { onConfirm(); onDismiss() }, color = if (destructive) Knit.colors.error else Knit.colors.accent, bold = true) },
        dismissButton = { TextBtn(dismissLabel, onDismiss) },
        containerColor = Knit.colors.surfaceRaised,
        shape = RoundedCornerShape(18.dp),
    )
}

/** A dialog with one text field, used for quick naming. */
@Composable
fun PromptDialog(
    title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit,
    placeholder: String = "", message: String? = null, keyboard: KeyboardType = KeyboardType.Text,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Knit.type.headline, color = Knit.colors.label) },
        text = {
            Column {
                if (message != null) { Text(message, style = Knit.type.subhead, color = Knit.colors.secondaryLabel); Spacer(Modifier.height(12.dp)) }
                Field(text, { text = it }, placeholder = placeholder, keyboard = keyboard)
            }
        },
        confirmButton = { TextBtn(confirmLabel, { onConfirm(text.trim()); onDismiss() }, bold = true, enabled = text.isNotBlank()) },
        dismissButton = { TextBtn("Cancel", onDismiss) },
        containerColor = Knit.colors.surfaceRaised,
        shape = RoundedCornerShape(18.dp),
    )
}

data class MenuItem(val label: String, val icon: ImageVector? = null, val destructive: Boolean = false, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
fun MenuButton(
    items: List<MenuItem>, modifier: Modifier = Modifier, icon: ImageVector = Icons.More, description: String = "More",
    tint: Color = Knit.colors.accent,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconBtn(icon, description, { open = true }, tint = tint)
        Menu(open, { open = false }, items)
    }
}

@Composable
fun Menu(open: Boolean, onDismiss: () -> Unit, items: List<MenuItem>) {
    DropdownMenu(open, onDismiss, containerColor = Knit.colors.surfaceRaised, shape = RoundedCornerShape(13.dp)) {
        for (item in items) {
            val color = if (item.destructive) Knit.colors.error else Knit.colors.label
            DropdownMenuItem(
                text = { Text(item.label, style = Knit.type.callout, color = color) },
                onClick = { onDismiss(); item.onClick() },
                enabled = item.enabled,
                trailingIcon = item.icon?.let { { KIcon(it, color, 19.dp) } },
            )
        }
    }
}

/** A pull-down style value picker shown as a row. */
@Composable
fun <T> PickerRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListRow(title, value = label(selected), onClick = { open = true }, trailing = { KIcon(Icons.ChevronDown, Knit.colors.tertiaryLabel, 14.dp) })
        Box(Modifier.align(Alignment.TopEnd)) {
            Menu(open, { open = false }, options.map { o ->
                MenuItem(label(o), icon = if (o == selected) Icons.Check else null) { onSelect(o) }
            })
        }
    }
}

/**
 * A [Field] bound to a value that lives elsewhere (the pattern being edited). Typing is kept in
 * local state so the caret never jumps, while outside changes still show up.
 */
@Composable
fun BoundField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    suffix: String? = null,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    minLines: Int = 1,
    style: TextStyle = Knit.type.body,
    isError: Boolean = false,
) {
    var local by remember { mutableStateOf(value) }
    var external by remember { mutableStateOf(value) }
    if (value != external) {
        external = value
        if (value != local) local = value
    }
    Field(local, { local = it; external = it; onChange(it) }, modifier, label, placeholder, suffix, singleLine, keyboard, capitalization, minLines, style, isError)
}

/** Numeric [BoundField]: reports a parsed number, or null while the text is empty or invalid. */
@Composable
fun BoundNumberField(
    value: Double?, onChange: (Double?) -> Unit, modifier: Modifier = Modifier, label: String? = null,
    suffix: String? = null, placeholder: String = "0", decimals: Int = 1,
) {
    fun show(v: Double?): String = when {
        v == null -> ""
        v == v.toLong().toDouble() -> v.toLong().toString()
        else -> { val f = when (decimals) { 0 -> 1.0; 1 -> 10.0; else -> 100.0 }; (kotlin.math.round(v * f) / f).toString() }
    }
    var text by remember { mutableStateOf(show(value)) }
    var external by remember { mutableStateOf(value) }
    if (value != external) {
        external = value
        if (text.toDoubleLenient() != value) text = show(value)
    }
    NumberField(text, { text = it; val parsed = it.toDoubleLenient(); external = parsed; onChange(parsed) }, modifier, label, suffix, placeholder)
}
