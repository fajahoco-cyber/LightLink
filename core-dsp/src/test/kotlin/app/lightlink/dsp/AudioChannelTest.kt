package app.lightlink.dsp

import app.lightlink.protocol.MessageSplitter
import app.lightlink.protocol.ProtocolConstants
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioChannelTest {
    private fun receive(samples: FloatArray): List<ReceiverEvent> {
        val events = ArrayList<ReceiverEvent>()
        val core = AudioReceiverCore { events.add(it) }
        for (x in samples) core.pushSample(x)
        return events
    }

    private fun messages(events: List<ReceiverEvent>): List<String> =
        events.filterIsInstance<ReceiverEvent.MessageComplete>().map { it.text }

    private fun transmit(
        text: String,
        config: AudioConfig,
        sim: AudioChannelSimulator = AudioChannelSimulator(),
        msgId: Int = 1,
    ): List<ReceiverEvent> {
        val schedule = AudioModulator.schedule(MessageSplitter.split(text, msgId), config)
        return receive(sim.process(schedule.samples))
    }

    @Test
    fun scheduleListsEveryFrameStart() {
        val packets = MessageSplitter.split("a".repeat(30), 2)
        val schedule = AudioModulator.schedule(packets, AudioConfig(repeats = 2))
        assertEquals(6, schedule.frameStarts.size)
        assertEquals(listOf(0, 1, 2, 0, 1, 2), schedule.frameSeqs.toList())
        assertEquals(5, schedule.frameAt(schedule.samples.size - 1))
        assertTrue(schedule.durationMs > 0)
    }

    @Test
    fun everySupportedSpeedIsAutoDetected() {
        for (ms in AudioConfig.SUPPORTED_SYMBOL_MS) {
            val events = transmit("سلام دنیا", AudioConfig(symbolMs = ms, repeats = 1), msgId = ms)
            assertEquals(listOf("سلام دنیا"), messages(events), "symbolMs=$ms")
        }
    }

    @Test
    fun multiPacketPersianMessageWithEchoNoiseAndClockOffset() {
        val text = "سلام! من اینجا هستم. این یک پیام آزمایشی فارسی است."
        val events = transmit(
            text,
            AudioConfig(symbolMs = 15, repeats = 1),
            AudioChannelSimulator(noiseRms = 0.02, clockPpm = 150.0, seed = 5),
        )
        assertEquals(listOf(text), messages(events))
    }

    @Test
    fun negativeClockOffsetAlsoWorks() {
        val events = transmit(
            "اختلاف کلاک منفی",
            AudioConfig(symbolMs = 15, repeats = 1),
            AudioChannelSimulator(clockPpm = -200.0, seed = 9),
        )
        assertEquals(listOf("اختلاف کلاک منفی"), messages(events))
    }

    @Test
    fun survivesVolumeDriftAndNarrowbandInterference() {
        val events = transmit(
            "تغییر بلندی صدا",
            AudioConfig(symbolMs = 15, repeats = 2),
            AudioChannelSimulator(agcDepth = 0.4, interferenceHz = 1700.0, interferenceAmp = 0.02, seed = 3),
        )
        assertEquals(listOf("تغییر بلندی صدا"), messages(events))
    }

    @Test
    fun quietSignalIsStillReceived() {
        val events = transmit(
            "صدای آرام",
            AudioConfig(symbolMs = 25, repeats = 2),
            AudioChannelSimulator(gain = 0.04, noiseRms = 0.002, seed = 4),
        )
        assertEquals(listOf("صدای آرام"), messages(events))
    }

    @Test
    fun messageIsDeliveredOnceDespiteRepeats() {
        val text = "تکرار"
        val events = transmit(text, AudioConfig(symbolMs = 15, repeats = 3))
        assertEquals(listOf(text), messages(events))
        assertTrue(events.any { it is ReceiverEvent.Locked })
        assertTrue(events.any { it is ReceiverEvent.PacketDuplicate })
    }

    @Test
    fun maximumLengthMessageIsReceived() {
        val text = "ب".repeat(ProtocolConstants.MAX_MESSAGE_BYTES / 2)
        val events = transmit(text, AudioConfig(symbolMs = 25, repeats = 1), AudioChannelSimulator(seed = 8))
        assertEquals(listOf(text), messages(events))
    }

    @Test
    fun longSymbolsResistStrongRoomEcho() {
        val strongEcho = listOf(20.0 to 0.5, 60.0 to 0.35, 160.0 to 0.25)
        val events = transmit(
            "اتاق پر از پژواک",
            AudioConfig(symbolMs = 60, repeats = 2),
            AudioChannelSimulator(echoes = strongEcho, noiseRms = 0.02, seed = 6),
        )
        assertEquals(listOf("اتاق پر از پژواک"), messages(events))
    }

    @Test
    fun pureNoiseProducesNothing() {
        for (seed in 1L..4L) {
            val events = receive(AudioChannelSimulator(noiseRms = 0.02, seed = seed).noiseOnly(20.0))
            assertTrue(events.none { it is ReceiverEvent.PacketAccepted || it is ReceiverEvent.MessageComplete })
        }
    }

    @Test
    fun steadyToneProducesNothing() {
        val sr = 44100
        val tone = FloatArray(sr * 10) { i -> (0.3 * sin(2 * PI * 1200.0 * i / sr)).toFloat() }
        val events = receive(tone)
        assertTrue(events.none { it is ReceiverEvent.PacketAccepted || it is ReceiverEvent.MessageComplete })
    }

    @Test
    fun alternatingTonesWithoutValidFrameProduceNoMessage() {
        // preamble بی‌پایان بدون Sync معتبر نباید پیامی بسازد
        val config = AudioConfig(symbolMs = 15)
        val bits = ByteArray(400) { (it % 2).toByte() }
        val tones = AudioModulator.tones(bits, config)
        val pcm = ShortArray(tones.size) { (tones[it] * 32767f).toInt().toShort() }
        val events = receive(AudioChannelSimulator(seed = 2).process(pcm))
        assertTrue(events.none { it is ReceiverEvent.MessageComplete || it is ReceiverEvent.PacketAccepted })
    }
}
