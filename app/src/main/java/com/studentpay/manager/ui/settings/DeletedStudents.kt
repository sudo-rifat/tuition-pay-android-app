package com.studentpay.manager.ui.settings

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.studentpay.manager.R
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.domain.DeletedStudentSummary
import com.studentpay.manager.ui.appViewModel
import com.studentpay.manager.ui.components.AppTopBar
import com.studentpay.manager.ui.components.EmptyState
import com.studentpay.manager.ui.components.groupedShape
import com.studentpay.manager.ui.formatDate
import com.studentpay.manager.ui.formatTaka
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeletedUiState(
    val isLoading: Boolean = true,
    val students: List<DeletedStudentSummary> = emptyList(),
    /** Name of the student that was just restored (one-shot notice). */
    val restoredName: String? = null,
    val error: Boolean = false,
)

class DeletedStudentsViewModel(private val repository: StudentPayRepository) : ViewModel() {
    private val restored = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow(false)

    val uiState: StateFlow<DeletedUiState> =
        combine(repository.observeDeletedSummaries(), restored, error) { list, r, e ->
            DeletedUiState(isLoading = false, students = list, restoredName = r, error = e)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeletedUiState())

    fun restore(summary: DeletedStudentSummary) {
        viewModelScope.launch {
            try {
                repository.restoreStudent(summary.student.id)
                restored.value = summary.student.name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = true
            }
        }
    }

    fun noticeShown() {
        restored.value = null
        error.value = false
    }
}

/** Deleted students and what they paid. Their income stays in totals and reports; Restore brings them back. */
@Composable
fun DeletedStudentsScreen(onBack: () -> Unit) {
    val vm = appViewModel { DeletedStudentsViewModel(it.repository) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val restoredText = state.restoredName?.let { stringResource(R.string.deleted_restored, it) }
    val errorText = stringResource(R.string.error_generic)
    LaunchedEffect(state.restoredName, state.error) {
        if (restoredText != null) { snackbar.showSnackbar(restoredText); vm.noticeShown() }
        else if (state.error) { snackbar.showSnackbar(errorText); vm.noticeShown() }
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.settings_deleted), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.students.isEmpty() -> EmptyState(
                title = stringResource(R.string.deleted_empty_title),
                body = stringResource(R.string.deleted_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "info") {
                    Text(
                        stringResource(R.string.deleted_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                itemsIndexed(state.students, key = { _, s -> s.student.id }) { index, s ->
                    DeletedRow(s, index, state.students.size, onRestore = { vm.restore(s) })
                }
            }
        }
    }
}

@Composable
private fun DeletedRow(s: DeletedStudentSummary, index: Int, count: Int, onRestore: () -> Unit) {
    Surface(
        shape = groupedShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.student.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    stringResource(
                        R.string.deleted_row_support,
                        formatDate(s.student.deletedAtMillis ?: 0L),
                        formatTaka(s.totalReceived),
                        s.paymentCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRestore) { Text(stringResource(R.string.deleted_restore)) }
        }
    }
}
