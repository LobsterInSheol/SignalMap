package com.example.datasender.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.blackholeSink
import okio.buffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.round

class SpeedTestRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),

    private val apimBase: String = "https://apim-inzynierka.azure-api.net/v1/submit",
    private val latencyUrl: String = "$apimBase/speedtest/ping",
    private val uploadUrl: String = "$apimBase/speedtest/upload",

    // adres dużego pliku do testowania pobierania
    private val downloadUrl: String =
        "https://speedtestorage.blob.core.windows.net/speedtest/50MB.bin",
) {

    // dodawanie nagłówków blokujących pamięć podręczną (cache)
    private fun Request.Builder.nocache() = this
        .header("Cache-Control", "no-cache, no-store")
        .header("Pragma", "no-cache")

    // obliczanie mediany z listy wyników
    private fun List<Double>.median(): Double {
        if (isEmpty()) return Double.NaN
        val s = sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }

    // pomiar prędkości pobierania (download)
    suspend fun measureDownloadMbps(
        rounds: Int = 2,
        parallel: Int = 2,
        warmupSeconds: Int = 1,
        measureSeconds: Int = 7,
        rangeChunkBytes: Long = 5L * 1024 * 1024,
        onInstantMbps: ((Double) -> Unit)? = null
    ): Double? = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<Double>()

            repeat(rounds) {
                val total = AtomicLong(0L)
                val started = System.nanoTime()

                coroutineScope {
                    repeat(parallel) { i ->
                        launch {
                            val rangeStart = i * rangeChunkBytes
                            val req = Request.Builder()
                                .url(downloadUrl)
                                .nocache()
                                .header("Range", "bytes=$rangeStart-")
                                .get()
                                .build()

                            client.newCall(req).execute().use { resp ->
                                val src = resp.body?.source() ?: return@use
                                val sink = blackholeSink().buffer()

                                // etap wstępnego rozgrzania połączenia
                                val warmUntil = System.nanoTime() + warmupSeconds * 1_000_000_000L
                                while (System.nanoTime() < warmUntil) {
                                    val r = src.read(sink.buffer, 64 * 1024)
                                    if (r == -1L) return@use
                                }

                                // właściwy pomiar pobierania danych przez określony czas
                                val measureStart = System.nanoTime()
                                var lastEmit = measureStart
                                val endAt = measureStart + measureSeconds * 1_000_000_000L

                                while (System.nanoTime() < endAt) {
                                    val r = src.read(sink.buffer, 64 * 1024)
                                    if (r == -1L) break

                                    val sum = total.addAndGet(r)

                                    // wysyłanie aktualnej prędkości do interfejsu co 200ms
                                    val now = System.nanoTime()
                                    if (onInstantMbps != null && now - lastEmit >= 200_000_000L) {
                                        val elapsed = (now - measureStart) / 1_000_000_000.0
                                        if (elapsed > 0) {
                                            val mbps = (sum * 8) / (elapsed * 1_000_000)
                                            onInstantMbps(mbps)
                                        }
                                        lastEmit = now
                                    }
                                }

                                sink.close()
                            }
                        }
                    }
                }

                // wyliczanie końcowej prędkości dla danej rundy
                val seconds = (System.nanoTime() - started) / 1_000_000_000.0
                val eff = seconds - warmupSeconds
                if (eff <= 0) return@withContext null

                val mbps = (total.get() * 8) / (eff * 1_000_000)
                results += mbps
            }

            val median = results.median()
            if (median.isNaN()) null else round(median * 100) / 100.0
        } catch (_: Exception) {
            null
        }
    }

    // pomiar prędkości wysyłania (upload)
    suspend fun measureUploadMbps(
        bytesPerPart: Int = 2 * 1024 * 1024,
        parts: Int = 4,
        onInstantMbps: ((Double) -> Unit)? = null
    ): Double? = withContext(Dispatchers.IO) {
        val totalAck = AtomicLong(0L)
        val start = System.nanoTime()
        var lastEmit = start

        try {
            supervisorScope {
                repeat(parts) { idx ->
                    launch {
                        try {
                            // generowanie sztucznych danych do wysyłki
                            val data = ByteArray(bytesPerPart) { (it % 251).toByte() }
                            val body = data.toRequestBody("application/octet-stream".toMediaType())

                            val req = Request.Builder()
                                .url(uploadUrl)
                                .nocache()
                                .post(body)
                                .build()

                            client.newCall(req).execute().use { resp ->
                                // odczytanie ile bajtów serwer faktycznie odebrał
                                val hdr = resp.header("X-Bytes-Received")?.toLongOrNull()
                                val echoed = hdr ?: runCatching { resp.body?.string()?.toLong() }.getOrNull()

                                if (resp.isSuccessful && echoed != null && echoed > 0) {
                                    val sum = totalAck.addAndGet(echoed)

                                    // przekazywanie chwilowej prędkości uploadu
                                    val now = System.nanoTime()
                                    if (onInstantMbps != null && now - lastEmit >= 200_000_000L) {
                                        val elapsed = (now - start) / 1_000_000_000.0
                                        if (elapsed > 0) {
                                            val mbps = (sum * 8) / (elapsed * 1_000_000)
                                            onInstantMbps(mbps)
                                        }
                                        lastEmit = now
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("SpeedTest", "upload part#$idx error: ${e.message}")
                        }
                    }
                }
            }

            val seconds = (System.nanoTime() - start) / 1_000_000_000.0
            if (seconds <= 0.0 || totalAck.get() <= 0) return@withContext null

            val mbps = (totalAck.get() * 8) / (seconds * 1_000_000)
            round(mbps * 100) / 100.0
        } catch (_: Exception) {
            null
        }
    }

    data class PingStats(val medianMs: Long?, val jitterMs: Long?)

    // pomiar opóźnień (ping) oraz zmienności opóźnień (jitter)
    suspend fun measurePingStats(
        samples: Int = 20,
        intervalMs: Long = 200L
    ): PingStats = withContext(Dispatchers.IO) {
        val rtts = mutableListOf<Long>()

        repeat(samples) {
            try {
                val req = Request.Builder()
                    .url("$latencyUrl?t=${System.nanoTime()}")
                    .nocache()
                    .get()
                    .build()

                val t0 = System.nanoTime()
                client.newCall(req).execute().use { }
                val ms = (System.nanoTime() - t0) / 1_000_000
                rtts += ms
            } catch (_: Exception) {
                // zignorowanie błędnej próby ping
            }

            delay(intervalMs)
        }

        if (rtts.isEmpty()) return@withContext PingStats(null, null)

        rtts.sort()

        // wyznaczanie mediany czasu odpowiedzi
        val median = if (rtts.size % 2 == 1) {
            rtts[rtts.size / 2].toDouble()
        } else {
            (rtts[rtts.size / 2 - 1] + rtts[rtts.size / 2]) / 2.0
        }

        // obliczanie percentyla dla potrzeb wyliczenia jittera
        fun percentile(p: Double): Double {
            val idx = (p * (rtts.size - 1)).coerceIn(0.0, (rtts.size - 1).toDouble())
            val i = idx.toInt()
            val frac = idx - i
            return if (i + 1 < rtts.size) {
                rtts[i] + frac * (rtts[i + 1] - rtts[i])
            } else {
                rtts[i].toDouble()
            }
        }

        val p90 = percentile(0.90)
        val jitter = (p90 - median).coerceAtLeast(0.0)

        PingStats(median.toLong(), jitter.toLong())
    }
}