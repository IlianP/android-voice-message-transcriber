package de.ilianp.audiotranskript

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

/**
 * Covers [WizperClient]'s provider chain where it needs a real [android.content.Context] to read
 * the shared audio, which is why this runs under Robolectric rather than as a plain JVM test.
 */
@RunWith(AndroidJUnit4::class)
class WizperClientTest {

    private lateinit var server: MockWebServer
    private lateinit var audioFile: File

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        OpenRouterClient.baseUrl = server.url("/api/v1").toString().trimEnd('/')
        GroqClient.baseUrl = server.url("/groq").toString().trimEnd('/')
        audioFile = File.createTempFile("nachricht", ".ogg").apply { writeBytes(ByteArray(32)) }
    }

    @After
    fun tearDown() {
        server.shutdown()
        audioFile.delete()
    }

    @Test
    fun `cancelling a run stays a cancellation instead of becoming a provider error`() =
        runBlocking {
            // The request hangs, so the job is cancelled while OpenRouter is still in flight -
            // exactly what pressing "Abbrechen" does.
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

            val run = CoroutineScope(Dispatchers.IO).async {
                WizperClient.transcribe(
                    context = context,
                    audioUri = Uri.fromFile(audioFile),
                    openRouterApiKey = "sk-or-test",
                    // Fallbacks configured too: a swallowed cancellation would walk right into
                    // them and end in a WizperException listing three "failures".
                    groqApiKey = "gsk-test",
                    sonioxApiKey = "sxk-test",
                    languageCode = "de",
                )
            }

            delay(200)
            run.cancel()

            val thrown = runCatching { run.await() }.exceptionOrNull()
            assertTrue(
                "Abbruch wurde zu ${thrown?.javaClass?.simpleName}: ${thrown?.message}",
                thrown is CancellationException,
            )
        }

    @Test
    fun `a batch comes back in the order it was given, whatever finishes first`() = runBlocking {
        // The first message answers last: the transcripts still have to line up with the audio.
        server.dispatcher = markerDispatcher { marker ->
            if (marker == 1.toByte()) Thread.sleep(300)
            MockResponse().setBody(JSONObject().put("text", "Text $marker").toString())
        }
        val progress = mutableListOf<Int>()

        val texts = WizperClient.transcribeAll(
            listOf(payload(1), payload(2), payload(3)),
            openRouterApiKey = "sk-or-test",
            groqApiKey = "",
            sonioxApiKey = "",
            languageCode = "de",
            onProgress = { synchronized(progress) { progress += it } },
        )

        assertEquals(listOf("Text 1", "Text 2", "Text 3"), texts)
        assertEquals(listOf(1, 2, 3), progress.sorted())
    }

    @Test
    fun `a failing message in a batch is named in the error`() = runBlocking {
        server.dispatcher = markerDispatcher { marker ->
            if (marker == 2.toByte()) {
                MockResponse().setResponseCode(500).setBody("kaputt")
            } else {
                MockResponse().setBody(JSONObject().put("text", "ok").toString())
            }
        }

        val thrown = runCatching {
            WizperClient.transcribeAll(
                listOf(payload(1), payload(2), payload(3)),
                openRouterApiKey = "sk-or-test",
                groqApiKey = "",
                sonioxApiKey = "",
                languageCode = "de",
            )
        }.exceptionOrNull()

        assertTrue("Kein WizperException: $thrown", thrown is WizperException)
        assertTrue(thrown!!.message!!, thrown.message!!.startsWith("Nachricht 2 von 3"))
    }

    @Test
    fun `each message of a batch falls back to the next provider on its own`() = runBlocking {
        // OpenRouter fails only message 2; Groq has to step in for that one alone.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val raw = request.body.readByteArray()
                val response = if (path.startsWith("/groq/")) {
                    // Multipart upload: the audio bytes sit in the body as they are.
                    val marker = if (raw.contains(ByteArray(16) { 2 })) 2 else 0
                    MockResponse().setBody(JSONObject().put("text", "Groq $marker").toString())
                } else {
                    val body = JSONObject(String(raw))
                    val marker = Base64.getDecoder()
                        .decode(body.getJSONObject("input_audio").getString("data")).first()
                    if (marker == 2.toByte()) {
                        MockResponse().setResponseCode(503).setBody("ueberlastet")
                    } else {
                        MockResponse().setBody(JSONObject().put("text", "OpenRouter $marker").toString())
                    }
                }
                return response.setHeader("Content-Type", "application/json")
            }
        }

        val texts = WizperClient.transcribeAll(
            listOf(payload(1), payload(2), payload(3)),
            openRouterApiKey = "sk-or-test",
            groqApiKey = "gsk-test",
            sonioxApiKey = "",
            languageCode = "de",
        )

        assertEquals(listOf("OpenRouter 1", "Groq 2", "OpenRouter 3"), texts)
    }

    private fun ByteArray.contains(part: ByteArray): Boolean =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

    private fun payload(marker: Byte) = AudioPayload(ByteArray(16) { marker }, "audio.ogg", "audio/ogg")

    /** Answers each request by the first audio byte it carries, i.e. by which message it is. */
    private fun markerDispatcher(answer: (Byte) -> MockResponse) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val body = JSONObject(request.body.readUtf8())
            val audio = Base64.getDecoder().decode(body.getJSONObject("input_audio").getString("data"))
            return answer(audio.first()).setHeader("Content-Type", "application/json")
        }
    }
}
