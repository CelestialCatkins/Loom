package com.loom.app.kernel

import org.json.JSONObject

/**
 * 服务总线：插件之间唯一的两条通信通道之一。
 * provide / inject / observe 全部以「具名服务」为单位，注册必须可撤销。
 */
class ServiceBus {

    fun interface Handler {
        @Throws(Exception::class)
        fun call(method: String, args: JSONObject): Any?
    }

    class Entry(
        val name: String,
        val owner: String,
        val layer: String,
        val handler: Handler,
    ) {
        var calls: Int = 0
    }

    private val registry = LinkedHashMap<String, Entry>()

    fun provide(name: String, owner: String, layer: String, handler: Handler): Boolean {
        if (name.isBlank() || registry.containsKey(name)) return false
        registry[name] = Entry(name, owner, layer, handler)
        return true
    }

    fun withdraw(name: String, owner: String) {
        val e = registry[name] ?: return
        if (e.owner == owner) registry.remove(name)
    }

    fun withdrawAll(owner: String): List<String> {
        val removed = registry.filterValues { it.owner == owner }.keys.toList()
        removed.forEach { registry.remove(it) }
        return removed
    }

    fun clear() = registry.clear()

    fun lookup(name: String): Entry? = registry[name]

    fun entries(): List<Entry> = registry.values.toList()

    fun has(name: String): Boolean = registry.containsKey(name)

    fun ownerOf(name: String): String? = registry[name]?.owner

    fun layerOf(name: String): String? = registry[name]?.layer

    @Throws(Exception::class)
    fun call(name: String, method: String, args: JSONObject): Any? {
        val e = registry[name] ?: throw IllegalStateException("服务不存在：$name")
        e.calls++
        return e.handler.call(method, args)
    }
}
