package com.shohan.cleanspace.data

import android.app.usage.StorageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.shohan.cleanspace.data.models.AppStorageInfo
import com.shohan.cleanspace.data.models.CategorySize
import com.shohan.cleanspace.data.models.DuplicateFile
import com.shohan.cleanspace.data.models.DuplicateGroup
import com.shohan.cleanspace.data.models.JunkFile
import com.shohan.cleanspace.data.models.LargeFile
import com.shohan.cleanspace.data.models.MediaAppInfo
import com.shohan.cleanspace.data.models.MediaCategory
import com.shohan.cleanspace.data.models.OrphanedItem
import com.shohan.cleanspace.data.models.StorageOverview
import com.shohan.cleanspace.shizuku.ICacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest

class StorageRepository(private val context: Context) {

    // ── Storage Overview ──────────────────────────────────────────────────────

    fun getStorageOverview(): StorageOverview {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        return StorageOverview(totalBytes = total, usedBytes = total - free, freeBytes = free)
    }

    // ── Category Breakdown (MediaStore — fast, no walkTopDown, no double-count) ──

    suspend fun getCategoryBreakdown(): List<CategorySize> = withContext(Dispatchers.IO) {
        var images = 0L
        var videos = 0L
        var audio  = 0L

        // Images
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.SIZE), null, null, null
        )?.use { c ->
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            while (c.moveToNext()) images += c.getLong(sizeIdx).coerceAtLeast(0)
        }

        // Videos
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media.SIZE), null, null, null
        )?.use { c ->
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            while (c.moveToNext()) videos += c.getLong(sizeIdx).coerceAtLeast(0)
        }

        // Audio
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.SIZE), null, null, null
        )?.use { c ->
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (c.moveToNext()) audio += c.getLong(sizeIdx).coerceAtLeast(0)
        }

        val appsBytes = getTotalAppsSize()
        val total = getStorageOverview().usedBytes
        // Documents & Others = what's left (avoids double-counting)
        val others = (total - appsBytes - images - videos - audio).coerceAtLeast(0)

        listOf(
            CategorySize("Apps", appsBytes),
            CategorySize("Images", images),
            CategorySize("Videos", videos),
            CategorySize("Audio", audio),
            CategorySize("Other Files", others)
        ).filter { it.bytes > 0 }
    }

    private fun getTotalAppsSize(): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0L
        if (!PermissionHelper.hasUsageAccess(context)) return 0L
        return try {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val sm  = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val uuid = sm.getUuidForPath(context.dataDir)
            var total = 0L
            context.packageManager.getInstalledApplications(0).forEach { app ->
                try { total += ssm.queryStatsForUid(uuid, app.uid).run { appBytes + cacheBytes + dataBytes } }
                catch (_: Exception) {}
            }
            total
        } catch (_: Exception) { 0L }
    }

    // ── Junk File Scanner (File walk, scoped to external only) ────────────────

    private val junkExtensions = setOf("tmp", "log", "bak", "old", "temp")

    suspend fun scanJunkFiles(): List<JunkFile> = withContext(Dispatchers.IO) {
        withTimeout(60_000L) {
            val results = mutableListOf<JunkFile>()
            val root = Environment.getExternalStorageDirectory()
            val visited = HashSet<String>()  // symlink loop protection

            if (root.exists() && root.canRead()) {
                root.walkTopDown()
                    .onEnter { dir ->
                        val canonical = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
                        if (canonical in visited) return@onEnter false
                        visited.add(canonical)
                        dir.name != "Android"
                    }
                    .forEach { file ->
                        if (file.isFile) {
                            val ext = file.extension.lowercase()
                            if (ext in junkExtensions) {
                                results.add(JunkFile(
                                    path = file.absolutePath,
                                    name = file.name,
                                    sizeBytes = file.length(),
                                    reason = "Temporary file (.$ext)"
                                ))
                            }
                        } else if (file.isDirectory && file != root) {
                            val children = try { file.listFiles() } catch (_: Exception) { null }
                            if (children != null && children.isEmpty()) {
                                results.add(JunkFile(
                                    path = file.absolutePath,
                                    name = file.name,
                                    sizeBytes = 0L,
                                    reason = "Empty folder"
                                ))
                            }
                        }
                    }
            }

            // Own app cache
            val ownCache = context.cacheDir
            if (ownCache.exists()) {
                val size = ownCache.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                if (size > 0) results.add(0, JunkFile(
                    path = ownCache.absolutePath,
                    name = "Reclaim App Cache",
                    sizeBytes = size,
                    reason = "This app's own cache"
                ))
            }
            results
        }
    }

    fun deleteJunkFile(junkFile: JunkFile): Boolean = try {
        File(junkFile.path).run { if (isDirectory) deleteRecursively() else delete() }
    } catch (_: Exception) { false }

    fun clearOwnAppCache(): Boolean = try { context.cacheDir.deleteRecursively() } catch (_: Exception) { false }

    // ── Large File Finder (MediaStore — fast) ──────────────────────────────────

    suspend fun scanLargeFiles(thresholdBytes: Long = 50L * 1024 * 1024): List<LargeFile> =
        withContext(Dispatchers.IO) {
            withTimeout(45_000L) {
                val results = mutableListOf<LargeFile>()
                val collections = listOf(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI to MediaStore.Images.Media.SIZE,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI  to MediaStore.Video.Media.SIZE,
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI  to MediaStore.Audio.Media.SIZE
                )
                val dataCol = MediaStore.MediaColumns.DATA

                collections.forEach { (uri, sizeCol) ->
                    context.contentResolver.query(
                        uri,
                        arrayOf(dataCol, sizeCol),
                        "$sizeCol >= ?",
                        arrayOf(thresholdBytes.toString()),
                        "$sizeCol DESC"
                    )?.use { c ->
                        val dataIdx = c.getColumnIndexOrThrow(dataCol)
                        val sizeIdx = c.getColumnIndexOrThrow(sizeCol)
                        while (c.moveToNext()) {
                            val path = c.getString(dataIdx) ?: continue
                            val size = c.getLong(sizeIdx)
                            results.add(LargeFile(
                                path = path,
                                name = File(path).name,
                                sizeBytes = size,
                                extension = File(path).extension
                            ))
                        }
                    }
                }
                results.sortedByDescending { it.sizeBytes }.take(200)
            }
        }

    fun deleteFile(path: String): Boolean = try { File(path).delete() } catch (_: Exception) { false }
    fun deleteLargeFile(file: LargeFile): Boolean = deleteFile(file.path)

    // ── Duplicate File Finder (size → hash, capped at 300 groups) ─────────────

    private val dupExtensions = setOf(
        "jpg","jpeg","png","gif","webp","bmp","heic",
        "mp4","mkv","avi","mov","3gp","mp3","wav","m4a","ogg","flac",
        "pdf","doc","docx","xls","xlsx"
    )

    suspend fun scanDuplicates(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        withTimeout(120_000L) {
            val root = Environment.getExternalStorageDirectory()
            val bySize = HashMap<Long, MutableList<File>>()
            val visited = HashSet<String>()

            if (root.exists() && root.canRead()) {
                root.walkTopDown()
                    .onEnter { dir ->
                        val cp = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
                        if (cp in visited) return@onEnter false
                        visited.add(cp)
                        dir.name != "Android"
                    }
                    .forEach { file ->
                        if (file.isFile && file.length() > 10_240L) {
                            if (file.extension.lowercase() in dupExtensions) {
                                bySize.getOrPut(file.length()) { mutableListOf() }.add(file)
                            }
                        }
                    }
            }

            val results = mutableListOf<DuplicateGroup>()
            for ((size, files) in bySize) {
                if (files.size < 2) continue
                val byHash = HashMap<String, MutableList<File>>()
                for (file in files) {
                    val hash = try { md5Of(file) } catch (_: Exception) { null } ?: continue
                    byHash.getOrPut(hash) { mutableListOf() }.add(file)
                }
                for (group in byHash.values) {
                    if (group.size < 2) continue
                    results.add(DuplicateGroup(
                        files = group.mapIndexed { i, f ->
                            DuplicateFile(f.absolutePath, f.name, size, i == 0)
                        },
                        sizeBytes = size
                    ))
                }
                if (results.size >= 300) break  // cap to prevent OOM
            }
            results.sortedByDescending { it.wastedBytes }
        }
    }

    private fun md5Of(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read: Int
            while (input.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ── Media App Cleaner (WhatsApp / Telegram / Messenger) ───────────────────

    private val knownMediaApps = listOf(
        "com.whatsapp"       to "WhatsApp",
        "com.whatsapp.w4b"   to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.facebook.orca"  to "Messenger",
        "com.facebook.katana" to "Facebook"
    )

    private val imageExt = setOf("jpg","jpeg","png","gif","webp","bmp","heic")
    private val videoExt = setOf("mp4","mkv","avi","mov","3gp","webm","flv")
    private val audioExt = setOf("mp3","wav","ogg","m4a","flac","aac")
    private val docExt   = setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv")

    suspend fun scanMediaApps(): List<MediaAppInfo> = withContext(Dispatchers.IO) {
        val mediaRoot = File(Environment.getExternalStorageDirectory(), "Android/media")
        val results = mutableListOf<MediaAppInfo>()

        for ((pkg, name) in knownMediaApps) {
            val appFolder = File(mediaRoot, pkg)
            if (!appFolder.exists() || !appFolder.canRead()) continue

            var images = 0L; var imgCount = 0
            var videos = 0L; var vidCount = 0
            var audio  = 0L; var audCount = 0
            var docs   = 0L; var docCount = 0
            var status = 0L; var staCount = 0
            var others = 0L; var othCount = 0

            appFolder.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                val ext = file.extension.lowercase()
                val isStatus = file.absolutePath.contains("/Statuses/") || file.absolutePath.contains("/.Statuses/")
                val sz = file.length()
                when {
                    isStatus -> { status += sz; staCount++ }
                    ext in imageExt -> { images += sz; imgCount++ }
                    ext in videoExt -> { videos += sz; vidCount++ }
                    ext in audioExt -> { audio += sz; audCount++ }
                    ext in docExt   -> { docs += sz; docCount++ }
                    else -> { others += sz; othCount++ }
                }
            }

            val cats = listOfNotNull(
                if (status > 0) MediaCategory("Status", status, staCount) else null,
                if (images > 0) MediaCategory("Images", images, imgCount) else null,
                if (videos > 0) MediaCategory("Videos", videos, vidCount) else null,
                if (audio  > 0) MediaCategory("Audio / Voice", audio,  audCount) else null,
                if (docs   > 0) MediaCategory("Documents", docs, docCount) else null,
                if (others > 0) MediaCategory("Other Files", others, othCount) else null
            )
            val total = images + videos + audio + docs + status + others
            if (total > 0) results.add(MediaAppInfo(pkg, name, total, cats))
        }
        results.sortedByDescending { it.totalBytes }
    }

    suspend fun deleteMediaCategory(packageName: String, categoryName: String): Long =
        withContext(Dispatchers.IO) {
            val mediaRoot = File(Environment.getExternalStorageDirectory(), "Android/media/$packageName")
            if (!mediaRoot.exists()) return@withContext 0L
            var freed = 0L
            mediaRoot.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                val ext = file.extension.lowercase()
                val isStatus = file.absolutePath.contains("/Statuses/") || file.absolutePath.contains("/.Statuses/")
                val matches = when (categoryName) {
                    "Status"       -> isStatus
                    "Images"       -> !isStatus && ext in imageExt
                    "Videos"       -> !isStatus && ext in videoExt
                    "Audio / Voice"-> !isStatus && ext in audioExt
                    "Documents"    -> !isStatus && ext in docExt
                    else           -> !isStatus && ext !in imageExt && ext !in videoExt && ext !in audioExt && ext !in docExt
                }
                if (matches) { val sz = file.length(); if (file.delete()) freed += sz }
            }
            freed
        }

    // ── Orphaned Data Finder (Shizuku required) ────────────────────────────────

    suspend fun scanOrphanedData(service: ICacheService): List<OrphanedItem> =
        withContext(Dispatchers.IO) {
            val installed = context.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()
            val results = mutableListOf<OrphanedItem>()
            for (location in listOf("Android/data", "Android/obb")) {
                val list = try { service.runCommand("ls /storage/emulated/0/$location") } catch (_: Exception) { "" }
                list.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { folder ->
                    if (folder !in installed && folder.contains(".")) {
                        val sz = try {
                            service.runCommand("du -sb /storage/emulated/0/$location/$folder")
                                .trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
                        } catch (_: Exception) { 0L }
                        results.add(OrphanedItem("/storage/emulated/0/$location/$folder", folder, sz, location))
                    }
                }
            }
            results.sortedByDescending { it.sizeBytes }
        }

    suspend fun deleteOrphanedItem(service: ICacheService, path: String): Boolean =
        withContext(Dispatchers.IO) {
            try { service.runCommand("rm -rf '$path' && echo OK").contains("OK") }
            catch (_: Exception) { false }
        }

    // ── App Storage List ───────────────────────────────────────────────────────

    suspend fun getInstalledApps(): List<AppStorageInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val results = mutableListOf<AppStorageInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && PermissionHelper.hasUsageAccess(context)) {
            try {
                val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val sm  = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val uuid = sm.getUuidForPath(context.dataDir)
                pm.getInstalledApplications(0).forEach { app ->
                    try {
                        val stats = ssm.queryStatsForUid(uuid, app.uid)
                        results.add(AppStorageInfo(
                            packageName = app.packageName,
                            appName = pm.getApplicationLabel(app).toString(),
                            appBytes = stats.appBytes,
                            cacheBytes = stats.cacheBytes,
                            dataBytes = stats.dataBytes,
                            isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        ))
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
        results.sortedByDescending { it.totalBytes }
    }
}
