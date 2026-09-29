package de.ilianp.audiotranskript

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Exercises [SummaryClient] against a [MockWebServer], offline and without an OpenRouter key:
 * the request shape (model, privacy routing, the transcript handed over as data), batches, and
 * the error shapes OpenRouter uses.
 */
class SummaryClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        SummaryClient.baseUrl = server.url("/api/v1").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // Answered as plain JSON: what OpenRouter sends for errors, and what a provider ignoring
    // `stream` would send - both are read in one piece.
    @Test
    fun `summarize posts the transcript to the chat endpoint and returns the answer`() = runTest {
        server.enqueue(jsonResponse(200, completion("  • Treffen morgen um 10  ")))

        val summary = SummaryClient.summarize(listOf("Hallo, wir treffen uns morgen um 10."), "key-1")

        assertEquals("• Treffen morgen um 10", summary)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/chat/completions", request.path)
        assertEquals("Bearer key-1", request.getHeader("Authorization"))

        val body = JSONObject(request.body.readUtf8())
        assertEquals("deepseek/deepseek-v4.1-flash", body.getString("model"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        val user = messages.getJSONObject(1)
        assertEquals("user", user.getString("role"))
        assertTrue(user.getString("content").contains("Hallo, wir treffen uns morgen um 10."))
    }

    @Test
    fun `summarize only routes to providers that neither train on nor keep the text`() = runTest {
        server.enqueue(jsonResponse(200, completion("• ok")))

        SummaryClient.summarize(listOf("Hallo"), "key-1")

        val provider = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("provider")
        assertEquals("deny", provider.getString("data_collection"))
        assertTrue(provider.getBoolean("zdr"))
    }

    @Test
    fun `summarize asks for no reasoning, a stream and the cheapest fast provider`() = runTest {
        server.enqueue(sseResponse("• ok"))

        SummaryClient.summarize(listOf("Hallo"), "key-1")

        val body = JSONObject(server.takeRequest().body.readUtf8())
        // Measured: reasoning cost seconds before the first word, for no better summary.
        assertEquals(false, body.getJSONObject("reasoning").getBoolean("enabled"))
        assertTrue(body.getBoolean("stream"))
        val provider = body.getJSONObject("provider")
        assertEquals("price", provider.getString("sort"))
        assertEquals(1.5, provider.getJSONObject("preferred_max_latency").getDouble("p50"), 0.0)
        assertEquals(80, provider.getJSONObject("preferred_min_throughput").getInt("p50"))
    }

    @Test
    fun `a streamed summary is handed out piece by piece and returned whole`() = runTest {
        server.enqueue(sseResponse("\n", "• Treffen ", "morgen um 10\n", "• Kuchen mitbringen"))
        val partials = mutableListOf<String>()

        val summary = SummaryClient.summarize(listOf("Hallo"), "key-1") { partials += it }

        assertEquals("• Treffen morgen um 10\n• Kuchen mitbringen", summary)
        // The leading line break alone shows nothing, so it is not handed out as a partial.
        assertEquals(
            listOf("• Treffen ", "• Treffen morgen um 10\n", "• Treffen morgen um 10\n• Kuchen mitbringen"),
            partials,
        )
    }

    @Test
    fun `an error inside the stream is surfaced`() = runTest {
        val body = "data: " + JSONObject().put("choices", org.json.JSONArray().put(
            JSONObject().put("delta", JSONObject().put("content", "• Anfang")),
        )) + "\n\n" +
            "data: {\"error\":{\"message\":\"Provider disconnected\",\"code\":502}}\n\n"
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(body),
        )

        val e = runCatching { SummaryClient.summarize(listOf("Hallo"), "key-1") }.exceptionOrNull()

        assertTrue(e is WizperException)
        assertEquals("Provider disconnected", e!!.message)
    }

    @Test
    fun `cancelling cuts a stream that is still waiting for its next line`() = runBlocking {
        // Headers, then nothing for a while - a model that stalls. Kept short enough for the
        // server to wind down in tearDown; without the fix, cancelling waits all of it out.
        server.enqueue(sseResponse("• Anfang").setBodyDelay(3, TimeUnit.SECONDS))

        val job = launch(Dispatchers.Default) {
            SummaryClient.summarize(listOf("Hallo"), "key-1")
        }
        delay(300)
        val took = kotlin.system.measureTimeMillis {
            withTimeout(5_000) { job.cancelAndJoin() }
        }

        assertTrue("Abbrechen hat $took ms gedauert", took < 1_000)
    }

    @Test
    fun `an empty stream fails instead of showing an empty summary`() = runTest {
        server.enqueue(sseResponse("  ", "\n"))

        val e = runCatching { SummaryClient.summarize(listOf("Hallo"), "key-1") }.exceptionOrNull()

        assertTrue(e is WizperException)
    }

    @Test
    fun `a batch goes in with its messages numbered, in order`() {
        val message = SummaryClient.userMessage(listOf("Erste", "Zweite"))

        assertTrue(message.contains("Nachricht 1:\nErste"))
        assertTrue(message.contains("Nachricht 2:\nZweite"))
        assertTrue(message.indexOf("Erste") < message.indexOf("Zweite"))
    }

    @Test
    fun `a single message goes in without numbering`() {
        val message = SummaryClient.userMessage(listOf("Nur eine"))

        assertTrue(message.contains("Nur eine"))
        assertTrue(!message.contains("Nachricht 1"))
    }

    @Test
    fun `summarize surfaces the message from an HTTP error`() = runTest {
        server.enqueue(jsonResponse(402, """{"error":{"message":"Insufficient credits","code":402}}"""))

        val e = runCatching { SummaryClient.summarize(listOf("Hallo"), "key-1") }.exceptionOrNull()

        assertTrue(e is WizperException)
        assertTrue(e!!.message!!.contains("402"))
        assertTrue(e.message!!.contains("Insufficient credits"))
    }

    @Test
    fun `summarize surfaces an error object served with HTTP 200`() = runTest {
        server.enqueue(jsonResponse(200, """{"error":{"message":"No endpoints found","code":404}}"""))

        val e = runCatching { SummaryClient.summarize(listOf("Hallo"), "key-1") }.exceptionOrNull()

        assertTrue(e is WizperException)
        assertEquals("No endpoints found", e!!.message)
    }

    @Test
    fun `summarize fails instead of showing an empty summary`() = runTest {
        server.enqueue(jsonResponse(200, completion("   ")))

        val e = runCatching { SummaryClient.summarize(listOf("Hallo"), "key-1") }.exceptionOrNull()

        assertTrue(e is WizperException)
    }

    @Test
    fun `summarize fails loudly on a non-JSON response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>gateway</html>"))

        val e = runCatching { SummaryClient.summarize(listOf("Hallo"), "key-1") }.exceptionOrNull()

        assertTrue(e is WizperException)
        assertTrue(e!!.message!!.contains("Unerwartete Antwort"))
    }

    private fun completion(content: String) = JSONObject()
        .put(
            "choices",
            org.json.JSONArray().put(
                JSONObject().put("message", JSONObject().put("role", "assistant").put("content", content)),
            ),
        )
        .toString()

    /**
     * An OpenRouter stream: a keep-alive comment first, then one chunk per piece, then [DONE] -
     * the shape `text/event-stream` answers come in.
     */
    private fun sseResponse(vararg pieces: String): MockResponse {
        val body = StringBuilder(": OPENROUTER PROCESSING\n\n")
        pieces.forEach { piece ->
            val chunk = JSONObject().put(
                "choices",
                org.json.JSONArray().put(JSONObject().put("delta", JSONObject().put("content", piece))),
            )
            body.append("data: ").append(chunk).append("\n\n")
        }
        body.append("data: [DONE]\n\n")
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(body.toString())
    }

    private fun jsonResponse(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
