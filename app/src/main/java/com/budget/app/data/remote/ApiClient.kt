package com.budget.app.data.remote

import com.budget.app.BuildConfig
import com.budget.app.data.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The server answered with an error. [code] is the API's stable error code. */
class ApiException(val status: Int, val code: String, message: String) : Exception(message)

/**
 * Talks to /api/v1. The device token is attached only to requests built here
 * against the configured server, so it can never be sent anywhere else.
 * A network failure surfaces as [IOException]; an answer the server refused as
 * [ApiException].
 */
class ApiClient(
    private val serverProvider: () -> String?,
    private val tokenProvider: () -> String?,
) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ------------------------------------------------------------ sign-in

    suspend fun meta(server: String): Meta = decode(send("GET", "/api/v1/meta", server = server, auth = false))

    suspend fun login(server: String, username: String, password: String): LoginResponse {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }.toString()
        return decode(send("POST", "/api/v1/auth/login", body = body, server = server, auth = false))
    }

    suspend fun enroll(server: String, proof: String, code: String, name: String): EnrollResponse {
        val body = buildJsonObject {
            put("proof", proof)
            put("code", code)
            put("name", name)
        }.toString()
        return decode(send("POST", "/api/v1/auth/enroll", body = body, server = server, auth = false))
    }

    /** The second person's account from an invite code, and this phone's token. */
    suspend fun join(server: String, code: String, username: String, password: String, name: String): JoinResponse {
        val body = buildJsonObject {
            put("code", code)
            put("username", username)
            put("password", password)
            put("name", name)
        }.toString()
        return decode(send("POST", "/api/v1/auth/join", body = body, server = server, auth = false))
    }

    suspend fun me(): MeResponse = decode(send("GET", "/api/v1/auth/me"))

    suspend fun refresh(): RefreshResponse = decode(send("POST", "/api/v1/auth/refresh", body = "{}"))

    suspend fun logout() {
        send("POST", "/api/v1/auth/logout", body = "{}")
    }

    // ------------------------------------------------------------ data

    suspend fun month(year: Int, month: Int): MonthView = decode(send("GET", "/api/v1/months/$year/$month"))

    suspend fun projection(horizonDays: Int? = null): Projection = decode(
        send("GET", "/api/v1/projection", query = horizonDays?.let { mapOf("horizonDays" to it.toString()) } ?: emptyMap()),
    )

    suspend fun recordActual(
        year: Int,
        month: Int,
        lineItemId: String,
        amountCents: Long,
        paidOn: String?,
        enteredAt: String,
        idempotencyKey: String,
    ) {
        val body = buildJsonObject {
            put("lineItemId", lineItemId)
            put("amount", Money.toDecimal(amountCents))
            if (paidOn != null) put("paidOn", paidOn)
            put("enteredAt", enteredAt)
        }.toString()
        send("PUT", "/api/v1/months/$year/$month/actuals", body = body, idempotencyKey = idempotencyKey)
    }

    suspend fun clearActual(year: Int, month: Int, lineItemId: String, enteredAt: String, idempotencyKey: String) {
        val base = (serverProvider() ?: throw IOException("No server")).toHttpUrlOrNull()
            ?: throw IOException("Bad server address")
        val url = base.newBuilder()
            .addPathSegments("api/v1/months/$year/$month/actuals")
            .addQueryParameter("lineItemId", lineItemId)
            .addQueryParameter("enteredAt", enteredAt)
            .build()
        execute(Request.Builder().url(url).delete(), auth = true, idempotencyKey = idempotencyKey)
    }

    suspend fun setBalance(year: Int, month: Int, amountCents: Long, enteredAt: String, idempotencyKey: String) {
        val body = buildJsonObject {
            put("amount", Money.toDecimal(amountCents))
            put("enteredAt", enteredAt)
        }.toString()
        send("PUT", "/api/v1/months/$year/$month/balance", body = body, idempotencyKey = idempotencyKey)
    }

    // ------------------------------------------------------------ bank feed (server 0.27+)

    suspend fun cards(): CardsResponse = decode(send("GET", "/api/v1/cards"))

    suspend fun review(): ReviewResponse = decode(send("GET", "/api/v1/review"))

    /** [month] is YYYY-MM; the month in progress when null. */
    suspend fun insights(month: String? = null): InsightsResponse =
        decode(send("GET", "/api/v1/insights" + (month?.let { "?m=$it" } ?: "")))

    /** One category up close; [id] is a category id or "none". */
    suspend fun categoryDetail(id: String, month: String?): CategoryDetailResponse =
        decode(send("GET", "/api/v1/insights/category?id=" + java.net.URLEncoder.encode(id, "UTF-8") + (month?.let { "&m=$it" } ?: "")))

    /** The confirm step for a charge that looks like a bill payment. */
    suspend fun confirmReview(item: LooksPaidDto, amountCents: Long, paidOn: String, idempotencyKey: String) {
        val body = buildJsonObject {
            put("lineItemId", item.lineItemId)
            put("year", item.year)
            put("month", item.month)
            put("txnId", item.txnId)
            put("amount", Money.toDecimal(amountCents))
            put("paidOn", paidOn)
        }.toString()
        send("POST", "/api/v1/review/confirm", body = body, idempotencyKey = idempotencyKey)
    }

    suspend fun dismissReview(item: LooksPaidDto) {
        val body = buildJsonObject {
            put("lineItemId", item.lineItemId)
            put("txnId", item.txnId)
        }.toString()
        send("POST", "/api/v1/review/dismiss", body = body)
    }

    suspend fun undoAuto(item: AutoRecordedDto) {
        val body = buildJsonObject {
            put("lineItemId", item.lineItemId)
            put("year", item.year)
            put("month", item.month)
        }.toString()
        send("POST", "/api/v1/review/undo-auto", body = body)
    }

    /** Spending envelopes for the pay period in progress (server 0.40+). */
    suspend fun envelopes(): EnvelopesResponse = decode(send("GET", "/api/v1/envelopes"))

    /** A repeating charge: [action] is "add" (make it a monthly bill) or "dismiss". */
    suspend fun repeat(key: String, action: String) {
        val body = buildJsonObject {
            put("key", key)
            put("action", action)
        }.toString()
        send("POST", "/api/v1/review/repeat", body = body)
    }

    /** Asks the server to read the bank now (skipped when it just did). */
    suspend fun syncBank(): BankSyncResponse = decode(send("POST", "/api/v1/bank/sync", body = "{}"))

    // ------------------------------------------------------------ plumbing

    private inline fun <reified T> decode(text: String): T = json.decodeFromString(text)

    private suspend fun send(
        method: String,
        path: String,
        body: String? = null,
        server: String? = null,
        auth: Boolean = true,
        idempotencyKey: String? = null,
        query: Map<String, String> = emptyMap(),
    ): String {
        val base = (server ?: serverProvider() ?: throw IOException("No server")).toHttpUrlOrNull()
            ?: throw IOException("Bad server address")
        val url = base.newBuilder().encodedPath(path).apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val builder = Request.Builder().url(url)
        val payload = body?.toRequestBody(jsonType)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(payload ?: "{}".toRequestBody(jsonType))
            "PUT" -> builder.put(payload ?: "{}".toRequestBody(jsonType))
            "DELETE" -> builder.delete(payload)
            else -> throw IllegalArgumentException(method)
        }
        return execute(builder, auth, idempotencyKey)
    }

    private suspend fun execute(builder: Request.Builder, auth: Boolean, idempotencyKey: String?): String =
        withContext(Dispatchers.IO) {
            builder.header("Accept", "application/json")
            builder.header("User-Agent", "Budget-Android/${BuildConfig.VERSION_NAME}")
            if (auth) {
                val token = tokenProvider() ?: throw ApiException(401, "missing_bearer", "Not signed in")
                builder.header("Authorization", "Bearer $token")
            }
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey)

            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) return@withContext text
                val detail = runCatching { json.decodeFromString<ErrorBody>(text).error }.getOrNull()
                throw ApiException(
                    status = response.code,
                    code = detail?.code?.ifEmpty { null } ?: "http_${response.code}",
                    message = detail?.message?.ifEmpty { null } ?: "The server answered ${response.code}",
                )
            }
        }
}
