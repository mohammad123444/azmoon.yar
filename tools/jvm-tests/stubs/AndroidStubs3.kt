package android.database
interface Cursor : java.io.Closeable {
    fun moveToFirst(): Boolean
    fun getString(i: Int): String?
    fun getLong(i: Int): Long
    fun isNull(i: Int): Boolean
}
