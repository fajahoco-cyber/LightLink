package app.lightlink.protocol

/** ثابت‌های پروتکل مشترک نور و صدا. مشخصات کامل در docs/PROTOCOL.md است. */
object ProtocolConstants {
    const val VERSION = 1

    /** کلمه‌ی همگام‌سازی ۱۶ بیتی که قبل از هر فریم فرستاده می‌شود (بدون کدگذاری FEC). */
    const val SYNC_WORD = 0xD391
    const val SYNC_BITS = 16

    /** Version، MsgId، Seq، Total، Length */
    const val HEADER_BYTES = 5
    const val CRC_BYTES = 2

    const val MAX_PAYLOAD = 16
    const val DEFAULT_PAYLOAD = 12

    /** سقف اندازه‌ی پیام به بایت (UTF-8). */
    const val MAX_MESSAGE_BYTES = 200

    /** هر بایت پس از Hamming(7,4) چهارده بیت می‌شود. */
    const val CODED_BITS_PER_BYTE = 14
}
