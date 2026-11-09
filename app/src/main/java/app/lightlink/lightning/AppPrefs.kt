package app.lightlink.lightning

import android.content.Context
import android.content.SharedPreferences

/** تنظیمات و تاریخچه، فقط روی خود گوشی (SharedPreferences)؛ هیچ داده‌ای از گوشی خارج نمی‌شود. */
class AppPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("lightlink", Context.MODE_PRIVATE)

    var temperatureC: Int
        get() = prefs.getInt(KEY_TEMP, DEFAULT_TEMP).coerceIn(-40, 55)
        set(value) = prefs.edit().putInt(KEY_TEMP, value.coerceIn(-40, 55)).apply()

    fun history(): List<LightningRecord> =
        LightningHistoryCodec.decode(prefs.getString(KEY_HISTORY, "") ?: "")

    fun addRecord(record: LightningRecord) {
        val updated = LightningHistoryCodec.add(history(), record)
        prefs.edit().putString(KEY_HISTORY, LightningHistoryCodec.encode(updated)).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    companion object {
        const val DEFAULT_TEMP = 20
        private const val KEY_TEMP = "temperature_c"
        private const val KEY_HISTORY = "lightning_history"
    }
}
