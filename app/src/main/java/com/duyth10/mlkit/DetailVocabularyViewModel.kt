package com.duyth10.mlkit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.duyth10.mlkit.Repository.APIRepository
import kotlinx.coroutines.launch
import com.duyth10.mlkit.model.DictionaryResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DetailVocabularyViewModel  @Inject constructor(private val repository: APIRepository) : ViewModel() {

    private val _wordDefinition = MutableLiveData<Resource<List<DictionaryResponse>>>()
    val wordDefinition: LiveData<Resource<List<DictionaryResponse>>> = _wordDefinition

    fun fetchWordDefinition(word: String) {
        _wordDefinition.value = Resource.loading(null)

        viewModelScope.launch {
            val result = repository.getWordDefinition(word)
            _wordDefinition.value = result
            Log.d("DetailVocabularyViewModel", "Success: $result")

        }
    }
}
