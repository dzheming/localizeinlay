package com.zmabel.localizeinlay

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.messages.MessageBusConnection
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.*

object SnJsonConfigMatcher {
    private var connection: MessageBusConnection? = null
    private var watchService: WatchService? = null
    private var watchThread: Thread? = null
    
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
                                            try {
                                                val watchablePath = watchable.toString()
                                                val contextPath = context.toString()
                                                val fullPath = if (watchablePath.endsWith(System.getProperty("file.separator"))) {
                                                    watchablePath + contextPath
                                                } else {
                                                    watchablePath + System.getProperty("file.separator") + contextPath
                                                }
                                                val changedFile = Paths.get(fullPath)
                                                checkFileChange(changedFile)
                                            } catch (_: Exception) {
                                            }
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
                    } catch (e1 : Exception) {
                        println(e1)
                    }
                }
            }
            
            watchThread?.isDaemon = true
            watchThread?.start()
            
            updateWatchPath()
        } catch (_: Exception) {
        }
    }
    
    private fun updateWatchPath() {
        try {
            val configPath = configPath()
            val configDir = configPath.parent
            
            if (configDir != null && Files.exists(configDir)) {
                configDir.register(
                    watchService, 
                    StandardWatchEventKinds.ENTRY_MODIFY, 
                    StandardWatchEventKinds.ENTRY_CREATE, 
                    StandardWatchEventKinds.ENTRY_DELETE
                )
            }
        } catch (_: Exception) {
        }
    }
    
    private fun checkFileChange(changedFile: Path) {
        try {
            val configPath = configPath()
            
            val configPathString = configPath.toString()
            val changedFilePathString = changedFile.toString()
            
            val absoluteConfigPath = try {
                configPath.toAbsolutePath().normalize().toString()
            } catch (_: Exception) {
                configPathString
            }
            
            val changedFileAbsolutePath = try {
                changedFile.toAbsolutePath().normalize().toString()
            } catch (_: Exception) {
                changedFilePathString
            }
            
            if (absoluteConfigPath == changedFileAbsolutePath || configPathString == changedFilePathString) {
                resetCache()
            }
        }  catch (_: Exception) {
        }
    }
    
    fun resetCache() {
        cachedLastModifiedMillis = Long.MIN_VALUE
        cachedMap = emptyMap()

        ApplicationManager.getApplication().invokeLater {
            val projects = ProjectManager.getInstance().openProjects
            
            for (project in projects) {
                if (project.isDisposed) continue
                
                try {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val files = fileEditorManager.openFiles
                    
                    for (file in files) {
                        try {
                            val editors = fileEditorManager.getEditors(file)
                            
                            val fileDocumentManager = FileDocumentManager.getInstance()
                            val document = fileDocumentManager.getDocument(file)
                            
                            val psiManager = PsiManager.getInstance(project)
                            val psiFile = psiManager.findFile(file)
                            
                            if (psiFile != null && document != null) {
                                val psiDocumentManager = PsiDocumentManager.getInstance(project)
                                psiDocumentManager.commitAllDocuments()
                                
                                val text = document.text
                                val length = text.length
                                if (length > 0) {
                                    WriteCommandAction.runWriteCommandAction(project) {
                                        try {
                                            document.insertString(length, " ")
                                            psiDocumentManager.commitDocument(document)
                                        } catch (_: Exception) {
                                        }
                                    }
                                    
                                    WriteCommandAction.runWriteCommandAction(project) {
                                        try {
                                            document.deleteString(length, length + 1)
                                            psiDocumentManager.commitDocument(document)
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            }
                            
                            for (editor in editors) {
                                editor.component.repaint()
                            }
                        } catch (_: Exception) {
                        }
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
    
    fun dispose() {
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
    // 匹配 body 中的对象里出现的 sn / str，对字段名大小写不敏感
    // 形如：{ "sn": 1001, "str": "xxxx" }
    private val entryPattern = Regex(
        """\{[^{}]*"sn"\s*:\s*([-+]?\d+)[^{}]*"str"\s*:\s*"([^"]*)"""",
        setOf(RegexOption.IGNORE_CASE)
    )

    @Volatile
    private var cachedLastModifiedMillis: Long = Long.MIN_VALUE

    @Volatile
    private var cachedMap: Map<String, String> = emptyMap()

    /**
     * 根据实参文本找到要展示的内联字符串。
     * 返回 null 表示不命中配置，也就不显示提示。
     */
    fun displayTextFor(numericText: String): String? {
        val normalized = normalizeIntegerText(numericText) ?: return null
        val map = loadSnMap()
        return map[normalized]
    }
    
    /**
     * 根据字符串查找匹配的sn值。
     * 返回一个Map，键是sn，值是对应的str。
     */
    fun findSnByString(query: String): Map<String, String> {
        val map = loadSnMap()
        return map.filter { (_, value) -> value.contains(query, ignoreCase = true) }
    }

    private fun loadSnMap(): Map<String, String> {
        return try {
            val path = configPath()
            if (!Files.exists(path)) return emptyMap()

            val lastModified = Files.getLastModifiedTime(path).toMillis()
            if (lastModified == cachedLastModifiedMillis) return cachedMap

            val content = Files.readString(path, StandardCharsets.UTF_8)
            val map = parseSnEntries(content)
            cachedMap = map
            cachedLastModifiedMillis = lastModified
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseSnEntries(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (match in entryPattern.findAll(content)) {
            val snRaw = match.groupValues[1]
            val strValue = match.groupValues[2]
            val key = normalizeIntegerText(snRaw) ?: continue
            result[key] = strValue
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

    private const val DEFAULT_PATH: String = "ConfLocalize.json"
}
