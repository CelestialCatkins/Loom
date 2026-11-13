package com.loom.app.kernel

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 项目文件系统（ProjectFS）。严格遵循 PRD 8.1：
 *
 * projects/{id}/project.json
 * projects/{id}/plugins/ui|logic/{plugin-id}/
 * projects/{id}/data/            ProjectFS 数据
 * projects/{id}/exports/         导出快照
 *
 * 所有项目数据都在应用私有目录，导出归档不含宿主密钥或用户凭证。
 */
class ProjectFs(private val filesDir: File) {

    class Project(
        val id: String,
        var name: String,
        val created: Long,
        var updated: Long,
        val dir: File,
        var meta: JSONObject,
    )

    val root: File = File(filesDir, "projects").apply { mkdirs() }
    private val tmp: File = File(filesDir, "tmp").apply { mkdirs() }

    // ---------------------------------------------------------------- 项目

    fun list(): List<Project> {
        val dirs = root.listFiles() ?: return emptyList()
        return dirs.filter { File(it, "project.json").exists() }
            .mapNotNull { d ->
                val meta = Json.obj(runCatching { File(d, "project.json").readText() }.getOrNull())
                if (meta.length() == 0) null else Project(
                    id = Json.str(meta, "id", d.name),
                    name = Json.str(meta, "name", d.name),
                    created = Json.long(meta, "created"),
                    updated = Json.long(meta, "updated"),
                    dir = d,
                    meta = meta,
                )
            }
            .sortedByDescending { it.updated }
    }

    fun open(id: String?): Project? = if (id.isNullOrBlank()) null else list().firstOrNull { it.id == id }

    @Throws(IOException::class)
    fun create(name: String): Project {
        val clean = name.trim().let { if (it.length > 24) it.substring(0, 24) else it }
        require(clean.isNotEmpty()) { "项目名不能为空" }
        val id = "p" + System.currentTimeMillis().toString(36) + (100 + (Math.random() * 900).toInt()).toString(36)
        val dir = File(root, id)
        if (!dir.mkdirs()) throw IOException("无法创建项目目录")
        File(dir, "plugins/ui").mkdirs()
        File(dir, "plugins/logic").mkdirs()
        File(dir, "data").mkdirs()
        File(dir, "exports").mkdirs()
        val now = System.currentTimeMillis()
        val meta = JSONObject()
            .put("id", id)
            .put("name", clean)
            .put("created", now)
            .put("updated", now)
            .put("schema", 1)
            .put("disabled", org.json.JSONArray())
            .put("config", JSONObject())
        File(dir, "project.json").writeText(meta.toString(2))
        return Project(id, clean, now, now, dir, meta)
    }

    fun save(p: Project) {
        p.updated = System.currentTimeMillis()
        p.meta.put("updated", p.updated)
        p.meta.put("name", p.name)
        File(p.dir, "project.json").writeText(p.meta.toString(2))
    }

    fun delete(p: Project) = p.dir.deleteRecursively()

    // ---------------------------------------------------------------- 插件目录

    fun layerDir(p: Project, layer: Layer): File =
        File(File(p.dir, "plugins"), layer.key).apply { mkdirs() }

    fun pluginDir(p: Project, layer: Layer, pluginId: String): File =
        File(layerDir(p, layer), pluginId)

    fun pluginDirs(p: Project): List<File> {
        val plugins = File(p.dir, "plugins")
        val layers = plugins.listFiles() ?: return emptyList()
        return layers.filter { it.isDirectory }.flatMap { l ->
            l.listFiles()?.filter { it.isDirectory } ?: emptyList()
        }
    }

    // ---------------------------------------------------------------- 数据

    fun dataDir(p: Project): File = File(p.dir, "data").apply { mkdirs() }

    fun exportsDir(p: Project): File = File(p.dir, "exports").apply { mkdirs() }

    fun store(p: Project): JSONObject {
        val f = File(dataDir(p), "store.json")
        return if (f.exists()) Json.obj(runCatching { f.readText() }.getOrNull()) else JSONObject()
    }

    fun saveStore(p: Project, o: JSONObject) {
        File(dataDir(p), "store.json").writeText(o.toString())
    }

    fun pluginState(p: Project, pluginId: String): JSONObject {
        val f = File(dataDir(p), "state-$pluginId.json")
        return if (f.exists()) Json.obj(runCatching { f.readText() }.getOrNull()) else JSONObject()
    }

    fun savePluginState(p: Project, pluginId: String, o: JSONObject) {
        File(dataDir(p), "state-$pluginId.json").writeText(o.toString())
    }

    // ---------------------------------------------------------------- 归档

