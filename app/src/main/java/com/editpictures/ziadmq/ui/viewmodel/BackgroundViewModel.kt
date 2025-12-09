package com.editpictures.ziadmq.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.editpictures.ziadmq.data.ImageProcessor
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
                val result = processor.removeBackground(bg)
                _state.value = UiState.Success(result)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Error")
            }
        }
    }
}
