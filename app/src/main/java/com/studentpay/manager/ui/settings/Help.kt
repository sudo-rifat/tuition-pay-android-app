package com.studentpay.manager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studentpay.manager.R
import com.studentpay.manager.ui.components.AppTopBar

/** The legacy app had a Bengali user manual; it is kept (updated for the native app) with an English version. */
private val helpBn = listOf(
    R.string.help_bn_1_title to R.string.help_bn_1_body,
    R.string.help_bn_2_title to R.string.help_bn_2_body,
    R.string.help_bn_3_title to R.string.help_bn_3_body,
    R.string.help_bn_4_title to R.string.help_bn_4_body,
    R.string.help_bn_5_title to R.string.help_bn_5_body,
    R.string.help_bn_6_title to R.string.help_bn_6_body,
    R.string.help_bn_7_title to R.string.help_bn_7_body,
)
private val helpEn = listOf(
    R.string.help_en_1_title to R.string.help_en_1_body,
    R.string.help_en_2_title to R.string.help_en_2_body,
    R.string.help_en_3_title to R.string.help_en_3_body,
    R.string.help_en_4_title to R.string.help_en_4_body,
    R.string.help_en_5_title to R.string.help_en_5_body,
    R.string.help_en_6_title to R.string.help_en_6_body,
    R.string.help_en_7_title to R.string.help_en_7_body,
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    var bengali by rememberSaveable { mutableStateOf(true) }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.settings_help), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(bengali, { bengali = true }, { Text(stringResource(R.string.help_lang_bn)) })
                FilterChip(!bengali, { bengali = false }, { Text(stringResource(R.string.help_lang_en)) })
            }
            (if (bengali) helpBn else helpEn).forEach { (title, body) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
