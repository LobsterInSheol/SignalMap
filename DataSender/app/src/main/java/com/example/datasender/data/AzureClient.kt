package com.example.datasender.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object AzureClient {

    private const val TAG = "AzureClient"

    // Bazowy adres APIM. Poszczególne endpointy są składane poniżej.
    private const val APIM_BASE = "https://apim-inzynierka.azure-api.net/v1"
    private const val APIM_URL = "$APIM_BASE/submit"
    private const val APIM_SPEEDTEST_URL = "$APIM_BASE/submit/speedtest"
    private const val BROKER_URL = "$APIM_BASE/submit/gettoken"
    private const val REGISTER_URL = "$APIM_BASE/submit/register"

    // Jeśli APIM wymaga subskrypcji (product subscription), tutaj można wpisać klucz.
    // Gdy null, nagłówek nie jest dodawany.
    val SUBSCRIPTION_KEY: String? = null

    // Wspólny klient HTTP z timeoutami (żeby żądania nie wisiały w nieskończoność).
    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Cache tokenu w pamięci procesu: ogranicza liczbę wywołań do brokera /gettoken.
    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpEpochSec: Long = 0L

    /**
     * Rejestruje urządzenie w backendzie.
     * Wysyła androidId i oczekuje shortCode w odpowiedzi.
     *
     * @throws IOException gdy backend zwróci kod != 2xx
     */
    suspend fun register(context: Context): String = withContext(Dispatchers.IO) {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val body = JSONObject()
            .apply { put("androidId", androidId) }
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val req = Request.Builder()
            .url(REGISTER_URL)
            .post(body)
            .addHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .build()

        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("register HTTP ${resp.code}: $txt")

            // Backend ma zwrócić {"shortCode": "..."}
            JSONObject(txt).getString("shortCode")
        }
    }

    private fun nowEpochSec(): Long = System.currentTimeMillis() / 1000

    /**
     * Sprawdza czy token w cache jest jeszcze ważny.
     * Odejmujemy 30 sekund jako bufor bezpieczeństwa na opóźnienia i różnice czasu.
     */
    private fun isTokenValid(): Boolean =
        !cachedToken.isNullOrBlank() && nowEpochSec() < (tokenExpEpochSec - 30)

    /**
     * Pobiera token dostępu z brokera (/gettoken).
     * To jest wariant blokujący (OkHttp execute), więc wywołujemy go tylko w wątku IO.
     *
     * Oczekiwany format odpowiedzi:
     * - access_token: String
     * - expires_at: Long (epoch seconds)
     *
     * @throws IOException gdy backend zwróci błąd lub payload jest niepoprawny
     */
    @Throws(IOException::class)
    private fun fetchTokenBlocking(shortCode: String): Pair<String, Long> {
        val req = Request.Builder()
            .url(BROKER_URL)
            // Dodano wymagany nagłówek identyfikujący klienta przed wydaniem tokena
            .addHeader("X-Short-Code", shortCode)
            .build()

        client.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string()
            if (!resp.isSuccessful) throw IOException("gettoken HTTP ${resp.code}: $bodyStr")

            val obj = JSONObject(bodyStr ?: "{}")
            val token = obj.optString("access_token", "")
            val exp = obj.optLong("expires_at", 0L)

            if (token.isBlank() || exp <= 0L) throw IOException("gettoken invalid payload")
            return token to exp
        }
    }

    /**
     * Zwraca ważny token:
     * - jeśli cache jest aktualny -> zwraca z pamięci,
     * - jeśli nie -> pobiera nowy z brokera i aktualizuje cache.
     */
    private suspend fun getAccessToken(shortCode: String): String = withContext(Dispatchers.IO) {
        if (isTokenValid()) return@withContext cachedToken!!

        val (tok, exp) = fetchTokenBlocking(shortCode)
        cachedToken = tok
        tokenExpEpochSec = exp
        tok
    }

    /**
     * Wysyła standardowy payload do endpointu /submit.
     * Zwraca komunikat tekstowy (UI może to pokazać bez dodatkowej mapy kodów).
     */
    suspend fun sendToAzure(json: JSONObject): String = postJsonWithRetry(
        url = APIM_URL,
        json = json,
        logPrefix = "APIM",
        okMsg = "Dane wysłane",
        okRetryMsg = "Dane wysłane (retry)",
        errPrefix = "Błąd"
    )

    /**
     * Wysyła wynik speedtestu do endpointu /submit/speedtest.
     */
    suspend fun sendSpeedTest(json: JSONObject): String = postJsonWithRetry(
        url = APIM_SPEEDTEST_URL,
        json = json,
        logPrefix = "APIM SPEEDTEST",
        okMsg = "Speedtest wysłany",
        okRetryMsg = "Speedtest wysłany (retry)",
        errPrefix = "Błąd speedtest"
    )

    /**
     * NOWA METODA: Kolejkuje wysyłkę w WorkManagerze.
     * Zapewnia odporność na brak zasięgu (pociągi, tunele) poprzez Store-and-Forward.
     */
    fun scheduleDataUpload(context: Context, json: JSONObject, isSpeedTest: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString("payload", json.toString())
            .putString("type", if (isSpeedTest) "speedtest" else "telemetry")
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<DataSendWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS, // Poprawione z OneTimeWorkRequest na WorkRequest
                TimeUnit.MILLISECONDS
            )
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueue(uploadRequest)
    }

    /**
     * Wspólna implementacja wysyłki:
     * - wysyła JSON jako POST z nagłówkiem Authorization,
     * - jeśli backend zwróci 401, czyści cache tokenu i próbuje jeszcze raz (dokładnie 1 retry).
     *
     * Zachowanie celowo jest ograniczone do jednego retry, żeby nie zapętlić wysyłki w razie problemów po stronie backendu.
     */
    private suspend fun postJsonWithRetry(
        url: String,
        json: JSONObject,
        logPrefix: String,
        okMsg: String,
        okRetryMsg: String,
        errPrefix: String
    ): String = withContext(Dispatchers.IO) {
        try {
            // Wyciągamy shortCode z JSONa, aby móc autoryzować pobranie tokena
            val shortCode = json.optString("shortCode", "")
            if (shortCode.isBlank()) return@withContext "$errPrefix: Brak shortCode"

            var token = getAccessToken(shortCode)

            val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
            val req1 = buildAuthorizedPost(url = url, token = token, body = body)

            client.newCall(req1).execute().use { resp ->
                var responseBody = resp.body?.string()
                Log.d(TAG, "$logPrefix #1 HTTP ${resp.code} body: $responseBody")

                if (resp.code == 401) {
                    // Token odrzucony — pobieramy nowy i robimy jednorazowy retry.
                    cachedToken = null
                    token = getAccessToken(shortCode)

                    val req2 = req1.newBuilder()
                        .header(HEADER_AUTHORIZATION, "Bearer $token")
                        .build()

                    client.newCall(req2).execute().use { resp2 ->
                        responseBody = resp2.body?.string()
                        Log.d(TAG, "$logPrefix #2 HTTP ${resp2.code} body: $responseBody")

                        return@withContext if (resp2.isSuccessful) {
                            okRetryMsg
                        } else {
                            "$errPrefix: ${resp2.code} ${resp2.message} ${responseBody ?: ""}"
                        }
                    }
                }

                return@withContext if (resp.isSuccessful) {
                    okMsg
                } else {
                    "$errPrefix: ${resp.code} ${resp.message} ${responseBody ?: ""}"
                }
            }
        } catch (e: Exception) {
            // Tu łapiemy wszystko, bo metoda ma zwracać czytelny komunikat zamiast wywalić aplikację.
            Log.e(TAG, "Wyjątek przy wysyłce ($logPrefix)", e)
            return@withContext "Wyjątek: ${e.message}"
        }
    }

    /**
     * Buduje request POST z Authorization i JSON content-type.
     * Jeśli ustawiono SUBSCRIPTION_KEY, dodaje nagłówek wymagany przez APIM.
     */
    private fun buildAuthorizedPost(
        url: String,
        token: String,
        body: RequestBody
    ): Request {
        return Request.Builder()
            .url(url)
            .post(body)
            .addHeader(HEADER_AUTHORIZATION, "Bearer $token")
            .addHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .apply {
                SUBSCRIPTION_KEY?.let { addHeader(HEADER_APIM_SUBSCRIPTION_KEY, it) }
            }
            .build()
    }

    private const val HEADER_AUTHORIZATION = "Authorization"
    private const val HEADER_CONTENT_TYPE = "Content-Type"
    private const val HEADER_APIM_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key"

    private const val CONTENT_TYPE_JSON = "application/json"
    private val JSON_MEDIA_TYPE = CONTENT_TYPE_JSON.toMediaType()
}