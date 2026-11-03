package com.equinoxtrack.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.ui.theme.AmountStyle
import com.equinoxtrack.app.ui.theme.Eq
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/* ------------------------------------------------------------------ */
/* Interaction                                                         */
/* ------------------------------------------------------------------ */

/** Subtle spring scale while pressed: the core micro-interaction of every tappable surface. */
@Composable
fun Modifier.pressable(
    onClick: (() -> Unit)?,
    shape: Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
): Modifier {
    if (onClick == null) return this.clip(shape)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

/* ------------------------------------------------------------------ */
/* Surfaces                                                            */
/* ------------------------------------------------------------------ */

@Composable
fun EqCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = Eq.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Eq.colors
    val edge = Brush.verticalGradient(listOf(c.strokeStrong, c.stroke.copy(alpha = 0.4f)))
    Column(
        modifier = modifier
            .pressable(onClick, shape)
            .background(color, shape)
            .border(BorderStroke(1.dp, edge), shape)
            .padding(padding),
        content = content,
    )
}

/**
 * The signature "equinox" surface: deep gradient, a soft light source in the corner and faint
 * orbital rings. Used for the balance hero and other brand moments.
 */
@Composable
fun HeroSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    brush: Brush = Eq.colors.heroBrush,
    glow: Color = Eq.colors.accent,
    shape: Shape = RoundedCornerShape(30.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .pressable(onClick, shape)
            .background(brush, shape)
            .drawBehind {
                val r = size.maxDimension
                drawCircle(
                    brush = Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width * 0.95f, 0f), radius = r * 0.7f),
                    radius = r * 0.7f,
                    center = Offset(size.width * 0.95f, 0f),
                )
                val center = Offset(size.width * 0.88f, size.height * 0.08f)
                listOf(0.38f, 0.58f, 0.80f).forEachIndexed { i, f ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.07f - i * 0.015f),
                        radius = r * f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.03f))), shape),
        content = content,
    )
}

/** Soft accent glow behind screens, giving depth without decoration. */
@Composable
fun ScreenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Eq.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(listOf(c.backgroundGlow, Color.Transparent), center = Offset(size.width * 0.15f, 0f), radius = size.width * 1.1f),
                    radius = size.width * 1.1f,
                    center = Offset(size.width * 0.15f, 0f),
                )
            },
        content = content,
    )
}

/* ------------------------------------------------------------------ */
/* Text & badges                                                       */
/* ------------------------------------------------------------------ */

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Eq.type.titleMedium, color = Eq.colors.textPrimary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = Eq.type.labelLarge,
                color = Eq.colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Eq.colors.textTertiary) {
    Text(text.uppercase(), style = Eq.type.labelSmall, color = color, modifier = modifier)
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 42.dp, corner: Dp = 14.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.10f))))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Eq.type.labelMedium,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Animates between Long values without losing precision on large amounts. */
@Composable
fun animatedLong(target: Long, durationMs: Int = 650): Long {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target != to) {
            val current = from + ((to - from) * progress.value).toLong()
            from = current
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
        }
    }
    return from + ((to - from) * progress.value).toLong()
}

