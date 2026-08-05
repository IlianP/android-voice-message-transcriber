package de.ilianp.audiotranskript

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises [SonioxClient] against a [MockWebServer] instead of the live API, so these run
 * offline in CI without needing a Soniox key. They encode the same scenarios that were checked
 * by hand against the real API during development (see PR #3): the happy path, the two Soniox
 * error shapes (HTTP error body vs. `status: error`), and the leftover-sweep's filtering rules.
 */
class SonioxClientTest {

    private lateinit var server: MockWebServer
    private val payload = AudioPayload(byteArrayOf(1, 2, 3), "audio.ogg", "audio/ogg")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        SonioxClient.baseUrl = server.url("/v1").toString().trimEnd('/')
        SonioxClient.pollIntervalMs = 1L
        SonioxClient.timeoutMs = 300L
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ---- transcribe(): happy path -----------------------------------------------------------

    @Test
    fun `transcribe uploads, polls until completed, fetches text, then deletes both`() = runTest {
        val requests = mutableListOf<RecordedRequest>()
        server.dispatcher = recordingDispatcher(requests) { request ->
            when {
                request.method == "POST" && request.path == "/v1/files" ->
                    jsonResponse(201, """{"id":"file-1"}""")

                request.method == "POST" && request.path == "/v1/transcriptions" ->
                    jsonResponse(200, """{"id":"job-1","status":"queued"}""")

                request.method == "GET" && request.path == "/v1/transcriptions/job-1" -> {
                    // First poll still running, second poll completed - exercises the loop, not
                    // just a single iteration.
                    val status = if (requests.count { it.path == "/v1/transcriptions/job-1" } <= 1) {
                        "processing"
                    } else {
                        "completed"
                    }
                    jsonResponse(200, """{"id":"job-1","status":"$status"}""")
                }

                request.method == "GET" && request.path == "/v1/transcriptions/job-1/transcript" ->
                    jsonResponse(200, """{"text":"Hallo, wie geht's?"}""")

                request.method == "DELETE" -> MockResponse().setResponseCode(204)

                else -> MockResponse().setResponseCode(404)
            }
        }

        val text = SonioxClient.transcribe(payload, "test-key", "de")

        assertEquals("Hallo, wie geht's?", text)

        val deletes = requests.filter { it.method == "DELETE" }.map { it.path }
        assertTrue("job must be deleted", deletes.contains("/v1/transcriptions/job-1"))
        assertTrue("file must be deleted", deletes.contains("/v1/files/file-1"))

        val upload = requests.first { it.path == "/v1/files" }
        assertTrue(upload.body.readUtf8().contains("client_reference_id"))
        val create = requests.first { it.method == "POST" && it.path == "/v1/transcriptions" }
        val createBody = JSONObject(create.body.readUtf8())
        assertEquals("stt-async-v5", createBody.getString("model"))
        assertEquals("de", createBody.getJSONArray("language_hints").getString(0))
    }

    @Test
    fun `transcribe cleans up even when polling ends in error status`() = runTest {
        server.dispatcher = recordingDispatcher { request ->
            when {
                request.path == "/v1/files" -> jsonResponse(201, """{"id":"file-1"}""")
                request.method == "POST" && request.path == "/v1/transcriptions" ->
                    jsonResponse(200, """{"id":"job-1","status":"queued"}""")
                request.path == "/v1/transcriptions/job-1" ->
                    jsonResponse(200, """{"id":"job-1","status":"error","error_message":"Audio zu leise."}""")
                request.method == "DELETE" -> MockResponse().setResponseCode(204)
                else -> MockResponse().setResponseCode(404)
            }
        }

        val error = runCatching { SonioxClient.transcribe(payload, "test-key", "") }.exceptionOrNull()

        assertTrue(error is WizperException)
        assertEquals("Audio zu leise.", error?.message)
        // Cleanup must still happen on the error path.
        assertTrue(server.requestCount >= 4)
    }

    @Test
    fun `transcribe surfaces Soniox's readable message instead of the raw error body`() = runTest {
        server.dispatcher = recordingDispatcher { request ->
            when {
                request.path == "/v1/files" -> jsonResponse(
                    401,
                    """{"status_code":401,"error_type":"unauthenticated","message":"Incorrect API key provided."}""",
                )
                else -> MockResponse().setResponseCode(404)
            }
        }

        val error = runCatching { SonioxClient.transcribe(payload, "bad-key", "") }.exceptionOrNull()

        assertTrue(error is WizperException)
        assertEquals("Upload HTTP 401: Incorrect API key provided.", error?.message)
    }

    @Test
    fun `transcribe times out if polling never reaches a final status`() = runTest {
        server.dispatcher = recordingDispatcher { request ->
            when {
                request.path == "/v1/files" -> jsonResponse(201, """{"id":"file-1"}""")
                request.method == "POST" && request.path == "/v1/transcriptions" ->
                    jsonResponse(200, """{"id":"job-1","status":"queued"}""")
                request.path == "/v1/transcriptions/job-1" ->
                    jsonResponse(200, """{"id":"job-1","status":"processing"}""")
                request.method == "DELETE" -> MockResponse().setResponseCode(204)
                else -> MockResponse().setResponseCode(404)
            }
        }

        val error = runCatching { SonioxClient.transcribe(payload, "test-key", "") }.exceptionOrNull()

        assertTrue(error is WizperException)
        assertEquals("Zeitüberschreitung bei der Transkription.", error?.message)
    }

    // ---- cleanUpLeftovers(): filtering rules -------------------------------------------------

    private fun fileEntry(id: String, ref: String?, ageMs: Long) = JSONObject().apply {
        put("id", id)
        put("filename", "$id.ogg")
        put("size", 1)
        put("created_at", isoTimestamp(ageMs))
        if (ref != null) put("client_reference_id", ref) else put("client_reference_id", JSONObject.NULL)
    }

    private fun jobEntry(id: String, ref: String?, ageMs: Long, status: String = "completed") = JSONObject().apply {
        put("id", id)
        put("status", status)
        put("model", "stt-async-v5")
        put("created_at", isoTimestamp(ageMs))
        if (ref != null) put("client_reference_id", ref) else put("client_reference_id", JSONObject.NULL)
    }

    private fun isoTimestamp(ageMs: Long): String =
        java.time.Instant.ofEpochMilli(System.currentTimeMillis() - ageMs).toString()

    @Test
    fun `cleanUpLeftovers only deletes entries tagged by this app`() = runTest {
        val tenMinutes = 10 * 60 * 1000L
        val old = tenMinutes + 60_000L

        val files = listOf(
            fileEntry("own-old", "audio-transkript-app", old),
            fileEntry("foreign-old", "some-other-app", old),
            fileEntry("untagged-old", null, old),
        )
        val jobs = listOf(
            jobEntry("job-own-old", "audio-transkript-app", old),
        )
        val deletedPaths = mutableListOf<String>()

        server.dispatcher = recordingDispatcher { request ->
            when {
                request.method == "GET" && request.path?.startsWith("/v1/files") == true ->
                    jsonResponse(200, JSONObject().put("files", org.json.JSONArray(files)).toString())
                request.method == "GET" && request.path?.startsWith("/v1/transcriptions") == true ->
                    jsonResponse(200, JSONObject().put("transcriptions", org.json.JSONArray(jobs)).toString())
                request.method == "DELETE" -> {
                    deletedPaths += request.path!!
                    MockResponse().setResponseCode(204)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val deleted = SonioxClient.cleanUpLeftovers("test-key")

        assertEquals(2, deleted) // own-old file + job-own-old job
        assertTrue(deletedPaths.contains("/v1/files/own-old"))
        assertTrue(deletedPaths.contains("/v1/transcriptions/job-own-old"))
        assertFalse(deletedPaths.contains("/v1/files/foreign-old"))
        assertFalse(deletedPaths.contains("/v1/files/untagged-old"))
    }

    @Test
    fun `cleanUpLeftovers leaves fresh own entries alone so an in-flight run is never swept`() = runTest {
        val files = listOf(fileEntry("own-fresh", "audio-transkript-app", ageMs = 5_000L))
        val deletedPaths = mutableListOf<String>()

        server.dispatcher = recordingDispatcher { request ->
            when {
                request.method == "GET" && request.path?.startsWith("/v1/files") == true ->
                    jsonResponse(200, JSONObject().put("files", org.json.JSONArray(files)).toString())
                request.method == "GET" && request.path?.startsWith("/v1/transcriptions") == true ->
                    jsonResponse(200, """{"transcriptions":[]}""")
                request.method == "DELETE" -> {
                    deletedPaths += request.path!!
                    MockResponse().setResponseCode(204)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val deleted = SonioxClient.cleanUpLeftovers("test-key")

        assertEquals(0, deleted)
        assertTrue(deletedPaths.isEmpty())
    }

    @Test
    fun `cleanUpLeftovers skips jobs that are still queued or processing`() = runTest {
        val jobs = listOf(
            jobEntry("job-running", "audio-transkript-app", ageMs = 20 * 60 * 1000L, status = "processing"),
        )
        val deletedPaths = mutableListOf<String>()

        server.dispatcher = recordingDispatcher { request ->
            when {
                request.method == "GET" && request.path?.startsWith("/v1/transcriptions") == true ->
                    jsonResponse(200, JSONObject().put("transcriptions", org.json.JSONArray(jobs)).toString())
                request.method == "GET" && request.path?.startsWith("/v1/files") == true ->
                    jsonResponse(200, """{"files":[]}""")
                request.method == "DELETE" -> {
                    deletedPaths += request.path!!
                    MockResponse().setResponseCode(204)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val deleted = SonioxClient.cleanUpLeftovers("test-key")

        assertEquals(0, deleted)
        assertTrue(deletedPaths.isEmpty())
    }

    @Test
    fun `cleanUpLeftovers follows pagination cursors`() = runTest {
        val old = 20 * 60 * 1000L
        val page1 = JSONObject().apply {
            put("files", org.json.JSONArray(listOf(fileEntry("page1", "audio-transkript-app", old))))
            put("next_page_cursor", "cursor-2")
        }
        val page2 = JSONObject().apply {
            put("files", org.json.JSONArray(listOf(fileEntry("page2", "audio-transkript-app", old))))
            put("next_page_cursor", JSONObject.NULL)
        }
        val deletedPaths = mutableListOf<String>()

        server.dispatcher = recordingDispatcher { request ->
            when {
                request.method == "GET" && request.path == "/v1/files?limit=1000" ->
                    jsonResponse(200, page1.toString())
                request.method == "GET" && request.path == "/v1/files?limit=1000&cursor=cursor-2" ->
                    jsonResponse(200, page2.toString())
                request.method == "GET" && request.path?.startsWith("/v1/transcriptions") == true ->
                    jsonResponse(200, """{"transcriptions":[]}""")
                request.method == "DELETE" -> {
                    deletedPaths += request.path!!
                    MockResponse().setResponseCode(204)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val deleted = SonioxClient.cleanUpLeftovers("test-key")

        assertEquals(2, deleted)
        assertTrue(deletedPaths.contains("/v1/files/page1"))
        assertTrue(deletedPaths.contains("/v1/files/page2"))
    }

    // ---- helpers ------------------------------------------------------------------------------

    private fun jsonResponse(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun recordingDispatcher(
        into: MutableList<RecordedRequest> = mutableListOf(),
        handler: (RecordedRequest) -> MockResponse,
    ): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            into += request
            return handler(request)
        }
    }
}
