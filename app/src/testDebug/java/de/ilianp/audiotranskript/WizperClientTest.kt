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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
}
