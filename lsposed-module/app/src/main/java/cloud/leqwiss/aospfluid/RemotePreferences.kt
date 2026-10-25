package cloud.leqwiss.aospfluid

import android.content.SharedPreferences

fun SharedPreferences.readBoolean(key: String, default: Boolean): Boolean {
    return runCatching { getBoolean(key, default) }.getOrDefault(default)
}

fun SharedPreferences.readInt(key: String, default: Int, minimum: Int, maximum: Int): Int {
    return runCatching { getInt(key, default) }.getOrDefault(default).coerceIn(minimum, maximum)
}
