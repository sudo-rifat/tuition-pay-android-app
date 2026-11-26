package com.studentpay.manager.ui.reports

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.studentpay.manager.R
import com.studentpay.manager.pdf.PdfPreviewDocument
import com.studentpay.manager.pdf.ReportService
import com.studentpay.manager.ui.appViewModel
import com.studentpay.manager.ui.components.AppTopBar
import com.studentpay.manager.ui.components.EmptyState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PdfPreviewViewModel(private val reports: ReportService, val fileName: String) : ViewModel() {
    /** Only the file name is passed through navigation; it must stay inside the report cache folder. */
    val file: File? = fileName.takeIf { it.matches(Regex("[A-Za-z0-9_]+\\.pdf")) }
        ?.let { reports.fileFor(it) }
        ?.takeIf { it.exists() }

    private val _notice = MutableStateFlow<Boolean?>(null) // true = saved, false = error
    val notice: StateFlow<Boolean?> = _notice.asStateFlow()

    fun saveTo(uri: android.net.Uri) {
        val f = file ?: return
        viewModelScope.launch {
            try {
                reports.saveTo(f, uri)
                _notice.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notice.value = false
            }
        }
    }

    fun noticeShown() { _notice.value = null }
}

@Composable
fun PdfPreviewScreen(fileName: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "preview-$fileName") { PdfPreviewViewModel(it.reports, fileName) }
    val notice by vm.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.saveTo(uri)
    }
    val savedText = stringResource(R.string.reports_saved)
    val errorText = stringResource(R.string.reports_error)
    LaunchedEffect(notice) {
        when (notice) {
            true -> { snackbar.showSnackbar(savedText); vm.noticeShown() }
            false -> { snackbar.showSnackbar(errorText); vm.noticeShown() }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.preview_title),
                onBack = onBack,
                actions = {
                    if (vm.file != null) {
                        TextButton(onClick = { saveLauncher.launch(vm.fileName) }) {
                            Text(stringResource(R.string.reports_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        val file = vm.file
        if (file == null) {
            EmptyState(
                title = stringResource(R.string.preview_missing_title),
                body = stringResource(R.string.preview_missing_body),
                modifier = Modifier.padding(padding),
            )
        } else {
            PreviewPages(file, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun PreviewPages(file: File, modifier: Modifier) {
    val doc = remember(file) { runCatching { PdfPreviewDocument(file) }.getOrNull() }
    DisposableEffect(doc) { onDispose { doc?.close() } }
    if (doc == null) {
        EmptyState(
            title = stringResource(R.string.preview_missing_title),
            body = stringResource(R.string.preview_missing_body),
            modifier = modifier,
        )
        return
    }
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { (maxWidth - 32.dp).roundToPx().coerceAtLeast(1) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(doc.pageCount) { index ->
                val bitmap by produceState<Bitmap?>(null, doc, index, widthPx) { value = doc.render(index, widthPx) }
                Surface(color = Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    val b = bitmap
                    if (b == null) {
                        Box(Modifier.fillMaxWidth().aspectRatio(595f / 842f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Image(
                            bitmap = b.asImageBitmap(),
                            contentDescription = stringResource(R.string.preview_page, index + 1, doc.pageCount),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
