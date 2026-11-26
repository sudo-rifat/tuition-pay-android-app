package com.studentpay.manager.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.studentpay.manager.R
import com.studentpay.manager.ui.home.HomeScreen
import com.studentpay.manager.ui.lock.PinSetupScreen
import com.studentpay.manager.ui.lock.UnlockScreen
import com.studentpay.manager.ui.payments.PaymentsScreen
import com.studentpay.manager.ui.reports.PdfPreviewScreen
import com.studentpay.manager.ui.reports.ReportsScreen
import com.studentpay.manager.ui.settings.ChangePinScreen
import com.studentpay.manager.ui.settings.HelpScreen
import com.studentpay.manager.ui.settings.SettingsScreen
import com.studentpay.manager.ui.students.StudentDetailScreen
import com.studentpay.manager.ui.students.StudentFormScreen
import com.studentpay.manager.ui.students.StudentsScreen

object Routes {
    const val HOME = "home"
    const val STUDENTS = "students"
    const val PAYMENTS = "payments"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    const val ARG_STUDENT = "studentId"
    const val ARG_FILE = "fileName"
    const val STUDENT_DETAIL = "student/{$ARG_STUDENT}"
    const val STUDENT_FORM = "student_form/{$ARG_STUDENT}"
    const val PREVIEW = "report_preview/{$ARG_FILE}"
    const val CHANGE_PIN = "change_pin"
    const val HELP = "help"

    fun studentDetail(id: Long) = "student/$id"
    /** id 0 = new student. */
    fun studentForm(id: Long = 0L) = "student_form/$id"
    fun preview(fileName: String) = "report_preview/$fileName"
}

private data class TopLevel(val route: String, val label: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Default.Home),
    TopLevel(Routes.STUDENTS, R.string.nav_students, Icons.Default.Person),
    TopLevel(Routes.PAYMENTS, R.string.nav_payments, Icons.Default.CheckCircle),
    TopLevel(Routes.REPORTS, R.string.nav_reports, Icons.AutoMirrored.Filled.List),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val gate by vm.gate.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (gate) {
            Gate.LOADING -> Unit
            Gate.SETUP -> PinSetupScreen(onPinCreated = vm::createPin)
            Gate.LOCKED -> UnlockScreen(vm)
            Gate.UNLOCKED -> MainScaffold(vm)
        }
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val showBar = topLevel.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { nav.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenStudent = { nav.navigate(Routes.studentDetail(it)) },
                    onAddStudent = { nav.navigate(Routes.studentForm()) },
                )
            }
            composable(Routes.STUDENTS) {
                StudentsScreen(
                    onOpenStudent = { nav.navigate(Routes.studentDetail(it)) },
                    onAddStudent = { nav.navigate(Routes.studentForm()) },
                    onEditStudent = { nav.navigate(Routes.studentForm(it)) },
                )
            }
            composable(Routes.PAYMENTS) {
                PaymentsScreen(onAddStudent = { nav.navigate(Routes.studentForm()) })
            }
            composable(Routes.REPORTS) {
                ReportsScreen(onOpenPreview = { nav.navigate(Routes.preview(it)) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    appVm = vm,
                    onChangePin = { nav.navigate(Routes.CHANGE_PIN) },
                    onHelp = { nav.navigate(Routes.HELP) },
                )
            }
            composable(
                Routes.STUDENT_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_STUDENT) { type = NavType.LongType }),
            ) { e ->
                val id = e.arguments?.getLong(Routes.ARG_STUDENT) ?: 0L
                StudentDetailScreen(
                    studentId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.studentForm(id)) },
                    onPreview = { nav.navigate(Routes.preview(it)) },
                )
            }
            composable(
                Routes.STUDENT_FORM,
                arguments = listOf(navArgument(Routes.ARG_STUDENT) { type = NavType.LongType }),
            ) { e ->
                StudentFormScreen(
                    studentId = e.arguments?.getLong(Routes.ARG_STUDENT) ?: 0L,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                Routes.PREVIEW,
                arguments = listOf(navArgument(Routes.ARG_FILE) { type = NavType.StringType }),
            ) { e ->
                PdfPreviewScreen(
                    fileName = e.arguments?.getString(Routes.ARG_FILE).orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.CHANGE_PIN) { ChangePinScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.HELP) { HelpScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
