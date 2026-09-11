package com.example.museumapp.data.repository

sealed interface RepositoryResult<out T> {
    data class Success<T>(val data: T) : RepositoryResult<T>

    /**
     * [recoverable] is true when the failure does NOT necessarily mean the requested action
     * didn't happen server-side - a read timeout (the request may have been accepted and is
     * still processing) or a 409 conflict (something, possibly our own prior request, is already
     * running). Callers that start background jobs should reconcile with the server's actual
     * state instead of treating these as hard failures or blindly retrying (which could create a
     * duplicate job). A non-recoverable error (validation failure, 404, etc.) means the action
     * genuinely did not happen.
     */
    data class Error(val message: String, val recoverable: Boolean = false) : RepositoryResult<Nothing>
}
