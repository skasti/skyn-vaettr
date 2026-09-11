package no.skasti.skynvaettr.episodes

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import no.skasti.skynvaettr.signals.Signal

class EvaluationChangeEpisodeRecorderTest {
    private val light = Signal<Boolean>("state.indoor.light")
    private val button = Signal<Boolean>("sensor.indoor.button")
    private val t0 = Instant.parse("2026-09-11T10:00:00Z")

    @Test
    fun `constant error does not create an unbounded episode`() {
        val recorder = recorder()

        assertTrue(recorder.observe(t0, 1.0).isEmpty())
        assertTrue(recorder.observe(t0.plusSeconds(10), 1.0).isEmpty())
        assertTrue(recorder.observe(t0.plusSeconds(60), 1.0).isEmpty())
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `evaluation change records thirty seconds before and after trigger`() {
        val recorder = recorder()
        recorder.observe(t0, 1.0)

        val triggerAt = t0.plusSeconds(60)
        assertTrue(recorder.observe(triggerAt, 0.0).isEmpty())
        assertTrue(recorder.isRecording)
        assertTrue(recorder.advanceTo(triggerAt.plusSeconds(29)).isEmpty())

        val completed = recorder.advanceTo(triggerAt.plusSeconds(30))

        assertEquals(1, completed.size)
        assertEquals(triggerAt.minusSeconds(30), completed.single().from)
        assertEquals(triggerAt.plusSeconds(30), completed.single().to)
        assertEquals(listOf(light, button), completed.single().signals)
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `both wrong to right and right to wrong are interesting`() {
        val recorder = recorder()
        recorder.observe(t0, 1.0)
        recorder.observe(t0.plusSeconds(60), 0.0)
        val first = recorder.advanceTo(t0.plusSeconds(90)).single()

        recorder.observe(t0.plusSeconds(100), 0.0)
        recorder.observe(t0.plusSeconds(120), 1.0)
        val second = recorder.advanceTo(t0.plusSeconds(150)).single()

        assertEquals(t0.plusSeconds(30), first.from)
        assertEquals(t0.plusSeconds(90), first.to)
        assertEquals(t0.plusSeconds(90), second.from)
        assertEquals(t0.plusSeconds(150), second.to)
    }

    @Test
    fun `new evaluation changes extend one active episode`() {
        val recorder = recorder()
        recorder.observe(t0, 1.0)
        recorder.observe(t0.plusSeconds(60), 0.0)

        assertTrue(recorder.observe(t0.plusSeconds(80), 1.0).isEmpty())
        assertTrue(recorder.advanceTo(t0.plusSeconds(109)).isEmpty())
        val completed = recorder.advanceTo(t0.plusSeconds(110))

        assertEquals(1, completed.size)
        assertEquals(t0.plusSeconds(30), completed.single().from)
        assertEquals(t0.plusSeconds(110), completed.single().to)
    }

    @Test
    fun `material change threshold ignores small evaluation noise`() {
        val recorder = EvaluationChangeEpisodeRecorder(
            signals = listOf(light),
            preRoll = Duration.ofSeconds(30),
            postRoll = Duration.ofSeconds(30),
            minimumEvaluationChange = 0.25,
        )

        recorder.observe(t0, 0.50)
        recorder.observe(t0.plusSeconds(10), 0.60)
        assertFalse(recorder.isRecording)

        recorder.observe(t0.plusSeconds(20), 0.90)
        assertTrue(recorder.isRecording)
    }

    @Test
    fun `minimum duration can keep episode open beyond post roll`() {
        val recorder = EvaluationChangeEpisodeRecorder(
            signals = listOf(light),
            preRoll = Duration.ofSeconds(10),
            postRoll = Duration.ofSeconds(5),
            minimumEpisodeDuration = Duration.ofSeconds(30),
        )
        recorder.observe(t0, 0.0)
        val triggerAt = t0.plusSeconds(20)
        recorder.observe(triggerAt, 1.0)

        assertTrue(recorder.advanceTo(triggerAt.plusSeconds(19)).isEmpty())
        val completed = recorder.advanceTo(triggerAt.plusSeconds(20))

        assertEquals(triggerAt.minusSeconds(10), completed.single().from)
        assertEquals(triggerAt.plusSeconds(20), completed.single().to)
    }

    @Test
    fun `change after quiet window starts a separate episode`() {
        val recorder = recorder()
        recorder.observe(t0, 0.0)
        recorder.observe(t0.plusSeconds(60), 1.0)

        val completed = recorder.observe(t0.plusSeconds(100), 0.0)

        assertEquals(1, completed.size)
        assertEquals(t0.plusSeconds(30), completed.single().from)
        assertEquals(t0.plusSeconds(90), completed.single().to)
        assertTrue(recorder.isRecording)

        val second = recorder.advanceTo(t0.plusSeconds(130)).single()
        assertEquals(t0.plusSeconds(70), second.from)
        assertEquals(t0.plusSeconds(130), second.to)
    }

    @Test
    fun `validates evaluation and temporal ordering`() {
        val recorder = recorder()
        recorder.observe(t0, 0.0)

        assertFailsWith<IllegalArgumentException> {
            recorder.observe(t0.minusSeconds(1), 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            recorder.observe(t0.plusSeconds(1), Double.NaN)
        }
    }

    private fun recorder() = EvaluationChangeEpisodeRecorder(
        signals = listOf(light, button),
        preRoll = Duration.ofSeconds(30),
        postRoll = Duration.ofSeconds(30),
        minimumEvaluationChange = 0.5,
    )
}
