package com.loom.app.kernel

import org.json.JSONArray
import org.json.JSONObject

/**
 * 事件总线：承载意图事件与状态事件，并记录因果链（运行链条）。
 * 追踪使用环形缓冲（默认 120 条），可整体关闭以省开销。
 */
class EventBus {

    class Event(
        val name: String,
        val from: String,
        val traceId: String,
        val parentId: String?,
        val payload: JSONObject,
        val ts: Long = System.currentTimeMillis(),
    )

    class Step(
        val kind: String,
        val from: String,
        val to: String,
        val label: String,
        val ts: Long = System.currentTimeMillis(),
    )

    class Trace(
        val id: String,
        val parentId: String?,
        val event: String,
        val from: String,
        val ts: Long = System.currentTimeMillis(),
    ) {
        var endTs: Long = ts
        var error: Boolean = false
        var errorMsg: String? = null
        val steps = ArrayList<Step>()

        fun step(kind: String, from: String, to: String, label: String) {
            steps.add(Step(kind, from, to, label))
        }

        fun toJson(): JSONObject {
            val o = JSONObject()
            o.put("id", id)
            if (parentId != null) o.put("parent", parentId)
            o.put("event", event)
            o.put("from", from)
            o.put("ts", ts)
            o.put("dur", (endTs - ts).coerceAtLeast(0))
            o.put("error", error)
            if (errorMsg != null) o.put("errorMsg", errorMsg)
            val arr = JSONArray()
            steps.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("kind", s.kind)
                        .put("from", s.from)
                        .put("to", s.to)
                        .put("label", s.label)
                        .put("ts", s.ts)
                )
            }
            o.put("steps", arr)
            return o
        }
    }

    fun interface Listener {
        fun onEvent(e: Event)
    }

    fun interface TraceHook {
        fun onTrace(t: Trace)
    }

    private class Sub(val event: String, val owner: String, val listener: Listener)

    private val subs = ArrayList<Sub>()
    private val buffer = ArrayList<Trace>()
    private val live = HashMap<String, Trace>()
    private var seq = 0

    var limit: Int = 120
    var tracing: Boolean = true
    var hook: TraceHook? = null

    var currentTraceId: String? = null
        private set

    fun on(event: String, owner: String, listener: Listener) {
        subs.add(Sub(event, owner, listener))
    }

    fun off(event: String, owner: String): Boolean {
        val i = subs.indexOfFirst { it.event == event && it.owner == owner }
        if (i < 0) return false
        subs.removeAt(i)
        return true
    }

    fun offOwner(owner: String): Int {
        val before = subs.size
        subs.removeAll { it.owner == owner }
        return before - subs.size
    }

    /** 订阅关系快照（owner 对 event），关系图用它画事件订阅边。 */
    fun subscriptions(): List<Pair<String, String>> = subs.map { it.owner to it.event }

    fun clearSubscribers() = subs.clear()

    fun subscriberCount(): Int = subs.size

    fun subscribersOf(event: String): List<String> =
        subs.filter { it.event == event }.map { it.owner }.distinct()

    fun hasSubscribers(event: String): Boolean = subs.any { it.event == event }

    fun emit(name: String, from: String, payload: JSONObject, parentId: String?): Trace {
        val id = "t" + (++seq)
        val trace = Trace(id, parentId, name, from)
        trace.step("emit", from, "bus", name)
        live[id] = trace
        val previous = currentTraceId
        currentTraceId = id
        val targets = subs.filter { it.event == name }.toList()
        for (s in targets) {
            trace.step("handle", "bus", s.owner, name)
            try {
                s.listener.onEvent(Event(name, from, id, parentId ?: id, payload))
            } catch (t: Throwable) {
                trace.error = true
                trace.errorMsg = t.message ?: t.javaClass.simpleName
                trace.step("error", s.owner, "bus", trace.errorMsg ?: "error")
            }
        }
        currentTraceId = previous
        trace.endTs = System.currentTimeMillis()
        live.remove(id)
        buffer.add(trace)
        while (buffer.size > limit) buffer.removeAt(0)
        if (tracing) {
            try {
                hook?.onTrace(trace)
            } catch (ignored: Throwable) {
            }
        }
        return trace
    }

    fun recordCall(traceId: String?, caller: String, owner: String?, service: String, method: String) {
        if (traceId == null) return
        val t = live[traceId] ?: return
        t.step("call", caller, owner ?: "?", "$service.$method")
    }

    fun traceList(): List<Trace> = buffer.toList()

    fun size(): Int = buffer.size

    fun clear() {
        buffer.clear()
        live.clear()
    }
}
