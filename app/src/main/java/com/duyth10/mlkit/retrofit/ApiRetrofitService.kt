package com.duyth10.mlkit.retrofit

import com.duyth10.mlkit.model.DictionaryResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ApiRetrofitService {

    // can do take sound pronun of vocabulary
    // https://api.dictionaryapi.dev/media/pronunciations/en/if-us.mp3
    //  thôn tin ấy ra từ api van chua ki neu ma cai tu day no co nhieu thong tin hon


    @GET("api/v2/entries/en/{word}")
    suspend fun getWordDefinition(
        @Path("word") word: String
    ): Response<List<DictionaryResponse>>


}