@Composable
fun MoneyText(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AmountStyle,
    color: Color = Eq.colors.textPrimary,
    signed: Boolean = false,
    hidden: Boolean = false,
    animate: Boolean = false,
    compact: Boolean = false,
    textAlign: TextAlign? = null,
) {
    val value = if (animate) animatedLong(minor) else minor
    Text(
        text = if (hidden) Money.masked(currency) else Money.format(value, currency, signed = signed, compact = compact),
        style = style,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

/* ------------------------------------------------------------------ */
/* Buttons                                                             */
/* ------------------------------------------------------------------ */

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = Eq.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .heightIn(min = 54.dp)
            .pressable(if (enabled) onClick else null, shape)
            .background(if (enabled) c.accentBrush else Brush.linearGradient(listOf(c.surfaceRaised, c.surfaceRaised)), shape)
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.18f else 0f), shape)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) c.onAccent else c.textTertiary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Eq.type.labelLarge, color = if (enabled) c.onAccent else c.textTertiary)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Eq.colors.textPrimary,
) {
    val c = Eq.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .heightIn(min = 50.dp)
            .pressable(onClick, shape)
            .background(c.surfaceRaised, shape)
            .border(1.dp, c.stroke, shape)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Eq.type.labelLarge, color = color)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Eq.colors.textPrimary,
    background: Color = Eq.colors.surfaceRaised,
    size: Dp = 42.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .pressable(onClick, CircleShape)
            .background(background, CircleShape)
            .border(1.dp, Eq.colors.stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/* ------------------------------------------------------------------ */
/* Segmented control                                                   */
/* ------------------------------------------------------------------ */

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    colorFor: ((T) -> Color)? = null,
) {
    val c = Eq.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surfaceSunken, shape)
            .border(1.dp, c.stroke, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val tint = colorFor?.invoke(option) ?: c.accent
            val bg by animateColorAsState(if (isSelected) tint.copy(alpha = if (c.isDark) 0.22f else 0.14f) else Color.Transparent, label = "seg")
            val fg by animateColorAsState(if (isSelected) tint else c.textSecondary, label = "segText")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = Eq.type.labelLarge, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = Eq.colors.accent) {
    val c = Eq.colors
    val bg by animateColorAsState(if (selected) tint.copy(alpha = if (c.isDark) 0.22f else 0.13f) else c.surfaceRaised, label = "chip")
    val border by animateColorAsState(if (selected) tint.copy(alpha = 0.6f) else c.stroke, label = "chipBorder")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) tint else c.textSecondary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Eq.type.labelLarge, color = if (selected) c.textPrimary else c.textSecondary, maxLines = 1)
    }
}

/* ------------------------------------------------------------------ */
/* Top bar & layout helpers                                            */
/* ------------------------------------------------------------------ */

@Composable
fun EqTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Eq.type.headlineSmall, color = Eq.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Eq.type.bodySmall, color = Eq.colors.textSecondary, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Staggered entrance animation for dashboard sections. */
@Composable
fun Reveal(index: Int, content: @Composable () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(420, delayMillis = 60 * index)) + slideInVertically(tween(480, delayMillis = 60 * index, easing = FastOutSlowInEasing)) { it / 6 },
    ) { content() }
}

/* ------------------------------------------------------------------ */
/* Inputs                                                              */
/* ------------------------------------------------------------------ */

@Composable
fun EqTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    prefix: String? = null,
    leadingIcon: ImageVector? = null,
    supporting: String? = null,
    isError: Boolean = false,
    password: Boolean = false,
) {
    val c = Eq.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder, color = c.textTertiary) } } else null,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        prefix = if (prefix != null) { { Text(prefix, color = c.textSecondary) } } else null,
        leadingIcon = if (leadingIcon != null) { { Icon(leadingIcon, null, tint = c.textSecondary) } } else null,
        supportingText = if (supporting != null) { { Text(supporting) } } else null,
        shape = RoundedCornerShape(16.dp),
        textStyle = Eq.type.bodyLarge.copy(color = c.textPrimary),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.stroke,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.textSecondary,
            cursorColor = c.accent,
        ),
    )
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    EqTextField(value, onValueChange, label = "Search", placeholder = placeholder, leadingIcon = Icons.Rounded.Search, modifier = modifier)
}

