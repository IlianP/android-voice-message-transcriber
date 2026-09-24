package de.ilianp.audiotranskript

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    private fun jsonResponse(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
