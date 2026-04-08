package com.zmabel.localizeinlay

import com.intellij.codeInsight.hints.declarative.HintColorKind
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.OwnBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class LocalizeInlayHintsProviderPlus : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector

    private object Collector : OwnBypassCollector {
        private val integerLiteralRegex = Regex("""^[+-]?\d[\d_]*[uUlL]*$""")
        private const val LOCAL_UTILS_METHOD = "LocalUtils"
        private const val GET_STRING_METHOD = "GetString"

        override fun collectHintsForFile(file: PsiFile, sink: InlayTreeSink) {
            val text = file.text
            var i = 0
            while (i < text.length) {
                val methodStart = findMethodCallStart(text, i)
                if (methodStart == -1) break
                
                val methodEnd = findMethodCallEnd(text, methodStart)
                if (methodEnd == -1) {
                    i = methodStart + 1
                    continue
                }
                
                processMethodCall(text, methodStart, methodEnd, sink)
                i = methodEnd + 1
            }
        }

        private fun findMethodCallStart(text: String, startIndex: Int): Int {
            val pattern = "$LOCAL_UTILS_METHOD\\s*\\.\\s*$GET_STRING_METHOD\\s*\\(".toRegex()
            val match = pattern.find(text, startIndex)
            return match?.range?.first ?: -1
        }

        private fun findMethodCallEnd(text: String, startIndex: Int): Int {
            var depth = 0
            var i = text.indexOf('(', startIndex)
            if (i == -1) return -1
            
            i++
            while (i < text.length) {
                val ch = text[i]
                when (ch) {
                    '(' -> {
                        depth++
                        i++
                    }
                    ')' -> {
                        depth--
                        if (depth < 0) return i
                        i++
                    }
                    '"', '\'' -> i = skipQuoted(text, i)
                    '/' -> {
                        if (i + 1 < text.length) {
                            when (text[i + 1]) {
                                '/' -> i = skipLineComment(text, i + 2)
                                '*' -> i = skipBlockComment(text, i + 2)
                                else -> i++
                            }
                        } else {
                            i++
                        }
                    }
                    else -> i++
                }
            }
            return -1
        }

        private fun processMethodCall(text: String, startIndex: Int, endIndex: Int, sink: InlayTreeSink) {
            try {
                val openParenIndex = text.indexOf('(', startIndex)
                if (openParenIndex == -1) return
                
                val argumentsStart = openParenIndex + 1
                processArguments(text, argumentsStart, endIndex, sink)
            } catch (e: Exception) {
                // 忽略解析错误
            }
        }

        private fun processArguments(text: String, startIndex: Int, endIndex: Int, sink: InlayTreeSink) {
            var i = startIndex
            var currentArgStart = startIndex
            var depth = 0
            
            while (i < endIndex) {
                val ch = text[i]
                when (ch) {
                    '(' -> {
                        depth++
                        i++
                    }
                    ')' -> {
                        depth--
                        i++
                    }
                    ',' -> {
                        if (depth == 0) {
                            processSingleArgument(text, currentArgStart, i, sink)
                            currentArgStart = i + 1
                        }
                        i++
                    }
                    '"', '\'' -> i = skipQuoted(text, i)
                    '/' -> {
                        if (i + 1 < text.length) {
                            when (text[i + 1]) {
                                '/' -> i = skipLineComment(text, i + 2)
                                '*' -> i = skipBlockComment(text, i + 2)
                                else -> i++
                            }
                        } else {
                            i++
                        }
                    }
                    else -> i++
                }
            }
            
            // 处理最后一个参数
            if (currentArgStart < endIndex) {
                processSingleArgument(text, currentArgStart, endIndex, sink)
            }
        }

        private fun processSingleArgument(text: String, from: Int, to: Int, sink: InlayTreeSink) {
            val normalized = normalizeArgumentSlice(text, from, to) ?: return
            
            // 检查是否是整数字面量
            if (integerLiteralRegex.matches(normalized.literalText)) {
                val display = SnJsonConfigMatcher.displayTextFor(normalized.literalText) ?: return
                // Inline position is at the end of integer literal argument.
                sink.addPresentation(
                    InlineInlayPosition(normalized.literalEndOffset, relatedToPrevious = true),
                    payloads = null,
                    tooltip = null,
                    hintFormat = HintFormat.default.withColorKind(HintColorKind.TextWithoutBackground),
                ) {
                    text("\u00a0$display")
                }
            } else {
                // 检查是否包含条件表达式
                if (text.substring(from, to).contains(":")) {
                    processConditionalArgument(text, from, to, sink)
                }
                
                // 检查是否包含嵌套的 LocalUtils.GetString 调用
                if (text.substring(from, to).contains("$LOCAL_UTILS_METHOD.$GET_STRING_METHOD")) {
                    var i = from
                    while (i < to) {
                        val methodStart = findMethodCallStart(text, i)
                        if (methodStart == -1 || methodStart >= to) break
                        
                        val methodEnd = findMethodCallEnd(text, methodStart)
                        if (methodEnd == -1 || methodEnd > to) {
                            i = methodStart + 1
                            continue
                        }
                        
                        processMethodCall(text, methodStart, methodEnd, sink)
                        i = methodEnd + 1
                    }
                }
            }
        }

        private fun processConditionalArgument(text: String, from: Int, to: Int, sink: InlayTreeSink) {
            try {
                // 在原始文本中查找条件表达式的位置
                var i = from
                var questionMarkIndex = -1
                var colonIndex = -1
                var depth = 0
                
                while (i < to) {
                    val ch = text[i]
                    when (ch) {
                        '(' -> depth++
                        ')' -> depth--
                        '?' -> if (depth == 0) questionMarkIndex = i
                        ':' -> if (depth == 0) colonIndex = i
                        '"', '\'' -> i = skipQuoted(text, i) - 1
                        '/' -> {
                            if (i + 1 < text.length) {
                                when (text[i + 1]) {
                                    '/' -> i = skipLineComment(text, i + 2) - 1
                                    '*' -> i = skipBlockComment(text, i + 2) - 1
                                }
                            }
                        }
                    }
                    i++
                    
                    if (questionMarkIndex != -1 && colonIndex != -1) break
                }
                
                if (questionMarkIndex != -1 && colonIndex != -1 && questionMarkIndex < colonIndex) {
                    // 计算条件表达式中两个参数的实际位置
                    val firstArgStart = questionMarkIndex + 1
                    val firstArgEnd = colonIndex
                    val secondArgStart = colonIndex + 1
                    val secondArgEnd = to
                    
                    processSingleArgument(text, firstArgStart, firstArgEnd, sink)
                    processSingleArgument(text, secondArgStart, secondArgEnd, sink)
                }
            } catch (e: Exception) {
                // 忽略解析错误
            }
        }

        private fun normalizeArgumentSlice(text: String, from: Int, to: Int): NumericArg? {
            if (from < 0 || to > text.length || from >= to) return null
            
            var left = from
            var right = to
            while (left < right && text[left].isWhitespace()) left++
            while (right > left && text[right - 1].isWhitespace()) right--
            if (left >= right) return null

            val raw = text.substring(left, right)
            return NumericArg(right, raw.replace(" ", ""))
        }

        private fun skipQuoted(text: String, start: Int): Int {
            val quote = text[start]
            var i = start + 1
            while (i < text.length) {
                val ch = text[i]
                if (ch == '\\') {
                    i += 2
                    continue
                }
                if (ch == quote) return i + 1
                i++
            }
            return i
        }

        private fun skipLineComment(text: String, start: Int): Int {
            var i = start
            while (i < text.length && text[i] != '\n') i++
            return i
        }

        private fun skipBlockComment(text: String, start: Int): Int {
            var i = start
            while (i + 1 < text.length) {
                if (text[i] == '*' && text[i + 1] == '/') return i + 2
                i++
            }
            return text.length
        }

        private data class NumericArg(
            val literalEndOffset: Int,
            val literalText: String,
        )
    }
}