    /** 导出 .loomproj：整目录打包，并写一份导出快照。 */
    @Throws(IOException::class)
    fun exportZip(p: Project, out: OutputStream) {
        val snapshot = JSONObject()
            .put("exportedAt", System.currentTimeMillis())
            .put("schema", 1)
            .put("plugins", p.meta.optJSONArray("disabled") ?: org.json.JSONArray())
        File(exportsDir(p), "snapshot.json").writeText(snapshot.toString(2))
        ZipOutputStream(out).use { zip ->
            zipDir(p.dir, "", zip)
            zip.finish()
        }
    }

    private fun zipDir(dir: File, prefix: String, zip: ZipOutputStream) {
        val fs = dir.listFiles()?.sortedBy { it.name } ?: return
        for (f in fs) {
            val name = prefix + f.name
            if (f.isDirectory) {
                zipDir(f, "$name/", zip)
            } else {
                zip.putNextEntry(ZipEntry(name))
                FileInputStream(f).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** 导出插件源码包：只打 plugins/ 目录。 */
    @Throws(IOException::class)
    fun exportPluginsZip(p: Project, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zipDir(File(p.dir, "plugins"), "plugins/", zip)
            zip.finish()
        }
    }

    /** 导入 .loomproj：同名项目自动生成副本，绝不覆盖已有项目。 */
    @Throws(IOException::class)
    fun importZip(input: InputStream): Project {
        val work = File(tmp, "imp" + System.currentTimeMillis())
        try {
            unzip(input, work)
            val projectJson = findProjectJson(work)
                ?: throw IOException("归档里没有 project.json，不是有效的 .loomproj")
            val srcDir = projectJson.parentFile ?: throw IOException("归档结构异常")
            val meta = Json.obj(runCatching { projectJson.readText() }.getOrNull())
            val baseName = Json.str(meta, "name", "导入项目")
            val p = create(uniqueName(baseName))
            srcDir.copyRecursively(p.dir, overwrite = true)
            val m = Json.obj(runCatching { File(p.dir, "project.json").readText() }.getOrNull())
            m.put("id", p.id)
            m.put("name", p.name)
            File(p.dir, "project.json").writeText(m.toString(2))
            p.meta = m
            save(p)
            return open(p.id) ?: p
        } finally {
            work.deleteRecursively()
        }
    }

    private fun uniqueName(base: String): String =
        if (list().none { it.name == base }) base else "$base 副本"

    /**
     * 安装插件包（zip）：按 plugin.json 的 layer 放进对应目录。
     * 只做结构校验，不执行任何插件代码；执行发生在启用时。
     */
    @Throws(IOException::class)
    fun installPluginZip(p: Project, input: InputStream): List<String> {
        val work = File(tmp, "plug" + System.currentTimeMillis())
        val installed = ArrayList<String>()
        try {
            unzip(input, work)
            val manifests = ArrayList<File>()
            findManifests(work, manifests, 0)
            if (manifests.isEmpty()) throw IOException("包内没有 plugin.json")
            for (mf in manifests) {
                val manifest = PluginManifest.parse(Json.obj(runCatching { mf.readText() }.getOrNull()))
                val dest = pluginDir(p, manifest.layer, manifest.id)
                if (dest.exists()) throw IOException("插件 ${manifest.id} 已存在于项目")
                mf.parentFile?.copyRecursively(dest, overwrite = true) ?: throw IOException("插件包结构异常")
                installed.add(manifest.id)
            }
            return installed
        } finally {
            work.deleteRecursively()
        }
    }

    private fun findManifests(dir: File, out: MutableList<File>, depth: Int) {
        if (depth > 3) return
        val direct = File(dir, "plugin.json")
        if (direct.exists()) {
            out.add(direct)
            return
        }
        dir.listFiles()?.filter { it.isDirectory }?.forEach { findManifests(it, out, depth + 1) }
    }

    private fun findProjectJson(dir: File): File? {
        val direct = File(dir, "project.json")
        if (direct.exists()) return direct
        dir.listFiles()?.filter { it.isDirectory }?.forEach { d ->
            findProjectJson(d)?.let { return it }
        }
        return null
    }

    @Throws(IOException::class)
    fun unzip(input: InputStream, dest: File) {
        dest.mkdirs()
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!name.contains("..")) {
                    val out = File(dest, name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    // ---------------------------------------------------------------- 统计

    fun pluginCount(p: Project): Int = pluginDirs(p).size

    fun sizeOf(p: Project): Long = size(p.dir)

    private fun size(f: File?): Long {
        if (f == null) return 0
        if (f.isFile) return f.length()
        return f.listFiles()?.sumOf { size(it) } ?: 0
    }

    fun readText(file: File): String = runCatching { file.readText() }.getOrDefault("")
}
