package com.loom.app.kernel

import org.json.JSONArray
import org.json.JSONObject

/** org.json 的小工具集：插件契约全部用 JSON 表达，内核不引入序列化框架。 */
object Json {

    fun obj(s: String?): JSONObject = try {
        if (s.isNullOrBlank()) JSONObject() else JSONObject(s)
    } catch (e: Exception) {
        JSONObject()
    }

    fun copy(o: JSONObject?): JSONObject = try {
        if (o == null) JSONObject() else JSONObject(o.toString())
    } catch (e: Exception) {
        JSONObject()
    }

    fun str(o: JSONObject?, k: String, def: String = ""): String {
        if (o == null || !o.has(k) || o.isNull(k)) return def
        return o.opt(k)?.toString() ?: def
    }

    fun int(o: JSONObject?, k: String, def: Int = 0): Int = o?.optInt(k, def) ?: def

    fun long(o: JSONObject?, k: String, def: Long = 0L): Long = o?.optLong(k, def) ?: def

    fun bool(o: JSONObject?, k: String, def: Boolean = false): Boolean = o?.optBoolean(k, def) ?: def

    fun arr(o: JSONObject?, k: String): JSONArray = o?.optJSONArray(k) ?: JSONArray()

    fun strList(o: JSONObject?, k: String): List<String> {
        val a = arr(o, k)
        val out = ArrayList<String>(a.length())
        for (i in 0 until a.length()) {
            val v = a.opt(i)
            if (v != null && v != JSONObject.NULL) out.add(v.toString())
        }
        return out
    }

    fun keys(o: JSONObject?): List<String> {
        if (o == null) return emptyList()
        val out = ArrayList<String>()
        val it = o.keys()
        while (it.hasNext()) out.add(it.next())
        return out
    }

    fun array(list: Collection<String>): JSONArray {
        val a = JSONArray()
        list.forEach { a.put(it) }
        return a
    }

    /** 展示用：整数不带 .0，对象/数组原样。 */
    fun display(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "—"
        is Number -> {
            val d = v.toDouble()
            if (d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()
        }
        is Boolean -> if (v) "true" else "false"
        is JSONObject -> v.toString()
        is JSONArray -> v.toString()
        else -> v.toString()
    }

    fun truthy(v: Any?): Boolean = when (v) {
        null, JSONObject.NULL -> false
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        else -> {
            val s = v.toString()
            s.isNotEmpty() && s != "false" && s != "0"
        }
    }
}
