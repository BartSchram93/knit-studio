package studio.knit.ui.pattern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import studio.knit.calc.Needles
import studio.knit.calc.pretty
import studio.knit.model.Needle
import studio.knit.model.NeedleType
import studio.knit.model.Yarn
import studio.knit.model.YarnWeight
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.unitFormat
import studio.knit.ui.components.BoundField
import studio.knit.ui.components.BoundNumberField
import studio.knit.ui.components.Group
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.PickerRow
import studio.knit.ui.components.PrimaryButton
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.Sheet
import studio.knit.ui.components.pressable
import studio.knit.ui.theme.Knit

/** A considered set of yarn-like colours for swatches and colourwork. */
val YarnColors: List<Long> = listOf(
    0xFFF4EFE6, 0xFFE8DCC8, 0xFFC9B79C, 0xFF8C7B6B, 0xFF5C4B3F, 0xFF2B2B2E, 0xFF8E8E93, 0xFFD1D1D6,
    0xFFB4533C, 0xFFD9735A, 0xFFE9A28E, 0xFF8C2F39, 0xFFC2374F, 0xFFE0768A, 0xFFF2C1C9, 0xFF9B5C8F,
    0xFFE0B04A, 0xFFF0D58C, 0xFFC08A2B, 0xFFD97B29, 0xFF8A9A5B, 0xFF5E7B4A, 0xFF2F5D45, 0xFFB7C9A8,
    0xFF2F5D62, 0xFF4F8A8B, 0xFF9CC5C9, 0xFF3A6EA5, 0xFF5B6CA8, 0xFF1F3A5F, 0xFFA9BCD9, 0xFF6A4C93,
)

@Composable
fun ColorDot(argb: Long?, size: Dp = 22.dp, selected: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(if (argb != null) Color(argb) else Knit.colors.fill)
            .border(if (selected) 2.5.dp else 0.6.dp, if (selected) Knit.colors.accent else Knit.colors.separator, CircleShape),
    )
}

@Composable
fun ColorGrid(selected: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in YarnColors.chunked(8)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (c in row) ColorDot(c, 34.dp, c == selected, Modifier.pressable { onSelect(c) }.semantics { contentDescription = "Colour" })
            }
        }
    }
}

/** Edits one yarn. Used for pattern materials and for the stash. */
@Composable
fun YarnSheet(initial: Yarn, isNew: Boolean, onSave: (Yarn) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val settings by LocalApp.current.settings.collectAsState()
    val units = unitFormat()
    var y by remember { mutableStateOf(initial) }
    Sheet(if (isNew) "New yarn" else "Yarn", onDismiss, doneLabel = "Save", onDone = { onSave(y); onDismiss() }) {
        Column(Modifier.padding(horizontal = PagePadding).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BoundField(y.brand, { y = y.copy(brand = it) }, Modifier.weight(1f), "Brand", "e.g. Holst", capitalization = KeyboardCapitalization.Words)
                BoundField(y.name, { y = y.copy(name = it) }, Modifier.weight(1f), "Yarn", "e.g. Supersoft", capitalization = KeyboardCapitalization.Words)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BoundField(y.label, { y = y.copy(label = it) }, Modifier.weight(0.6f), "Used as", "MC, A, B…", capitalization = KeyboardCapitalization.Characters)
                BoundField(y.color, { y = y.copy(color = it) }, Modifier.weight(1f), "Colour name", "e.g. Teal")
            }
            BoundField(y.fiber, { y = y.copy(fiber = it) }, Modifier.fillMaxWidth(), "Fibre", "e.g. 100% merino", capitalization = KeyboardCapitalization.None)
        }
        SectionHeader("Weight")
        Group {
            PickerRow("Yarn weight", YarnWeight.entries, y.weight, { if (settings.ukTerms) it.ukLabel else it.label }) { y = y.copy(weight = it) }
        }
        SectionHeader("Amount")
        Column(Modifier.padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BoundNumberField(y.metersPerSkein?.let { units.yarnValue(it) }, { v -> y = y.copy(metersPerSkein = v?.takeIf { it > 0 }?.let { units.toMeters(it) }) }, Modifier.weight(1f), "Length per skein", units.yarnUnit, decimals = 0)
                BoundNumberField(y.gramsPerSkein?.let { units.weightValue(it) }, { v -> y = y.copy(gramsPerSkein = v?.takeIf { it > 0 }?.let { units.toGrams(it) }) }, Modifier.weight(1f), "Weight per skein", units.weightUnit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BoundNumberField(y.skeins, { v -> y = y.copy(skeins = v?.takeIf { it > 0 }) }, Modifier.weight(1f), "Skeins")
                BoundField(y.dyeLot, { y = y.copy(dyeLot = it) }, Modifier.weight(1f), "Dye lot", capitalization = KeyboardCapitalization.Characters)
            }
        }
        SectionHeader("Colour")
        ColorGrid(y.colorArgb, { y = y.copy(colorArgb = it) }, Modifier.padding(horizontal = PagePadding + 4.dp))
        SectionHeader("Notes")
        BoundField(y.notes, { y = y.copy(notes = it) }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), singleLine = false, minLines = 2, placeholder = "Where you bought it, how it washes…")
        if (onDelete != null) {
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Remove yarn", { onDelete(); onDismiss() }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), destructive = true)
        }
    }
}

