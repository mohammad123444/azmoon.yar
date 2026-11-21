package org.json

import com.google.gson.*

class JSONException(m: String) : Exception(m)

class JSONArray {
    internal val arr: JsonArray
    constructor() { arr = JsonArray() }
    constructor(s: String) {
        val e = try { JsonParser.parseString(s) } catch (x: Exception) { throw JSONException(x.message ?: "bad") }
        if (!e.isJsonArray) throw JSONException("not array")
        arr = e.asJsonArray
    }
    constructor(c: Collection<*>) { arr = JsonArray(); for (x in c) put(x) }
    internal constructor(a: JsonArray) { arr = a }
    fun length() = arr.size()
    fun put(v: Any?): JSONArray { arr.add(wrap(v)); return this }
    fun getString(i: Int): String = arr[i].asString
    fun getInt(i: Int): Int = arr[i].asInt
    fun getJSONObject(i: Int): JSONObject { val e = arr[i]; if (!e.isJsonObject) throw JSONException("not obj"); return JSONObject(e.asJsonObject) }
    fun optJSONObject(i: Int): JSONObject? = if (i < arr.size() && arr[i].isJsonObject) JSONObject(arr[i].asJsonObject) else null
    override fun toString(): String = arr.toString()
}

class JSONObject {
    internal val obj: JsonObject
    constructor() { obj = JsonObject() }
    constructor(s: String) {
        val e = try { JsonParser.parseString(s) } catch (x: Exception) { throw JSONException(x.message ?: "bad") }
        if (!e.isJsonObject) throw JSONException("not object")
        obj = e.asJsonObject
    }
    internal constructor(o: JsonObject) { obj = o }
    fun put(k: String, v: Any?): JSONObject { obj.add(k, wrap(v)); return this }
    fun has(k: String) = obj.has(k)
    private fun el(k: String): JsonElement = obj.get(k)?.takeIf { !it.isJsonNull } ?: throw JSONException("missing $k")
    fun getString(k: String): String { val e = el(k); if (!e.isJsonPrimitive) throw JSONException("not string"); return e.asString }
    fun optString(k: String, d: String): String = obj.get(k)?.takeIf { it.isJsonPrimitive }?.asString ?: d
    fun getInt(k: String): Int = el(k).asInt
    fun optLong(k: String, d: Long): Long = try { obj.get(k)?.takeIf { it.isJsonPrimitive }?.asLong ?: d } catch (e: Exception) { d }
    fun getBoolean(k: String): Boolean = el(k).asBoolean
    fun optBoolean(k: String, d: Boolean): Boolean = obj.get(k)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: d
    fun getJSONArray(k: String): JSONArray { val e = el(k); if (!e.isJsonArray) throw JSONException("not array"); return JSONArray(e.asJsonArray) }
    fun optJSONArray(k: String): JSONArray? = obj.get(k)?.takeIf { it.isJsonArray }?.let { JSONArray(it.asJsonArray) }
    fun optJSONObject(k: String): JSONObject? = obj.get(k)?.takeIf { it.isJsonObject }?.let { JSONObject(it.asJsonObject) }
    fun keys(): Iterator<String> = obj.keySet().toList().iterator()
    override fun toString(): String = obj.toString()
    fun toString(indent: Int): String = GsonBuilder().setPrettyPrinting().create().toJson(obj)
}

internal fun wrap(v: Any?): JsonElement = when (v) {
    null -> JsonNull.INSTANCE
    is JSONObject -> v.obj
    is JSONArray -> v.arr
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Collection<*> -> JSONArray(v).arr
    else -> JsonPrimitive(v.toString())
}
