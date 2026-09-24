package de.ilianp.audiotranskript

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Renders the real screen on the JVM and writes it out as PNGs, so layout changes can actually
 * be looked at instead of only compiled. Robolectric's native graphics mode draws real pixels
 * without an emulator, which means this also runs on a machine with no hardware acceleration.
 *
 * The images land in `app/build/screenshots/`. They are artifacts for review, not golden files;
 * the assertions cover only what a picture cannot tell you by itself.
 *
 * Lives in `testDebug` because the activity the Compose test rule hosts the UI in comes from
 * `ui-test-manifest`, which is a debug-only dependency and has no business in a release build.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var server: MockWebServer
    private lateinit var audioFile: File

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val outputDir = File("build/screenshots")

    /** Repeated so the transcript is genuinely taller than the screen. */
    private val longTranscript: String
        get() = List(3) { paragraph }.joinToString(" ")

    private val paragraph = """
        Hey, ich wollte dir nur kurz Bescheid geben, dass das mit dem Termin am Donnerstag
        leider nicht klappt. Ich bin den ganzen Vormittag im Workshop und danach direkt beim
        Kunden draußen, das wird viel zu knapp. Freitag früh wäre bei mir dagegen komplett
        frei, da könnten wir uns in Ruhe zusammensetzen und die Zahlen noch mal durchgehen.
        Falls dir das nicht passt, sag einfach Bescheid, dann suchen wir nächste Woche einen
        neuen Termin. Ach ja, und die Unterlagen von letzter Woche habe ich dir schon per Mail
        geschickt, schau da gerne noch mal rein bevor wir sprechen. Bis dann!
    """.trimIndent().replace("\n", " ")

    /** The bottom-most piece of content, used to check nothing hides behind the player bar. */
    private val lastLineOnScreen =
        "Noch keine Jobs. Greift erst, wenn der Soniox-Fallback genutzt wird."

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(JSONObject().put("text", longTranscript).toString()),
        )
        OpenRouterClient.baseUrl = server.url("/api/v1").toString().trimEnd('/')
        SummaryClient.baseUrl = server.url("/api/v1").toString().trimEnd('/')

        // A real, openable file so the app's own audio reading path runs unchanged.
        audioFile = File.createTempFile("sprachnachricht", ".ogg").apply { writeBytes(ByteArray(64)) }

        Settings(context).apply {
            openRouterApiKey = "sk-or-demo"
            languageCode = "de"
        }
        TranscriptHistory(context).clear()
        File(context.filesDir, "pending").deleteRecursively()
    }

    @After
    fun tearDown() {
        server.shutdown()
        audioFile.delete()
        TranscriptHistory(context).clear()
        File(context.filesDir, "pending").deleteRecursively()
        Settings(context).apply {
            openRouterApiKey = ""
            groqApiKey = ""
            sonioxApiKey = ""
        }
    }

    @Test
    fun `first run shows the settings expanded`() {
        Settings(context).openRouterApiKey = ""

        composeRule.setContent { AppScreen(sharedUris = emptyList()) }
        composeRule.waitForIdle()

        capture("01-erststart-einstellungen-offen")
        composeRule.onNodeWithText("OpenRouter API-Key").assertExists()
    }

    @Test
    fun `settings fold away once a key is stored`() {
        composeRule.setContent { AppScreen(sharedUris = emptyList()) }
        composeRule.waitForIdle()

        capture("02-einstellungen-zugeklappt")
        composeRule.onNodeWithText("MAI-Transcribe-2 · Deutsch").assertExists()
    }

    @Test
    fun `the folded settings open again on tap`() {
        composeRule.setContent { AppScreen(sharedUris = emptyList()) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Einstellungen").performClick()
        composeRule.waitForIdle()

        capture("03-einstellungen-aufgeklappt")
        composeRule.onNodeWithText("Einstellungen speichern").assertExists()
    }

    @Test
    fun `the player stays pinned above and below the fold`() {
        showTranscribedMessage()

        capture("04-transkript-mit-player-leiste")
        composeRule.onNodeWithText("Tempo").assertExists()

        // Scroll to the very bottom of the content: the point of the bottomBar is that the
        // player survives this, which a screenshot of the unscrolled screen cannot show.
        composeRule.onNodeWithText(lastLineOnScreen).performScrollTo()
        composeRule.waitForIdle()

        capture("05-gescrollt-player-bleibt")
        composeRule.onNodeWithText("Tempo").assertExists()

        // The content must end above the bar, not run underneath it: the Scaffold's inner
        // padding is what keeps the last line reachable instead of hidden behind the player.
        val lastLine = composeRule.onNodeWithText(lastLineOnScreen).getUnclippedBoundsInRoot()
        val barTop = composeRule.onNodeWithText("Tempo").getUnclippedBoundsInRoot().top
        assertTrue(
            "Letzte Inhaltszeile (${lastLine.bottom}) liegt unter der Player-Leiste ($barTop)",
            lastLine.bottom <= barTop,
        )
    }

    @Test
    fun `the last message is back after opening the app without a share`() {
        val entry = storeMessage(longTranscript, marker = 1)

        // No shared URI: this is the app being opened from the launcher, which used to show
        // nothing but the empty state.
        composeRule.setContent { AppScreen(sharedUris = emptyList()) }
        composeRule.waitForIdle()

        capture("06-verlauf-letzte-nachricht-wiederhergestellt")
        composeRule.onNodeWithText("Donnerstag", substring = true).assertExists()
        composeRule.onNodeWithText("Aus dem Verlauf", substring = true).assertExists()
        // Played from the stored copy, so the player has to be up as well.
        composeRule.onNodeWithText("Tempo").assertExists()
        assertTrue("Keine Audio-Kopie gespeichert", entry != null)
    }

    @Test
    fun `the history lists the recent messages`() {
        val now = System.currentTimeMillis()
        val history = TranscriptHistory(context)
        history.add("Die aktuellste Nachricht: $paragraph", payload(1), now)
        history.add("Von gestern: $paragraph", payload(2), now - TimeUnit.HOURS.toMillis(30))
        history.add("Vom Wochenende: $paragraph", payload(3), now - TimeUnit.DAYS.toMillis(3))
        registerPlayableAudio(history)

        composeRule.setContent { AppScreen(sharedUris = emptyList()) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Verlauf (3)").performClick()
        composeRule.waitForIdle()

        capture("07-verlauf-liste")
        composeRule.onNodeWithText("gestern").assertExists()
        composeRule.onNodeWithText("vor 3 Tagen").assertExists()
        composeRule.onNodeWithText("Verlauf löschen").assertExists()
    }

    @Test
    fun `a stored transcript stays readable when its audio is gone`() {
        val history = TranscriptHistory(context)
        history.add(longTranscript, payload(1))
        val entry = history.entries().single()
        // The copy can be missing for real: a failed write, or a file removed underneath us.
        assertTrue("Audio-Kopie nicht loeschbar", File(history.audioUri(entry)!!.path!!).delete())

        composeRule.setContent { AppScreen(sharedUris = emptyList()) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Donnerstag", substring = true).assertExists()
        composeRule.onNodeWithText("Audio nicht mehr vorhanden", substring = true).assertExists()
        // Nothing left to play or to send to a provider, so neither control is offered.
        composeRule.onAllNodesWithText("Tempo").assertCountEquals(0)
        composeRule.onAllNodesWithText("Neu").assertCountEquals(0)
    }

    @Test
    fun `sharing the same message again starts a second run`() {
        val uri = Uri.fromFile(audioFile)
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, uri),
            ShadowMediaPlayer.MediaInfo(225_000, 0),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(JSONObject().put("text", "Zweiter Durchlauf, gleiche Datei.").toString()),
        )

        // Same URI both times: only the delivery id tells the screen that this is a new share.
        val delivery = mutableIntStateOf(1)
        composeRule.setContent {
            AppScreen(sharedUris = listOf(uri), shareDeliveryId = delivery.intValue)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Donnerstag", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }

        delivery.intValue = 2

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Zweiter Durchlauf", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Zweiter Durchlauf", substring = true).assertExists()
    }

    @Test
    fun `parked messages wait on the main screen`() {
        val queue = PendingQueue(context)
        queue.add(payload(1))
        queue.add(payload(2))

        composeRule.setContent { AppScreen(sharedUris = emptyList()) }
        composeRule.waitForIdle()

        capture("08-zwischenspeicher-wartet")
        composeRule.onNodeWithText("2 Nachrichten zwischengespeichert").assertExists()
        composeRule.onNodeWithText("Jetzt transkribieren").assertExists()
    }

    @Test
    fun `the last message picks up the parked ones and transcribes them as one`() {
        val queue = PendingQueue(context)
        queue.add(payload(1))
        queue.add(payload(2))
        audioFile.writeBytes(ByteArray(64) { 3 })

        // The requests run side by side, so answers go by which message was sent rather than by
        // arrival order - otherwise the test would pass by luck.
        val texts = mapOf<Byte, String>(
            1.toByte() to "Erste Nachricht: kommst du heute Abend?",
            2.toByte() to "Zweite Nachricht: ich bringe den Kuchen mit.",
            3.toByte() to "Dritte Nachricht: und sag Bescheid, wenn es später wird.",
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = JSONObject(request.body.readUtf8())
                val audio = Base64.getDecoder()
                    .decode(body.getJSONObject("input_audio").getString("data"))
                return MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSONObject().put("text", texts.getValue(audio.first())).toString())
            }
        }
        // The parked copies get names only the queue knows; every one of them lasts a minute.
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }

        // What MainActivity hands the screen when „Transkript starten“ arrives: the parked
        // messages taken out of the queue, then the shared one.
        val batch = queue.takeAll() + Uri.fromFile(audioFile)
        composeRule.setContent { AppScreen(sharedUris = batch, shareDeliveryId = 1) }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Dritte Nachricht", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        capture("09-stapel-drei-nachrichten")
        composeRule.onNodeWithText("Transkription · 3 Nachrichten").assertExists()
        composeRule.onNodeWithText("Nachricht 1 von 3").assertExists()
        // In the order they were shared: the parked ones first, the one that started it last.
        val first = composeRule.onNodeWithText(texts.getValue(1.toByte())).getUnclippedBoundsInRoot().top
        val second = composeRule.onNodeWithText(texts.getValue(2.toByte())).getUnclippedBoundsInRoot().top
        val third = composeRule.onNodeWithText(texts.getValue(3.toByte())).getUnclippedBoundsInRoot().top
        assertTrue("Reihenfolge stimmt nicht: $first / $second / $third", first < second && second < third)
        // Taken along, so nothing is left waiting.
        composeRule.onAllNodesWithText("zwischengespeichert", substring = true).assertCountEquals(0)
        assertTrue("Warteschlange nicht geleert", queue.count() == 0)

        val stored = TranscriptHistory(context).entries().single()
        assertTrue("Verlauf hat nicht alle drei: ${stored.segments}", stored.segments.size == 3)
    }

    @Test
    fun `a recreated screen keeps its batch and does not start over`() {
        val queue = PendingQueue(context)
        queue.add(payload(1))
        audioFile.writeBytes(ByteArray(64) { 2 })
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = JSONObject(request.body.readUtf8())
                val marker = Base64.getDecoder()
                    .decode(body.getJSONObject("input_audio").getString("data")).first()
                return MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSONObject().put("text", "Teil $marker vom Stapel").toString())
            }
        }
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }

        val batch = queue.takeAll() + Uri.fromFile(audioFile)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { AppScreen(sharedUris = batch, shareDeliveryId = 1) }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Teil 2 vom Stapel")).fetchSemanticsNodes().isNotEmpty()
        }
        val requestsBefore = server.requestCount

        // What a rotation does: the screen is rebuilt from its saved state, same share, same id.
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Transkription · 2 Nachrichten").assertExists()
        composeRule.onNodeWithText("Teil 1 vom Stapel").assertExists()
        assertTrue(
            "Nach dem Neuaufbau erneut transkribiert (${server.requestCount} statt $requestsBefore Requests)",
            server.requestCount == requestsBefore,
        )
    }

    @Test
    fun `a long message offers a summary without making one`() {
        showTranscribedMessage()

        capture("10-zusammenfassung-angeboten")
        composeRule.onNodeWithText("Lange Nachricht · 3:45").assertExists()
        composeRule.onNodeWithText("Zusammenfassen").assertExists()
        // Offered, not made: only the transcription went out.
        assertTrue("Ungefragt zusammengefasst", server.requestCount == 1)
    }

    @Test
    fun `a summary shows above the transcript and is kept in the history`() {
        val summary = "• Termin am Donnerstag fällt aus\n• Vorschlag: Freitag früh\n• Unterlagen kamen per Mail"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    JSONObject().put(
                        "choices",
                        JSONArray().put(JSONObject().put("message", JSONObject().put("content", summary))),
                    ).toString(),
                ),
        )
        showTranscribedMessage()

        composeRule.onNodeWithText("Zusammenfassen").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Vorschlag: Freitag früh", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        capture("11-zusammenfassung-ueber-transkript")
        composeRule.onAllNodesWithText("Lange Nachricht", substring = true).assertCountEquals(0)
        val summaryTop = composeRule.onNodeWithText("Vorschlag: Freitag früh", substring = true)
            .getUnclippedBoundsInRoot().top
        val transcriptTop = composeRule.onNodeWithText("Donnerstag leider", substring = true)
            .getUnclippedBoundsInRoot().top
        assertTrue("Zusammenfassung steht nicht über dem Transkript", summaryTop < transcriptTop)

        val stored = TranscriptHistory(context).entries().single()
        assertTrue("Zusammenfassung nicht im Verlauf: ${stored.summary}", stored.summary == summary)
    }

    @Test
    fun `a short message only gets a quiet summary button`() {
        showTranscribedMessage(durationMs = 45_000)

        composeRule.onNodeWithText("Zusammenfassung erstellen").assertExists()
        composeRule.onAllNodesWithText("Lange Nachricht", substring = true).assertCountEquals(0)
    }

    @Test
    fun `no summary is offered without an OpenRouter key`() {
        Settings(context).apply {
            openRouterApiKey = ""
            groqApiKey = "gsk-demo"
        }
        storeMessage(longTranscript, marker = 1)

        composeRule.setContent { AppScreen(sharedUris = emptyList()) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Donnerstag", substring = true).assertExists()
        composeRule.onAllNodesWithText("Lange Nachricht", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Zusammenfassung erstellen").assertCountEquals(0)
    }

    /** Puts one finished transcription into the history, with playable audio behind it. */
    private fun storeMessage(transcript: String, marker: Byte): HistoryEntry? {
        val history = TranscriptHistory(context)
        history.add(transcript, payload(marker))
        return registerPlayableAudio(history)
    }

    /** Teaches the shadow player about the stored copies, so the player bar renders enabled. */
    private fun registerPlayableAudio(history: TranscriptHistory): HistoryEntry? {
        var first: HistoryEntry? = null
        history.entries().forEach { entry ->
            val uri = history.audioUri(entry) ?: return@forEach
            if (first == null) first = entry
            ShadowMediaPlayer.addMediaInfo(
                DataSource.toDataSource(context, uri),
                ShadowMediaPlayer.MediaInfo(225_000, 0),
            )
        }
        return first
    }

    private fun payload(marker: Byte) =
        AudioPayload(ByteArray(64) { marker }, "audio.ogg", "audio/ogg")

    /** Shares a voice message and waits until its transcript is on screen. */
    private fun showTranscribedMessage(durationMs: Int = 225_000) {
        val uri = Uri.fromFile(audioFile)
        // Give the shadow player a real duration, so the bar renders enabled controls.
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, uri),
            ShadowMediaPlayer.MediaInfo(durationMs, 0),
        )

        composeRule.setContent { AppScreen(sharedUris = listOf(uri)) }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Donnerstag", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    // ---- capture helpers ----------------------------------------------------------------

    private fun capture(name: String) {
        val view = composeRule.activity.window.decorView
        val target = File(outputDir, "$name.png")
        writePng(view, target)
        assertTrue("Kein Screenshot geschrieben: $target", target.length() > 0)
    }

    private fun writePng(view: View, target: File) {
        require(view.width > 0 && view.height > 0) { "View wurde nicht gemessen: $view" }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        target.parentFile?.mkdirs()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
