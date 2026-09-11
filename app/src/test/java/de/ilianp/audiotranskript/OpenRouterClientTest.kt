package de.ilianp.audiotranskript

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * Exercises [OpenRouterClient] against a [MockWebServer] instead of the live API, so these run
 * offline in CI without needing an OpenRouter key: the happy path including the exact request
 * shape MAI-Transcribe-2 expects, both OpenRouter error shapes (HTTP error and an `error` object
 * served with HTTP 200), and the audio-format mapping.
 */
class OpenRouterClientTest {

    private lateinit var server: MockWebServer
    private val payload = AudioPayload(byteArrayOf(1, 2, 3), "audio.ogg", "audio/ogg")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        OpenRouterClient.baseUrl = server.url("/api/v1").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `transcribe posts base64 audio and returns the text`() = runTest {
        server.enqueue(jsonResponse(200, """{"text":"  Hallo Welt  ","usage":{"seconds":9.2}}"""))

        val text = OpenRouterClient.transcribe(payload, "key-1", "de")

        assertEquals("Hallo Welt", text)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/audio/transcriptions", request.path)
        assertEquals("Bearer key-1", request.getHeader("Authorization"))

        val body = JSONObject(request.body.readUtf8())
        assertEquals("microsoft/mai-transcribe-2", body.getString("model"))
        assertEquals("de", body.getString("language"))
        val audio = body.getJSONObject("input_audio")
        assertEquals("ogg", audio.getString("format"))
        assertArrayEquals(payload.bytes, Base64.getDecoder().decode(audio.getString("data")))
    }

    @Test
    fun `transcribe omits language when set to auto-detect`() = runTest {
        server.enqueue(jsonResponse(200, """{"text":"Hallo"}"""))

        OpenRouterClient.transcribe(payload, "key-1", "")

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertFalse(body.has("language"))
    }

    @Test
    fun `transcribe maps extensions onto the formats OpenRouter accepts`() = runTest {
        // WhatsApp voice messages arrive as .opus, which OpenRouter only knows as its container.
        server.enqueue(jsonResponse(200, """{"text":"Hallo"}"""))

        OpenRouterClient.transcribe(payload.copy(filename = "audio.opus"), "key-1", "")

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("ogg", body.getJSONObject("input_audio").getString("format"))
    }

    @Test
    fun `transcribe surfaces the message from an HTTP error`() = runTest {
        server.enqueue(jsonResponse(402, """{"error":{"message":"Insufficient credits","code":402}}"""))

        val e = runCatching { OpenRouterClient.transcribe(payload, "key-1", "") }.exceptionOrNull()

        assertTrue(e is WizperException)
        assertTrue(e!!.message!!.contains("402"))
        assertTrue(e.message!!.contains("Insufficient credits"))
    }

    @Test
    fun `transcribe surfaces an error object served with HTTP 200`() = runTest {
        server.enqueue(jsonResponse(200, """{"error":{"message":"Upstream timed out","code":504}}"""))

        val e = runCatching { OpenRouterClient.transcribe(payload, "key-1", "") }.exceptionOrNull()

        assertTrue(e is WizperException)
        assertEquals("Upstream timed out", e!!.message)
    }

    @Test
    fun `transcribe reports a placeholder when the model returns no text`() = runTest {
        server.enqueue(jsonResponse(200, """{"text":"   "}"""))

        assertEquals("(Keine Transkription erhalten)", OpenRouterClient.transcribe(payload, "k", ""))
    }

    @Test
    fun `transcribe fails loudly on a non-JSON response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("<html>gateway</html>"),
        )

        val e = runCatching { OpenRouterClient.transcribe(payload, "key-1", "") }.exceptionOrNull()

        assertTrue(e is WizperException)
        assertTrue(e!!.message!!.contains("Unerwartete Antwort"))
    }

    private fun jsonResponse(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) =
        assertTrue(expected.contentEquals(actual))
}
