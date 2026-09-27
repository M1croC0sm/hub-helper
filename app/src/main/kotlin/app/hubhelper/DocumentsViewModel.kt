package app.hubhelper

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import app.hubhelper.data.*
import kotlinx.coroutines.*

class DocumentsViewModel(application: Application) : AndroidViewModel(application) {
    var results by mutableStateOf<List<DocumentPageEntity>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null
    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(200)
            val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            try {
                results = if (terms.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
                    HubHelperDatabase.get(getApplication()).documentPageDao().search(terms.joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"" })
                }
                error = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = "Search unavailable: ${failure.message}" }
        }
    }
}
