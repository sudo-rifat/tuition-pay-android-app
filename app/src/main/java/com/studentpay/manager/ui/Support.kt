package com.studentpay.manager.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studentpay.manager.App
import com.studentpay.manager.AppContainer
import com.studentpay.manager.domain.Period
import java.time.LocalDate
import java.util.Locale

/** Creates a ViewModel with the app container. Scoped to the current NavBackStackEntry / Activity. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as App
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app.container) } })
}

fun todayPeriod(): Period {
    val d = LocalDate.now()
    return Period(d.year, d.monthValue - 1)
}

/** Taka amount for the UI, e.g. "৳12,500". */
fun formatTaka(amount: Long): String = "\u09F3" + String.format(Locale.US, "%,d", amount)

/** Period like "Mar 2026". */
fun com.studentpay.manager.domain.Period.shortLabel(): String =
    com.studentpay.manager.domain.Months.SHORT[month] + " " + year

fun com.studentpay.manager.domain.Period.fullLabel(): String =
    com.studentpay.manager.domain.Months.FULL[month] + " " + year

private val dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
fun formatDate(millis: Long): String =
    dateFmt.format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))
