package app.hubhelper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import androidx.compose.material3.*
import kotlinx.coroutines.*

/** Operations survive activity recreation, publish failures, and reject repeated taps while saving. */
class AppOperations : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    fun dismissError() { error = null }
    fun launch(block: suspend CoroutineScope.() -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "The operation failed. Your unsaved input is still available." }
            finally { busy = false }
        }
    }
}

@Composable
fun OperationStatus(operations: AppOperations) {
    if (operations.busy) AlertDialog(onDismissRequest = {}, title = { Text("Saving…") },
        text = { LinearProgressIndicator() }, confirmButton = {})
    operations.error?.let { message -> AlertDialog(onDismissRequest = operations::dismissError,
        title = { Text("Could not complete the operation") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = operations::dismissError) { Text("Return and retry") } }) }
}
