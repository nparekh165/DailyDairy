package com.khodiyar.dairybilling.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.ui.common.LocalFeatures
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.AppType
import com.khodiyar.dairybilling.ui.theme.Radius
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

// ---------- Text helpers ----------

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> stringResource(id, *args.toTypedArray())
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> context.getString(id, *args.toTypedArray())
}

// ---------- Tone / status ----------

enum class Tone { NEUTRAL, BRAND, SUCCESS, WARNING, DANGER, INFO, ACCENT }

data class ToneColors(val container: Color, val content: Color)

@Composable
fun Tone.colors(): ToneColors {
    val e = AppTheme.ext
    val c = AppTheme.colors
    return when (this) {
        Tone.NEUTRAL -> ToneColors(c.surfaceContainerHigh, c.onSurfaceVariant)
        Tone.BRAND -> ToneColors(c.primaryContainer, c.onPrimaryContainer)
        Tone.SUCCESS -> ToneColors(e.successContainer, e.onSuccessContainer)
        Tone.WARNING -> ToneColors(e.warningContainer, e.onWarningContainer)
        Tone.DANGER -> ToneColors(e.dangerContainer, e.onDangerContainer)
        Tone.INFO -> ToneColors(e.infoContainer, e.onInfoContainer)
        Tone.ACCENT -> ToneColors(e.accentContainer, c.onTertiaryContainer)
    }
}

/** Status pill: icon + text, so meaning never depends on color alone. */
@Composable
fun StatusBadge(label: String, tone: Tone, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val tc = tone.colors()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tc.container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tc.content, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = tc.content, maxLines = 1)
    }
}

// ---------- Surfaces ----------

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = AppTheme.colors.surface,
    border: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(Space.l),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val stroke = if (border) BorderStroke(1.dp, AppTheme.ext.border) else null
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = stroke) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = stroke) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Space.s, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = AppType.overline,
            color = AppTheme.ext.textSecondary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = Space.s)) {
                Text(action, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false, valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasize) AppTheme.colors.onSurface else AppTheme.ext.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = if (emphasize) AppType.money.copy(fontWeight = FontWeight.ExtraBold) else AppType.tableValue,
            color = if (valueColor != Color.Unspecified) valueColor else AppTheme.colors.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = AppTheme.ext.border)

/** Prominent house/flat number used throughout delivery and billing lists. */
@Composable
fun HouseBadge(houseNo: String, modifier: Modifier = Modifier, active: Boolean = true) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = 52.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(Radius.m))
            .background(if (active) AppTheme.colors.primaryContainer else AppTheme.colors.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            houseNo,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = if (active) AppTheme.colors.onPrimaryContainer else AppTheme.ext.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 72.dp),
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    tone: Tone = Tone.NEUTRAL,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                val tc = tone.colors()
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(Radius.s)).background(tc.container), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tc.content, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(Space.s))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = AppTheme.ext.textSecondary, maxLines = 2)
        }
        Spacer(Modifier.height(Space.m))
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (supporting != null) {
            Spacer(Modifier.height(2.dp))
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary, maxLines = 2)
        }
    }
}

@Composable
fun money(paise: Long, currency: String = "₹", withPaise: Boolean = true): String = Money.format(paise, currency, withPaise)

// ---------- App bar ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background, scrolledContainerColor = AppTheme.colors.surfaceContainer),
    )
}

// ---------- Buttons ----------

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.m),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            Spacer(Modifier.width(Space.s))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, destructive: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (destructive) AppTheme.colors.error.copy(alpha = 0.5f) else AppTheme.colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (destructive) AppTheme.colors.error else AppTheme.colors.primary),
        contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.m),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Large square-ish quick action used on the dashboard. */
@Composable
fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tone: Tone = Tone.BRAND) {
    val tc = tone.colors()
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, color = AppTheme.colors.surface, border = BorderStroke(1.dp, AppTheme.ext.border)) {
        Column(Modifier.padding(vertical = Space.m, horizontal = Space.s), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(Radius.m)).background(tc.container), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tc.content, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(Space.s))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- Inputs ----------

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    leadingIcon: ImageVector? = null,
    prefix: String? = null,
    suffix: String? = null,
    enabled: Boolean = true,
    password: Boolean = false,
    capitalizeWords: Boolean = false,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        isError = error != null,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        shape = MaterialTheme.shapes.medium,
        leadingIcon = leadingIcon?.let { { Icon(it, null) } },
        prefix = prefix?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
        suffix = suffix?.let { { Text(it, color = AppTheme.ext.textSecondary) } },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(if (visible) R.string.cd_hide_pin else R.string.cd_show_pin),
                    )
                }
            }
        } else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            capitalization = if (capitalizeWords) androidx.compose.ui.text.input.KeyboardCapitalization.Words else androidx.compose.ui.text.input.KeyboardCapitalization.None,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() ?: defaultKeyboardAction(imeAction) }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = AppTheme.colors.outline,
            unfocusedContainerColor = AppTheme.colors.surface,
            focusedContainerColor = AppTheme.colors.surface,
            disabledContainerColor = AppTheme.colors.surfaceContainer,
        ),
    )
}

