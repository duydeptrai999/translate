package com.duyth10.mlkit

sealed class Resource<out T> {

    data class Success<out T>(val data: T?) : Resource<T>()
    data class Error<out T>(val message: String, val data: T?) : Resource<T>()
    data class Loading<out T>(val data: T?) : Resource<T>()

    companion object {
        fun <T> success(data: T?): Resource<T> = Success(data)
        fun <T> error(msg: String, data: T?): Resource<T> = Error(msg, data)
        fun <T> loading(data: T?): Resource<T> = Loading(data)
    }
}
