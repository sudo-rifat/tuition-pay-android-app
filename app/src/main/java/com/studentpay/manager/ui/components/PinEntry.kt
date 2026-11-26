package com.studentpay.manager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.studentpay.manager.R
import com.studentpay.manager.domain.PinPolicy

/**
 * Four-digit PIN entry with its own numeric keypad, so only digits can ever be entered and no
 * keyboard (with suggestions/clipboard) is involved. Calls [onComplete] as soon as the 4th digit
 * is entered and then clears itself. The PIN is held only in this composable's memory (never saved
 * to instance state).
 */
@Composable
fun PinEntry(
    enabled: Boolean,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var digits by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    val compact = LocalConfiguration.current.screenHeightDp < 640
    val keySize = if (compact) 60.dp else 72.dp

    fun press(d: String) {
        if (!enabled || digits.length >= PinPolicy.LENGTH) return
        val next = digits + d
        digits = next
        if (next.length == PinPolicy.LENGTH) {
            onComplete(next)
            digits = ""
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val entered = stringResource(R.string.pin_digits_entered, digits.length, PinPolicy.LENGTH)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(48.dp).semantics { contentDescription = entered },
        ) {
            for (i in 0 until PinPolicy.LENGTH) {
                val filled = i < digits.length
                Surface(
                    shape = CircleShape,
                    color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = if (filled) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(if (reveal && filled) 40.dp else 16.dp),
                ) {
                    if (reveal && filled) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                digits[i].toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (d in row) Key(d, keySize, enabled) { press(d) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Key(
                    label = stringResource(if (reveal) R.string.pin_hide else R.string.pin_show),
                    size = keySize,
                    enabled = true,
                    small = true,
                    plain = true,
                ) { reveal = !reveal }
                Key("0", keySize, enabled) { press("0") }
                Key(
                    label = "\u232B",
                    size = keySize,
                    enabled = enabled,
                    plain = true,
                    description = stringResource(R.string.pin_delete),
                ) { if (digits.isNotEmpty()) digits = digits.dropLast(1) }
            }
        }
    }
}

@Composable
private fun Key(
    label: String,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    small: Boolean = false,
    plain: Boolean = false,
    description: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (plain) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(size).semantics { description?.let { contentDescription = it } },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = if (small) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}
