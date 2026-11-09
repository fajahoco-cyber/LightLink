package app.lightlink.protocol

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MessageTest {
    private val persian = "سلام! من اینجا هستم. این یک پیام آزمایشی فارسی است."

    @Test
    fun splitProducesConsistentPackets() {
        val packets = MessageSplitter.split(persian, msgId = 3, payloadSize = 8)
        assertTrue(packets.size > 1)
        assertTrue(packets.all { it.total == packets.size && it.msgId == 3 })
        assertEquals(packets.indices.toList(), packets.map { it.seq })
    }

    @Test
    fun tooLongMessageIsRejected() {
        MessageSplitter.split("a".repeat(ProtocolConstants.MAX_MESSAGE_BYTES), 1)
        assertFailsWith<MessageTooLongException> {
            MessageSplitter.split("a".repeat(ProtocolConstants.MAX_MESSAGE_BYTES + 1), 1)
        }
    }

    @Test
    fun emptyMessageIsRejected() {
        assertFailsWith<IllegalArgumentException> { MessageSplitter.split("", 1) }
    }

    @Test
    fun fullPipelineWithShuffleDuplicatesAndNoise() {
        val packets = MessageSplitter.split(persian, msgId = 9, payloadSize = 8)
        val shuffled = packets.shuffled(Random(1)) + packets.take(2) // ترتیب بهم‌ریخته و تکراری

        val decoder = FrameStreamDecoder()
        val assembler = MessageAssembler()
        var completed: String? = null
        var completeCount = 0

        for (p in shuffled) {
            val bits = ByteArray(12) + FrameEncoder.encode(p)
            // یک بیت خراب در هر فریم
            val i = Random(p.seq).nextInt(bits.size - 20) + 20
            bits[i] = (bits[i].toInt() xor 1).toByte()
            for (b in bits) {
                val event = decoder.push(b.toInt()) as? FrameEvent.Received ?: continue
                val result = assembler.accept(event.packet)
                if (result is AssembleResult.Complete) {
                    completed = result.text
                    completeCount++
                }
            }
        }
        assertEquals(persian, completed)
        assertEquals(1, completeCount)
    }

    @Test
    fun missingPacketsAreReported() {
        val packets = MessageSplitter.split(persian, msgId = 4, payloadSize = 8)
        val assembler = MessageAssembler()
        packets.filter { it.seq != 1 }.forEach { assembler.accept(it) }
        assertEquals(listOf(1), assembler.missingSeqs())
        val last = assembler.accept(packets[1])
        assertIs<AssembleResult.Complete>(last)
        assertEquals(persian, last.text)
    }

    @Test
    fun completedMessageIsNotDeliveredTwice() {
        val packets = MessageSplitter.split("سلام", msgId = 5)
        val assembler = MessageAssembler()
        packets.forEach { assembler.accept(it) }
        assertIs<AssembleResult.Duplicate>(assembler.accept(packets[0]))
    }

    @Test
    fun conflictingTotalIsIgnored() {
        val assembler = MessageAssembler()
        assembler.accept(Packet(6, 0, 3, byteArrayOf(1)))
        assertIs<AssembleResult.Conflict>(assembler.accept(Packet(6, 1, 4, byteArrayOf(2))))
    }

    @Test
    fun invalidUtf8IsNotShown() {
        val assembler = MessageAssembler()
        val result = assembler.accept(Packet(2, 0, 1, byteArrayOf(0xFF.toByte())))
        assertIs<AssembleResult.InvalidText>(result)
    }
}
