package com.example.rise.data.auth

import com.example.rise.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DefaultTelegramAuthRepository(
    private val client: OkHttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TelegramAuthRepository {

    override suspend fun exchange(authData: TelegramAuthData): TelegramAuthResponse = withContext(dispatcher) {
        val endpoint = BuildConfig.TELEGRAM_AUTH_ENDPOINT
        require(endpoint.isNotBlank()) { "Telegram auth endpoint is not configured" }

        val payload = JSONObject()
        for ((key, value) in authData.toMap()) {
            if (value != null) {
                payload.put(key, value)
            } else {
                payload.put(key, JSONObject.NULL)
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Telegram auth exchange failed with code ${response.code}: ${body.take(200)}"
                )
            }
            if (body.isBlank()) {
                throw IllegalStateException("Telegram auth exchange returned empty body")
            }
            val json = JSONObject(body)
            val token = json.optString("customToken", json.optString("token"))
            if (token.isBlank()) {
                throw IllegalStateException("Telegram auth exchange returned no custom token")
            }
            val displayName = json.optString("displayName").ifBlank { null }
            val photoUrl = json.optString("photoUrl").ifBlank { null }
            TelegramAuthResponse(
                customToken = token,
                displayName = displayName,
                photoUrl = photoUrl,
            )
        }
    }
}
