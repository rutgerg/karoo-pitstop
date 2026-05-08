package dev.karoorestaurant.telemetry

import android.content.SharedPreferences

internal class FakeSharedPreferences : SharedPreferences {

    private val storage: MutableMap<String, Any?> = mutableMapOf()

    override fun getString(key: String, defValue: String?): String? =
        if (storage.containsKey(key)) storage[key] as String? else defValue

    override fun getInt(key: String, defValue: Int): Int =
        if (storage.containsKey(key)) storage[key] as Int else defValue

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun contains(key: String?): Boolean = storage.containsKey(key)

    override fun getAll(): MutableMap<String, *> = HashMap(storage)

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        throw NotImplementedError()

    override fun getLong(key: String?, defValue: Long): Long = throw NotImplementedError()
    override fun getFloat(key: String?, defValue: Float): Float = throw NotImplementedError()
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = throw NotImplementedError()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ): Unit = throw NotImplementedError()

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ): Unit = throw NotImplementedError()

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removals += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun apply() {
            if (clearAll) storage.clear()
            removals.forEach { storage.remove(it) }
            storage.putAll(pending)
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = throw NotImplementedError()

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor =
            throw NotImplementedError()

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            throw NotImplementedError()

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            throw NotImplementedError()
    }
}
