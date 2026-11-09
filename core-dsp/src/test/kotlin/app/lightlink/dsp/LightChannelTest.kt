package app.lightlink.dsp

import app.lightlink.protocol.MessageSplitter
import app.lightlink.protocol.Packet
import app.lightlink.protocol.ProtocolConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LightChannelTest {
    private class Outcome(val messages: List<String>, val events: List<ReceiverEvent>)

    private fun run(samples: List<SimSample>): Outcome {
        val events = ArrayList<ReceiverEvent>()
        val core = LightReceiverCore { events.add(it) }
        for (s in samples) core.pushSample(s.timeNs, s.value)
        val messages = events.filterIsInstance<ReceiverEvent.MessageComplete>().map { it.text }
        return Outcome(messages, events)
    }

    private fun transmit(text: String, config: LightConfig, sim: ChannelSimulator, msgId: Int = 1): Outcome {
        val packets = MessageSplitter.split(text, msgId)
        val schedule = LightModulator.schedule(packets, config)
        return run(sim.simulate(schedule.levels, config.symbolMs))
    }

    @Test
    fun manchesterMapsBitsToHalfBits() {
        val levels = LightModulator.manchester(byteArrayOf(1, 0, 1))
        assertEquals(listOf(true, false, false, true, true, false), levels.toList())
    }

    @Test
    fun scheduleListsEveryFrameStart() {
        val packets = MessageSplitter.split("a".repeat(30), 2) // سه بسته‌ی ۱۲ بایتی
        val config = LightConfig(repeats = 2)
        val schedule = LightModulator.schedule(packets, config)
        assertEquals(6, schedule.frameStarts.size)
        assertEquals(listOf(0, 1, 2, 0, 1, 2), schedule.frameSeqs.toList())
        assertEquals(0, schedule.frameAt(0))
        assertEquals(5, schedule.frameAt(schedule.levels.size - 1))
    }

    @Test
    fun singlePacketPersianMessageIsReceived() {
        val outcome = transmit("سلام", LightConfig(symbolMs = 100, repeats = 1), ChannelSimulator())
        assertEquals(listOf("سلام"), outcome.messages)
    }

    @Test
    fun multiPacketMessageWithRepeatsIsDeliveredOnce() {
        val text = "سلام! من اینجا هستم. این یک پیام آزمایشی فارسی است."
        val outcome = transmit(text, LightConfig(symbolMs = 100, repeats = 2), ChannelSimulator(seed = 7))
        assertEquals(listOf(text), outcome.messages)
    }

    @Test
    fun worksAt24FpsWithSlowerSymbols() {
        val text = "تست نور با ۲۴ فریم"
        val outcome = transmit(
            text,
            LightConfig(symbolMs = 125, repeats = 2),
            ChannelSimulator(fps = 24.0, exposureMs = 10.0, seed = 3),
        )
        assertEquals(listOf(text), outcome.messages)
    }

    @Test
    fun survivesAmbientLightDriftAndNoise() {
        val text = "نور محیط تغییر می‌کند"
        val outcome = transmit(
            text,
            LightConfig(symbolMs = 100, repeats = 2),
            ChannelSimulator(drift = 25.0, noise = 7.0, seed = 11),
        )
        assertEquals(listOf(text), outcome.messages)
    }

    @Test
    fun repeatsRecoverFromAnUnluckyFirstPass() {
        // نویز زیاد: ممکن است بعضی بسته‌ها در دور اول رد شوند ولی دور دوم جبران می‌کند.
        val text = "تکرار بسته‌ها کمک می‌کند"
        var delivered = 0
        for (seed in 1L..6L) {
            val outcome = transmit(
                text,
                LightConfig(symbolMs = 100, repeats = 3),
                ChannelSimulator(noise = 10.0, senderJitterMs = 9.0, seed = seed),
            )
            if (outcome.messages == listOf(text)) delivered++
        }
        assertTrue(delivered >= 5, "فقط $delivered از ۶ اجرا موفق بود")
    }

    @Test
    fun pureNoiseProducesNoPackets() {
        for (seed in 1L..5L) {
            val sim = ChannelSimulator(noise = 6.0, seed = seed)
            val outcome = run(sim.noiseOnly(30.0))
            assertTrue(outcome.events.none { it is ReceiverEvent.PacketAccepted || it is ReceiverEvent.MessageComplete })
        }
    }

    @Test
    fun dimSignalBelowMinContrastIsIgnored() {
        val packets = MessageSplitter.split("سلام", 1)
        val config = LightConfig(repeats = 1)
        val schedule = LightModulator.schedule(packets, config)
        val sim = ChannelSimulator(low = 100.0, high = 104.0, noise = 0.5)
        val outcome = run(sim.simulate(schedule.levels, config.symbolMs))
        assertTrue(outcome.messages.isEmpty())
    }

    @Test
    fun eventsReportLockAndProgress() {
        val text = "a".repeat(30) // سه بسته
        val outcome = transmit(text, LightConfig(symbolMs = 100, repeats = 1), ChannelSimulator(seed = 5))
        assertEquals(listOf(text), outcome.messages)
        assertTrue(outcome.events.any { it is ReceiverEvent.Locked })
        val accepted = outcome.events.filterIsInstance<ReceiverEvent.PacketAccepted>()
        assertEquals(3, accepted.size)
        assertEquals(3, accepted.last().receivedCount)
    }

    @Test
    fun maximumLengthMessageFitsAndIsReceived() {
        val text = "ب".repeat(ProtocolConstants.MAX_MESSAGE_BYTES / 2)
        val outcome = transmit(text, LightConfig(symbolMs = 100, repeats = 1), ChannelSimulator(seed = 21))
        assertEquals(listOf(text), outcome.messages)
    }

    @Test
    fun packetObjectsRoundTripThroughSchedule() {
        val p = Packet(4, 0, 1, "x".toByteArray())
        val s = LightModulator.schedule(listOf(p), LightConfig(repeats = 1))
        assertNotNull(s)
        assertTrue(s.durationMs > 0)
    }
}
