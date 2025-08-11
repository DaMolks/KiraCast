package com.kiracast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiracast.data.ani.AniListApi
import com.kiracast.data.ani.AiringItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.*

class AiringViewModel : ViewModel() {

    private val repo = AniListApi.create(enableLogs = false)

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    sealed interface UiState {
        object Loading : UiState
        data class Ok(val items: List<AiringItem>) : UiState
        data class Error(val message: String) : UiState
    }

    fun loadToday(zone: ZoneId = ZoneId.systemDefault()) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val now = ZonedDateTime.now(zone)
                val start = now.toLocalDate().atStartOfDay(zone).toEpochSecond().toInt()
                val end = now.toLocalDate().plusDays(1).atStartOfDay(zone).toEpochSecond().toInt()
                val items = repo.fetchAiringBetween(start, end).sortedBy { it.whenEpochSec }
                _state.value = UiState.Ok(items)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }
}
