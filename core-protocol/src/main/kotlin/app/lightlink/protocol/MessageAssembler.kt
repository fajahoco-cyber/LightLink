package app.lightlink.protocol

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

sealed interface AssembleResult {
    /** بسته‌ی جدید پذیرفته شد و پیام هنوز کامل نیست. */
    class Progress(val received: Int, val total: Int) : AssembleResult

    /** این بسته (یا کل پیام) قبلاً دریافت شده بود. */
    data object Duplicate : AssembleResult

    /** مقدار total با بسته‌های قبلیِ همین پیام نمی‌خواند؛ نادیده گرفته شد. */
    data object Conflict : AssembleResult

    /** همه‌ی بسته‌ها رسید و متن معتبر است. */
    class Complete(val text: String) : AssembleResult

    /** همه‌ی بسته‌ها رسید ولی بایت‌ها UTF-8 معتبر نیستند؛ پیام نمایش داده نمی‌شود. */
    data object InvalidText : AssembleResult
}

/**
 * جمع‌کردن بسته‌ها و بازسازی پیام. بسته‌ها می‌توانند نامرتب یا تکراری برسند.
 * سیاست: اگر بسته‌ای با msgId دیگر برسد، پیام ناقص قبلی کنار گذاشته می‌شود.
 * پیامی که کامل شده، با تکرار بسته‌هایش دوباره تحویل داده نمی‌شود.
 */
class MessageAssembler {
    private var currentMsgId: Int? = null
    private var total = 0
    private var parts: Array<ByteArray?> = arrayOfNulls(0)
    private var lastCompletedMsgId: Int? = null

    fun accept(packet: Packet): AssembleResult {
        if (packet.msgId == lastCompletedMsgId) return AssembleResult.Duplicate

        if (currentMsgId != packet.msgId) {
            currentMsgId = packet.msgId
            total = packet.total
            parts = arrayOfNulls(total)
        } else if (packet.total != total) {
            return AssembleResult.Conflict
        }

        if (parts[packet.seq] != null) return AssembleResult.Duplicate
        parts[packet.seq] = packet.payload

        val received = parts.count { it != null }
        if (received < total) return AssembleResult.Progress(received, total)

        val all = ByteArray(parts.sumOf { it!!.size })
        var pos = 0
        for (part in parts) {
            part!!.copyInto(all, pos)
            pos += part.size
        }
        lastCompletedMsgId = packet.msgId
        clearCurrent()

        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            AssembleResult.Complete(decoder.decode(ByteBuffer.wrap(all)).toString())
        } catch (e: CharacterCodingException) {
            AssembleResult.InvalidText
        }
    }

    /** شماره‌ی بسته‌هایی که هنوز نرسیده‌اند (برای نمایش به کاربر). */
    fun missingSeqs(): List<Int> = parts.indices.filter { parts[it] == null }

    fun reset() {
        clearCurrent()
        lastCompletedMsgId = null
    }

    private fun clearCurrent() {
        currentMsgId = null
        total = 0
        parts = arrayOfNulls(0)
    }
}
