package com.duyth10.mlkit.Repository

import android.util.Log
import com.duyth10.mlkit.Resource
import com.duyth10.mlkit.model.DictionaryResponse
import com.duyth10.mlkit.retrofit.RetrofitClient
import javax.inject.Inject

class APIRepository  @Inject constructor() {

        suspend fun getWordDefinition(word: String): Resource<List<DictionaryResponse>> {
            return try {
                val response = RetrofitClient.api.getWordDefinition(word)
                if (response.isSuccessful) {
                        response.body()?.let { dictionaryResponse ->
                        Log.d("APIRepository", "Received Data: $dictionaryResponse")
                         Resource.success((dictionaryResponse))
                    } ?: Resource.error("No data available", null)
                } else {
                    Resource.error("Error: ${response.message()}", null)
                }
            } catch (e: Exception) {
                Resource.error("Exception: ${e.message}", null)
            }
        }

}