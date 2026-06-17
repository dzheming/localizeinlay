package com.zmabel.localizeinlay

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.psi.PsiManager
import com.intellij.util.messages.MessageBusConnection
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.*

object SnJsonConfigMatcher : Disposable {
    private var connection: MessageBusConnection? = null
    private var watchService: WatchService? = null
    private var watchThread: Thread? = null
    private var currentWatchedDir: Path? = null

    const val DEFAULT_PATH: String = "ConfLocalize.json"

    private data class CacheEntry(val lastModified: Long, val map: Map<String, String>)

    @Volatile
    private var cache = CacheEntry(Long.MIN_VALUE, emptyMap())

    private val snFirstPattern = Regex(
        """\{[^{}]*"sn"\s*:\s*([-+]?\d+)[^{}]*"str"\s*:\s*"([^"]*)"""",
        setOf(RegexOption.IGNORE_CASE)
    )

    private val strFirstPattern = Regex(
        """\{[^{}]*"str"\s*:\s*"([^"]*)"[^{}]*"sn"\s*:\s*([-+]?\d+)""",
        setOf(RegexOption.IGNORE_CASE)
    )

    init {
        registerFileListener()
        startFileSystemWatch()
    }
    
    private fun registerFileListener() {
        connection?.disconnect()
        
        connection = ApplicationManager.getApplication().messageBus.connect()
        connection?.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                for (event in events) {
                    when (event) {
                        is VFileContentChangeEvent -> handleFileChange(event.file)
                        is VFilePropertyChangeEvent -> {
                            if (event.propertyName == VirtualFile.PROP_NAME) {
                                handleFileChange(event.file)
                            }
                        }
                    }
                }
            }
            
            private fun handleFileChange(file: VirtualFile) {
                try {
                    val configPath = configPath()
                    val absoluteConfigPath = configPath.toAbsolutePath().normalize().toString()
                    val fileAbsolutePath = Paths.get(file.path).toAbsolutePath().normalize().toString()
                    
                    if (fileAbsolutePath == absoluteConfigPath) {
                        resetCache()
                    }
                } catch (_: Exception) {
                }
            }
        })
    }
    
    private fun startFileSystemWatch() {
        try {
            watchService = FileSystems.getDefault().newWatchService()
            
            watchThread = Thread {
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        val key = watchService?.take() ?: break
                        
                        for (event in key.pollEvents()) {
                            if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                                try {
                                    val watchable = key.watchable()
                                    if (watchable is Path) {
                                        val context = event.context()
                                        if (context is Path) {
                                                val changedFile = watchable.resolve(context)
                                                checkFileChange(changedFile)
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                        
                        key.reset()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    } catch (_ : Exception) {
                    }
                }
            }
            
            watchThread?.isDaemon = true
            watchThread?.start()
            
            updateWatchPath()
        } catch (_: Exception) {
        }
    }
    
    fun updateWatchPath() {
        try {
            val configPath = configPath()
            val configDir = configPath.parent ?: return

            if (configDir == currentWatchedDir) return
            if (!Files.exists(configDir)) return

            configDir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE
            )
            currentWatchedDir = configDir
        } catch (_: Exception) {
        }
    }
    
    private fun checkFileChange(changedFile: Path) {
        try {
            val configPath = configPath()
            val absoluteConfigPath = configPath.toAbsolutePath().normalize().toString()
            val changedFileAbsolutePath = changedFile.toAbsolutePath().normalize().toString()
            if (absoluteConfigPath == changedFileAbsolutePath) {
                resetCache()
            }
        }  catch (_: Exception) {
        }
    }
    
    fun resetCache() {
        cache = CacheEntry(Long.MIN_VALUE, emptyMap())

        ApplicationManager.getApplication().invokeLater {
            val projects = ProjectManager.getInstance().openProjects
            
            for (project in projects) {
                if (project.isDisposed) continue
                
                try {
                    val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
                    val psiManager = PsiManager.getInstance(project)
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    for (file in fileEditorManager.openFiles) {
                        val psiFile = psiManager.findFile(file) ?: continue
                        daemonCodeAnalyzer.restart(psiFile)
                    }
                } catch (_: Exception) {
                }
            }
            
            try {
                VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
            } catch (_: Exception) {
            }
        }
    }
    
    override fun dispose() {
        connection?.disconnect()
        connection = null
        
        watchThread?.interrupt()
        watchService?.close()
        watchThread = null
        watchService = null
    }
    
    private fun configPath(): Path {
        val configured = LocalizeInlaySettingsState.getInstance().jsonPath
        val raw = if (configured.isNullOrBlank()) DEFAULT_PATH else configured
        return Path.of(raw)
    }

    fun displayTextFor(numericText: String): String? {
        val normalized = normalizeIntegerText(numericText) ?: return null
        val map = loadSnMap()
        return map[normalized]
    }
    
    fun findSnByString(query: String): Map<String, String> {
        val map = loadSnMap()
        return map.filter { (_, value) -> value.contains(query, ignoreCase = true) }
    }

    private fun loadSnMap(): Map<String, String> {
        return try {
            val path = configPath()
            if (!Files.exists(path)) return emptyMap()

            val lastModified = Files.getLastModifiedTime(path).toMillis()
            val currentCache = cache
            if (lastModified == currentCache.lastModified) return currentCache.map

            val content = Files.readString(path, StandardCharsets.UTF_8)
            val map = parseSnEntries(content)
            cache = CacheEntry(lastModified, map)
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseSnEntries(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (match in snFirstPattern.findAll(content)) {
            val snRaw = match.groupValues[1]
            val strValue = match.groupValues[2]
            val key = normalizeIntegerText(snRaw) ?: continue
            result[key] = strValue
        }
        for (match in strFirstPattern.findAll(content)) {
            val strValue = match.groupValues[1]
            val snRaw = match.groupValues[2]
            val key = normalizeIntegerText(snRaw) ?: continue
            if (!result.containsKey(key)) {
                result[key] = strValue
            }
        }
        return result
    }

    private fun normalizeIntegerText(value: String): String? {
        var text = value.trim()
        if (text.isEmpty()) return null

        text = text.replace("_", "")
        while (text.endsWith("l", true)) {
            text = text.dropLast(1)
        }
        if (text.startsWith("+")) {
            text = text.drop(1)
        }
        if (text.isEmpty()) return null

        return canonicalInteger(text)
    }

    private fun canonicalInteger(text: String): String? {
        return try {
            val canonical = BigInteger(text).toString()
            if (canonical == "-0") "0" else canonical
        } catch (_: NumberFormatException) {
            null
        }
    }

}
