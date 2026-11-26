package com.studentpay.manager.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.studentpay.manager.R
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.domain.PaymentType
import com.studentpay.manager.domain.Student
import com.studentpay.manager.ui.appViewModel
import com.studentpay.manager.ui.components.AppTopBar
import com.studentpay.manager.ui.components.ConfirmDialog
import com.studentpay.manager.ui.components.EmptyState
import com.studentpay.manager.ui.components.groupedShape
import com.studentpay.manager.ui.formatTaka
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeletePrompt(val student: Student, val paymentCount: Int)

data class StudentsUiState(
    val isLoading: Boolean = true,
    val students: List<Student> = emptyList(),
    val deletePrompt: DeletePrompt? = null,
    val error: Boolean = false,
)

class StudentsViewModel(private val repository: StudentPayRepository) : ViewModel() {
    private val prompt = MutableStateFlow<DeletePrompt?>(null)
    private val error = MutableStateFlow(false)

    val uiState: StateFlow<StudentsUiState> =
        combine(repository.observeStudents(), prompt, error) { list, p, e ->
            StudentsUiState(isLoading = false, students = list, deletePrompt = p, error = e)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentsUiState())

    fun requestDelete(student: Student) {
        viewModelScope.launch {
            prompt.value = DeletePrompt(student, repository.paymentCountFor(student.id))
        }
    }

    fun cancelDelete() { prompt.value = null }

    fun confirmDelete() {
        val p = prompt.value ?: return
        prompt.value = null
        viewModelScope.launch {
            try { repository.deleteStudent(p.student.id) } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = true }
        }
    }

    fun errorShown() { error.value = false }
}

@Composable
fun StudentsScreen(onOpenStudent: (Long) -> Unit, onAddStudent: () -> Unit, onEditStudent: (Long) -> Unit) {
    val vm = appViewModel { StudentsViewModel(it.repository) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val errorText = stringResource(R.string.error_generic)
    LaunchedEffect(state.error) {
        if (state.error) { snackbar.showSnackbar(errorText); vm.errorShown() }
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_students)) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddStudent,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.students_add)) },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.students.isEmpty() -> EmptyState(
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(state.students, key = { _, s -> s.id }) { index, s ->
                    StudentRow(
                        s, index, state.students.size,
                        onOpen = { onOpenStudent(s.id) },
                        onEdit = { onEditStudent(s.id) },
                        onDelete = { vm.requestDelete(s) },
                    )
                }
            }
        }
    }

    state.deletePrompt?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.delete_title, p.student.name),
            text = stringResource(R.string.delete_body, p.paymentCount),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = vm::confirmDelete,
            onDismiss = vm::cancelDelete,
        )
    }
}

@Composable
private fun StudentRow(
    student: Student,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        shape = groupedShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(student.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    stringResource(
                        R.string.students_row_support,
                        stringResource(if (student.type == PaymentType.A) R.string.type_a_short else R.string.type_b_short),
                        formatTaka(student.monthlyFee.toLong()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more, student.name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}
