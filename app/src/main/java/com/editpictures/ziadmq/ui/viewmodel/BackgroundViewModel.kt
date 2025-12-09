package com.editpictures.ziadmq.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.editpictures.ziadmq.data.ImageProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BgViewModel(private val processor: ImageProcessor) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val bitmap: Bitmap) : UiState()
        data class Error(val message: String) : UiState()
    }

    fun startRemove(bg: Bitmap) {
        viewModelScope.launch {
            _state.value = UiState.Loading

            try {
                // This calls the new ML Kit processor
                val result = processor.removeBackground(bg)
                _state.value = UiState.Success(result)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Unknown Error")
            }
        }
    }
}