@Composable
fun SwatchPicker(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { argb ->
            val col = Color(argb)
            val isSel = argb == selected
            val ring by animateDpAsState(if (isSel) 3.dp else 0.dp, label = "ring")
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(col)
                    .then(if (ring > 0.5.dp) Modifier.border(ring, Eq.colors.textPrimary, CircleShape) else Modifier)
                    .clickable { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun IconPicker(keys: List<String>, selected: String, tint: Color, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { key ->
            val isSel = key == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSel) tint.copy(alpha = 0.22f) else Eq.colors.surfaceRaised)
                    .border(1.dp, if (isSel) tint else Eq.colors.stroke, RoundedCornerShape(14.dp))
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.of(key), null, tint = if (isSel) tint else Eq.colors.textSecondary, modifier = Modifier.size(21.dp))
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Progress                                                            */
/* ------------------------------------------------------------------ */

@Composable
fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Eq.colors.accent,
    height: Dp = 8.dp,
    marker: Float? = null,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "progress")
    val c = Eq.colors
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2
        drawRoundRect(color = c.surfaceSunken, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        if (animated > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color)),
                size = size.copy(width = (size.width * animated).coerceAtLeast(size.height)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
        }
        if (marker != null) {
            val x = size.width * marker.coerceIn(0f, 1f)
            drawLine(c.textTertiary, Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 2.dp.toPx())
        }
    }
}

/* ------------------------------------------------------------------ */
/* Rows                                                                */
/* ------------------------------------------------------------------ */

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = Eq.colors.accent,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 38.dp, corner = 12.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Eq.type.titleSmall, color = Eq.colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = Eq.type.bodySmall, color = Eq.colors.textSecondary)
        }
        if (trailing != null) trailing() else if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, null, tint = Eq.colors.textTertiary)
        }
    }
}

@Composable
fun EqSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = Eq.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent,
            checkedTrackColor = c.accent,
            uncheckedThumbColor = c.textSecondary,
            uncheckedTrackColor = c.surfaceSunken,
            uncheckedBorderColor = c.stroke,
        ),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(Eq.colors.accentSoft))
            Icon(icon, null, tint = Eq.colors.accent, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = Eq.type.titleLarge, color = Eq.colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = Eq.type.bodyMedium, color = Eq.colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            PrimaryButton(action, onAction)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Dialogs & sheets                                                    */
/* ------------------------------------------------------------------ */

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Eq.colors.surfaceRaised,
        title = { Text(title, style = Eq.type.titleLarge) },
        text = { Text(message, style = Eq.type.bodyMedium, color = Eq.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) Eq.colors.expense else Eq.colors.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Eq.colors.textSecondary) } },
    )
}

data class PickerItem<T>(val value: T, val title: String, val subtitle: String? = null, val icon: ImageVector? = null, val tint: Color? = null)

@Composable
fun <T> PickerSheet(
    title: String,
    items: List<PickerItem<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    searchable: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Eq.colors.surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(title, style = Eq.type.headlineSmall, color = Eq.colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            if (searchable) {
                SearchField(query, { query = it }, "Search")
                Spacer(Modifier.height(8.dp))
            }
            val shown = if (query.isBlank()) items else items.filter {
                it.title.contains(query, ignoreCase = true) || (it.subtitle?.contains(query, ignoreCase = true) ?: false)
            }
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(shown) { item ->
                    val isSel = item.value == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSel) Eq.colors.accentSoft else Color.Transparent)
                            .clickable { onSelect(item.value); onDismiss() }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.icon != null) {
                            IconBadge(item.icon, item.tint ?: Eq.colors.accent, size = 38.dp, corner = 12.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = Eq.type.titleSmall, color = Eq.colors.textPrimary)
                            if (item.subtitle != null) Text(item.subtitle, style = Eq.type.bodySmall, color = Eq.colors.textSecondary)
                        }
                        if (isSel) Icon(Icons.Rounded.Check, null, tint = Eq.colors.accent)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun EqDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK", color = Eq.colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Eq.colors.textSecondary) } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun EqTimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Eq.colors.surfaceRaised,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK", color = Eq.colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Eq.colors.textSecondary) } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
    )
}

/** Small dot used in legends. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(6.dp))
        Text(label, style = Eq.type.labelMedium, color = Eq.colors.textSecondary)
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Eq.colors.stroke))
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

/** Offsets a composable vertically by a fraction of its height; used for the floating add button. */
fun Modifier.lift(y: Dp): Modifier = this.offset(y = -y)
