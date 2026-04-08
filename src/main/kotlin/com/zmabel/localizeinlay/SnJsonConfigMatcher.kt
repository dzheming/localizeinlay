package com.zmabel.localizeinlay

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object SnJsonConfigMatcher {
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

    private const val DEFAULT_PATH: String = "D:\\zxhj\\zx-design\\4configs\\Config\\gen\\export\\sourcejson\\1_混合文本总表#文本#ConfLocalize.json"
}
