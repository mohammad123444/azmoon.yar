package android.content

import android.net.Uri
import android.database.Cursor
import java.io.InputStream
import java.io.OutputStream

abstract class Context {
    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences
    abstract val contentResolver: ContentResolver
    abstract val filesDir: java.io.File
    abstract val cacheDir: java.io.File
    companion object { const val MODE_PRIVATE = 0 }
}

interface SharedPreferences {
    fun getString(key: String, def: String?): String?
    fun getLong(key: String, def: Long): Long
    fun getInt(key: String, def: Int): Int
    fun getBoolean(key: String, def: Boolean): Boolean
    fun edit(): Editor
    interface Editor {
        fun putString(k: String, v: String?): Editor
        fun putLong(k: String, v: Long): Editor
        fun putInt(k: String, v: Int): Editor
        fun putBoolean(k: String, v: Boolean): Editor
        fun remove(k: String): Editor
        fun apply()
        fun commit(): Boolean
    }
}

abstract class ContentResolver {
    abstract fun openInputStream(uri: Uri): InputStream?
    abstract fun openOutputStream(uri: Uri, mode: String): OutputStream?
    abstract fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor?
    abstract fun getType(uri: Uri): String?
}
