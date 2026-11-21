package com.example.quizapp
import android.content.*
import android.database.Cursor
import android.net.Uri
import java.io.*

class MemPrefs : SharedPreferences {
    val m = HashMap<String, Any?>()
    override fun getString(key: String, def: String?) = m[key] as String? ?: def
    override fun getLong(key: String, def: Long) = (m[key] as Long?) ?: def
    override fun getInt(key: String, def: Int) = (m[key] as Int?) ?: def
    override fun getBoolean(key: String, def: Boolean) = (m[key] as Boolean?) ?: def
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(k: String, v: String?) = apply { m[k] = v }
        override fun putLong(k: String, v: Long) = apply { m[k] = v }
        override fun putInt(k: String, v: Int) = apply { m[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { m[k] = v }
        override fun remove(k: String) = apply { m.remove(k) }
        override fun apply() {}
        override fun commit() = true
    }
}

class TestResolver : ContentResolver() {
    val data = HashMap<String, ByteArray>()
    val names = HashMap<String, String>()
    var failOpen = false
    override fun openInputStream(uri: Uri): InputStream? =
        if (failOpen) throw IOException("boom") else data[uri.toString()]?.let { ByteArrayInputStream(it) }
    override fun openOutputStream(uri: Uri, mode: String): OutputStream? = null
    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val key = uri.toString()
        if (!data.containsKey(key)) return null
        val col = projection?.get(0)
        return object : Cursor {
            override fun moveToFirst() = true
            override fun getString(i: Int) = if (col == "_display_name") names[key] else null
            override fun getLong(i: Int) = data[key]!!.size.toLong()
            override fun isNull(i: Int) = col == "_size" && false
            override fun close() {}
        }
    }
}

class TestCtx(val root: File) : Context() {
    val prefs = HashMap<String, MemPrefs>()
    val resolver = TestResolver()
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs.getOrPut(name) { MemPrefs() }
    override val contentResolver: ContentResolver get() = resolver
    override val filesDir: File = File(root, "files").apply { mkdirs() }
    override val cacheDir: File = File(root, "cache").apply { mkdirs() }
    fun put(name: String, bytes: ByteArray): Uri {
        val u = Uri.parse("content://t/$name"); resolver.data[u.toString()] = bytes; resolver.names[u.toString()] = name; return u
    }
}

var failures = 0
var passes = 0
fun check(name: String, cond: Boolean) {
    if (cond) passes++ else { failures++; println("FAIL: $name") }
}
fun tmpDir(tag: String): File = java.nio.file.Files.createTempDirectory(tag).toFile()