@Composable
fun AppSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        placeholder = { Text(placeholder, color = AppTheme.ext.textTertiary) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AppTheme.ext.textSecondary) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.cd_clear_search)) } }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(Radius.l),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = AppTheme.colors.primary,
            unfocusedContainerColor = AppTheme.colors.surfaceContainer,
            focusedContainerColor = AppTheme.colors.surface,
        ),
    )
}

@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        options.forEach { o ->
            val isSel = o == selected
            FilterChip(
                selected = isSel,
                onClick = { onSelect(o) },
                label = { Text(label(o), style = MaterialTheme.typography.labelLarge) },
                leadingIcon = if (isSel) { { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp)) } } else null,
                modifier = Modifier.heightIn(min = 40.dp),
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppTheme.colors.primaryContainer,
                    selectedLabelColor = AppTheme.colors.onPrimaryContainer,
                    selectedLeadingIconColor = AppTheme.colors.onPrimaryContainer,
                ),
            )
        }
    }
}

// ---------- Dates ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(initial: LocalDate, maxDate: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val maxMillis = maxDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = maxMillis == null || utcTimeMillis <= maxMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = state) }
}

/** ‹ date › stepper; tapping the date opens the calendar. */
@Composable
fun DateStepper(date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, maxDate: LocalDate? = null, today: LocalDate = LocalDate.now()) {
    var picking by remember { mutableStateOf(false) }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = AppTheme.colors.surface, border = BorderStroke(1.dp, AppTheme.ext.border)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(date.minusDays(1)) }, modifier = Modifier.size(Space.touch)) {
                Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.cd_previous_day))
            }
            Row(
                Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { picking = true }.padding(vertical = Space.s),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp), tint = AppTheme.colors.primary)
                Spacer(Modifier.width(Space.s))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Dates.formatDayShort(date), style = MaterialTheme.typography.titleMedium)
                    val rel = when (date) {
                        today -> stringResource(R.string.label_today)
                        today.minusDays(1) -> stringResource(R.string.label_yesterday)
                        else -> date.year.toString()
                    }
                    Text(rel, style = MaterialTheme.typography.labelSmall, color = AppTheme.ext.textSecondary)
                }
            }
            val canNext = maxDate == null || date < maxDate
            IconButton(onClick = { onChange(date.plusDays(1)) }, enabled = canNext, modifier = Modifier.size(Space.touch)) {
                Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_next_day))
            }
        }
    }
    if (picking) AppDatePickerDialog(date, maxDate, { picking = false }, onChange)
}

@Composable
fun MonthStepper(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier, maxMonth: YearMonth? = null, locked: Boolean = false) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = AppTheme.colors.surface, border = BorderStroke(1.dp, AppTheme.ext.border)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(month.minusMonths(1)) }, modifier = Modifier.size(Space.touch)) {
                Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.cd_previous_month))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (locked) {
                    Icon(Icons.Rounded.Lock, stringResource(R.string.status_locked), Modifier.size(16.dp), tint = AppTheme.ext.warning)
                    Spacer(Modifier.width(6.dp))
                }
                Text(Dates.formatMonth(month), style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { onChange(month.plusMonths(1)) }, enabled = maxMonth == null || month < maxMonth, modifier = Modifier.size(Space.touch)) {
                Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_next_month))
            }
        }
    }
}

@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, maxDate: LocalDate? = null) {
    var picking by remember { mutableStateOf(false) }
    Surface(
        onClick = { picking = true },
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        color = AppTheme.colors.surface,
        border = BorderStroke(1.dp, AppTheme.colors.outline),
    ) {
        Row(Modifier.padding(horizontal = Space.l, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = AppTheme.ext.textSecondary)
                Text(Dates.formatDay(date), style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Rounded.CalendarMonth, null, tint = AppTheme.colors.primary)
        }
    }
    if (picking) AppDatePickerDialog(date, maxDate, { picking = false }, onChange)
}

// ---------- States ----------

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = Space.xxl, vertical = Space.xxxl), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(AppTheme.colors.primaryContainer.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppTheme.colors.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(Space.l))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = AppTheme.ext.textSecondary, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(Space.l))
            TonalButton(action, onAction)
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(Space.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = AppTheme.colors.error, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(Space.m))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(Space.m))
            TonalButton(stringResource(R.string.action_retry), onRetry)
        }
    }
}

/** Skeleton rows with a gentle pulse while data loads. */
@Composable
fun LoadingList(rows: Int = 6, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    val stringLoading = stringResource(R.string.state_loading)
    Column(modifier.fillMaxWidth().padding(Space.screen).semantics { contentDescription = stringLoading }, verticalArrangement = Arrangement.spacedBy(Space.m)) {
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp, 40.dp).clip(RoundedCornerShape(Radius.m)).background(AppTheme.colors.surfaceContainerHigh.copy(alpha = alpha)))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(AppTheme.colors.surfaceContainerHigh.copy(alpha = alpha)))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(AppTheme.colors.surfaceContainerHigh.copy(alpha = alpha)))
                }
            }
        }
    }
}