@Composable
fun NeedleSheet(
    initial: Needle, isNew: Boolean, onSave: (Needle) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit,
    /** Brands already in use, offered as one-tap choices. */
    brands: List<String> = emptyList(),
) {
    val units = unitFormat()
    var n by remember { mutableStateOf(initial) }
    Sheet(if (isNew) "New needle" else "Needle", onDismiss, doneLabel = "Save", onDone = { onSave(n); onDismiss() }) {
        SectionHeader("Size")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (size in Needles.sizes.filter { it.mm in 2.0..12.0 }) {
                studio.knit.ui.components.Chip(
                    if (units.imperial && size.us != null) "US ${size.us}" else "${size.mm.pretty(2)} mm",
                    kotlin.math.abs(n.sizeMm - size.mm) < 0.01, { n = n.copy(sizeMm = size.mm) },
                )
            }
        }
        Column(Modifier.padding(horizontal = PagePadding).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BoundNumberField(n.sizeMm, { v -> if (v != null && v > 0) n = n.copy(sizeMm = v) }, Modifier.weight(1f), "Diameter", "mm", decimals = 2)
                BoundNumberField(n.lengthCm?.let { units.lengthValue(it) }, { v -> n = n.copy(lengthCm = v?.takeIf { it > 0 }?.let { units.toCm(it) }) }, Modifier.weight(1f), "Length", units.lengthUnit, decimals = 0)
            }
            BoundField(n.material, { n = n.copy(material = it) }, Modifier.fillMaxWidth(), "Material", "Bamboo, steel, wood…")
        }
        SectionHeader("Brand")
        BoundField(n.brand, { n = n.copy(brand = it) }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), placeholder = "e.g. ChiaoGoo", capitalization = KeyboardCapitalization.Words)
        if (brands.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = PagePadding).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (b in brands) {
                    studio.knit.ui.components.Chip(b, n.brand.trim().equals(b, ignoreCase = true), { n = n.copy(brand = b) })
                }
            }
        }
        SectionHeader("Type")
        Group { PickerRow("Needle type", NeedleType.entries, n.type, { it.label }) { n = n.copy(type = it) } }
        SectionHeader("Notes")
        BoundField(n.notes, { n = n.copy(notes = it) }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), placeholder = "e.g. for the ribbing")
        if (onDelete != null) {
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Remove needle", { onDelete(); onDismiss() }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), destructive = true)
        }
    }
}

fun yarnSummary(y: Yarn, units: studio.knit.calc.UnitFormat, uk: Boolean): String = listOfNotNull(
    y.weight.takeIf { it != YarnWeight.UNKNOWN }?.let { if (uk) it.ukLabel else it.label },
    y.color.takeIf { it.isNotBlank() },
    y.skeins?.let { s -> "${s.pretty()} ${if (s == 1.0) "skein" else "skeins"}" },
    y.totalMeters?.let { units.yarn(it) },
).joinToString("  ·  ")

/** One line describing a needle; [withBrand] is false where the brand is already the heading. */
fun needleSummary(n: Needle, units: studio.knit.calc.UnitFormat, withBrand: Boolean = true): String = listOfNotNull(
    n.type.label, n.lengthCm?.let { units.length(it, 0) }, n.material.takeIf { it.isNotBlank() },
    n.brand.trim().takeIf { withBrand && it.isNotEmpty() }, n.notes.takeIf { it.isNotBlank() },
).joinToString("  ·  ")
