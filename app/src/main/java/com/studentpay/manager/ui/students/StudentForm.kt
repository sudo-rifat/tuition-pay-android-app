package com.studentpay.manager.ui.students

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.studentpay.manager.R
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.domain.FeeError
import com.studentpay.manager.domain.NameError
import com.studentpay.manager.domain.PaymentType
import com.studentpay.manager.domain.Student
import com.studentpay.manager.domain.StudentValidator
import com.studentpay.manager.ui.appViewModel
import com.studentpay.manager.ui.components.AppTopBar
import com.studentpay.manager.ui.components.ConfirmDialog
import com.studentpay.manager.ui.components.EmptyState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FormLoad {
    data object Loading : FormLoad
    /** [student] is null when adding a new one. */
    data class Ready(val student: Student?) : FormLoad
    data object NotFound : FormLoad
}

class StudentFormViewModel(private val repository: StudentPayRepository, private val studentId: Long) : ViewModel() {
    private val _load = MutableStateFlow<FormLoad>(FormLoad.Loading)
    val load: StateFlow<FormLoad> = _load.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    init {
        viewModelScope.launch {
            _load.value = if (studentId == 0L) FormLoad.Ready(null)
            else repository.getStudent(studentId)?.takeIf { !it.isDeleted }?.let { FormLoad.Ready(it) } ?: FormLoad.NotFound
        }
    }

    fun save(name: String, type: PaymentType, fee: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                if (studentId == 0L) repository.addStudent(name, type, fee)
                else repository.updateStudent(studentId, name, type, fee)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _failed.value = true
            }
        }
    }
}

@Composable
fun StudentFormScreen(studentId: Long, onDone: () -> Unit) {
    val vm = appViewModel(key = "form-$studentId") { StudentFormViewModel(it.repository, studentId) }
    val load by vm.load.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val isNew = studentId == 0L

    when (val l = load) {
        FormLoad.Loading -> Scaffold(topBar = { AppTopBar(stringResource(R.string.form_title_edit), onBack = onDone) }) { p ->
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        FormLoad.NotFound -> Scaffold(topBar = { AppTopBar(stringResource(R.string.form_title_edit), onBack = onDone) }) { p ->
            EmptyState(
                title = stringResource(R.string.student_not_found),
                body = stringResource(R.string.student_not_found_body),
                modifier = Modifier.padding(p),
            )
        }
        is FormLoad.Ready -> FormContent(
            title = stringResource(if (isNew) R.string.form_title_add else R.string.form_title_edit),
            initial = l.student,
            failed = failed,
            onSave = { name, type, fee -> vm.save(name, type, fee, onDone) },
            onClose = onDone,
        )
    }
}

@Composable
private fun FormContent(
    title: String,
    initial: Student?,
    failed: Boolean,
    onSave: (String, PaymentType, Int) -> Unit,
    onClose: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var fee by rememberSaveable { mutableStateOf(initial?.monthlyFee?.toString().orEmpty()) }
    var typeCode by rememberSaveable { mutableStateOf((initial?.type ?: PaymentType.A).code) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val type = PaymentType.fromCode(typeCode) ?: PaymentType.A
    val dirty = name != initial?.name.orEmpty() ||
        fee != initial?.monthlyFee?.toString().orEmpty() ||
        type != (initial?.type ?: PaymentType.A)
    val result = StudentValidator.validate(name, fee)

    fun close() {
        if (dirty && !saving) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(enabled = dirty) { close() }

    Scaffold(topBar = { AppTopBar(title, onBack = { close() }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(StudentValidator.MAX_NAME_LENGTH + 20) },
                label = { Text(stringResource(R.string.form_name)) },
                singleLine = true,
                isError = submitted && result.nameError != null,
                supportingText = {
                    if (submitted) when (result.nameError) {
                        NameError.EMPTY -> Text(stringResource(R.string.form_name_empty))
                        NameError.TOO_LONG -> Text(stringResource(R.string.form_name_long, StudentValidator.MAX_NAME_LENGTH))
                        null -> Unit
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = fee,
                onValueChange = { v -> fee = v.filter { it in '0'..'9' }.take(9) },
                label = { Text(stringResource(R.string.form_fee)) },
                prefix = { Text("\u09F3") },
                singleLine = true,
                isError = submitted && result.feeError != null,
                supportingText = {
                    if (submitted) when (result.feeError) {
                        FeeError.EMPTY, FeeError.NOT_A_NUMBER -> Text(stringResource(R.string.form_fee_invalid))
                        FeeError.TOO_LARGE -> Text(stringResource(R.string.form_fee_large, StudentValidator.MAX_FEE))
                        null -> Unit
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            if (initial != null) {
                Text(
                    stringResource(R.string.form_fee_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.form_type), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            TypeOption(
                selected = type == PaymentType.A,
                title = stringResource(R.string.type_a_title),
                description = stringResource(R.string.type_a_desc),
                onSelect = { typeCode = PaymentType.A.code },
            )
            TypeOption(
                selected = type == PaymentType.B,
                title = stringResource(R.string.type_b_title),
                description = stringResource(R.string.type_b_desc),
                onSelect = { typeCode = PaymentType.B.code },
            )

            if (failed) {
                Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    submitted = true
                    val n = result.name
                    val f = result.fee
                    if (result.isValid && n != null && f != null && !saving) {
                        saving = true
                        onSave(n, type, f)
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_body),
            confirmLabel = stringResource(R.string.action_discard),
            destructive = true,
            onConfirm = { confirmDiscard = false; onClose() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun TypeOption(selected: Boolean, title: String, description: String, onSelect: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