@Composable
fun InlineNotice(text: String, tone: Tone, modifier: Modifier = Modifier, icon: ImageVector? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    val tc = tone.colors()
    val ic = icon ?: when (tone) {
        Tone.DANGER -> Icons.Rounded.ErrorOutline
        Tone.WARNING, Tone.ACCENT -> Icons.Rounded.Warning
        Tone.SUCCESS -> Icons.Rounded.CheckCircle
        else -> Icons.Rounded.Info
    }
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(tc.container).padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ic, null, tint = tc.content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tc.content, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = tc.content, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun FullScreenLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

// ---------- Dialogs ----------

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = AppTheme.colors.error, contentColor = AppTheme.colors.onError) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** [PinConfirmDialog] when "Ask PIN for important actions" is switched off: one confirmation tap, plus a reason where needed. */
@Composable
private fun PlainConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit,
    requireReason: Boolean,
    reasonLabel: String,
    onConfirmedWithReason: ((String) -> Unit)?,
) {
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val needReason = stringResource(R.string.err_reason_required)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (requireReason) AppTextField(reason, { reason = it; error = null }, reasonLabel, error = error, singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (requireReason && reason.isBlank()) { error = needReason; return@Button }
                    onDismiss()
                    if (onConfirmedWithReason != null) onConfirmedWithReason(reason.trim()) else onConfirmed()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.error, contentColor = AppTheme.colors.onError),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Re-authentication gate for destructive actions: asks for the signed-in user's PIN (unless switched off). */
@Composable
fun PinConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    verify: suspend (String) -> Boolean,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit,
    requireReason: Boolean = false,
    reasonLabel: String = "",
    onConfirmedWithReason: ((String) -> Unit)? = null,
) {
    if (!LocalFeatures.current.pinForActions) {
        PlainConfirmDialog(title, message, confirmLabel, onConfirmed, onDismiss, requireReason, reasonLabel, onConfirmedWithReason)
        return
    }
    var pin by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val wrong = stringResource(R.string.err_wrong_pin)
    val needReason = stringResource(R.string.err_reason_required)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (requireReason) {
                    AppTextField(reason, { reason = it }, reasonLabel, singleLine = false, minLines = 2)
                }
                AppTextField(
                    value = pin,
                    onValueChange = { pin = it; error = null },
                    label = stringResource(R.string.label_your_pin),
                    error = error,
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                    password = true,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pin.isNotEmpty() && !busy,
                onClick = {
                    if (requireReason && reason.isBlank()) { error = needReason; return@Button }
                    busy = true
                    scope.launch {
                        val ok = verify(pin)
                        busy = false
                        if (ok) {
                            onDismiss()
                            if (onConfirmedWithReason != null) onConfirmedWithReason(reason.trim()) else onConfirmed()
                        } else {
                            error = wrong
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.error, contentColor = AppTheme.colors.onError),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ReasonDialog(title: String, label: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { AppTextField(reason, { reason = it }, label, singleLine = false, minLines = 2, imeAction = ImeAction.Done) },
        confirmButton = { Button(enabled = reason.isNotBlank(), onClick = { onConfirm(reason.trim()); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ---------- Data viz ----------

/** Compact bar chart (no axis clutter). The highlighted bar uses the brand color. */
@Composable
fun MiniBarChart(values: List<Long>, modifier: Modifier = Modifier, highlightIndex: Int = values.lastIndex, height: Dp = 72.dp, description: String) {
    val primary = AppTheme.ext.chartPrimary
    val muted = AppTheme.ext.chartMuted
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val gap = 4.dp.toPx()
        val barW = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(2f)
        values.forEachIndexed { i, v ->
            val h = if (v <= 0) 3.dp.toPx() else (size.height * (v.toFloat() / max)).coerceAtLeast(3.dp.toPx())
            val x = i * (barW + gap)
            drawRoundRect(
                color = if (i == highlightIndex) primary else muted,
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
    }
}

@Composable
fun LabeledProgress(progress: Float, label: String, modifier: Modifier = Modifier, tone: Tone = Tone.BRAND) {
    val color = when (tone) {
        Tone.SUCCESS -> AppTheme.ext.success
        Tone.WARNING -> AppTheme.ext.warning
        else -> AppTheme.colors.primary
    }
    Column(modifier) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = color,
            trackColor = AppTheme.colors.surfaceContainerHigh,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
    }
}

@Composable
fun AnimatedSection(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible) { content() }
}

@Composable
fun ProvideSecondaryText(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides AppTheme.ext.textSecondary, content = content)
}

@Composable
fun SmallLabel(text: String, modifier: Modifier = Modifier, color: Color = AppTheme.ext.textSecondary, style: TextStyle = MaterialTheme.typography.bodySmall) {
    Text(text, modifier = modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
