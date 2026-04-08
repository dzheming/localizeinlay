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

class LocalizeInlayHintsProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector

    private object Collector : OwnBypassCollector {
        private val integerLiteralRegex = Regex("""^[+-]?\d[\d_]*[uUlL]*$""")

        override fun collectHintsForFile(file: PsiFile, sink: InlayTreeSink) {
            val text = file.text
            val len = text.length
            var i = 0

            while (i < len) {
                val ch = text[i]
                if (ch == '"' || ch == '\'') {
                    i = skipQuoted(text, i)
                    continue
                }
                if (ch == '/' && i + 1 < len) {
                    val next = text[i + 1]
                    if (next == '/') {
                        i = skipLineComment(text, i + 2)
                        continue
                    }
                    if (next == '*') {
                        i = skipBlockComment(text, i + 2)
                        continue
                    }
                }
                if (ch == '(' && isLikelyCallStart(text, i)) {
                    i = processCallArguments(text, i, sink)
                    continue
                }
                i++
            }
        }

        private fun processCallArguments(text: String, openParenIndex: Int, sink: InlayTreeSink): Int {
            var depth = 1
            var i = openParenIndex + 1
            var argStart = i
            val len = text.length

            while (i < len) {
                val ch = text[i]
                if (ch == '"' || ch == '\'') {
                    i = skipQuoted(text, i)
                    continue
                }
                if (ch == '/' && i + 1 < len) {
                    val next = text[i + 1]
                    if (next == '/') {
                        i = skipLineComment(text, i + 2)
                        continue
                    }
                    if (next == '*') {
                        i = skipBlockComment(text, i + 2)
                        continue
                    }
                }

                when (ch) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            processSingleArgument(text, argStart, i, sink)
                            return i + 1
                        }
                    }
                    ',' -> {
                        if (depth == 1) {
                            processSingleArgument(text, argStart, i, sink)
                            argStart = i + 1
                        }
                    }
                }
                i++
            }
            return i
        }

        private fun processSingleArgument(text: String, from: Int, to: Int, sink: InlayTreeSink) {
            val normalized = normalizeArgumentSlice(text, from, to) ?: return
            if (!integerLiteralRegex.matches(normalized.literalText)) return
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
        }

        private fun normalizeArgumentSlice(text: String, from: Int, to: Int): NumericArg? {
            var left = from
            var right = to
            while (left < right && text[left].isWhitespace()) left++
            while (right > left && text[right - 1].isWhitespace()) right--
            if (left >= right) return null

            val raw = text.substring(left, right)
            return NumericArg(right, raw.replace(" ", ""))
        }

        private fun isLikelyCallStart(text: String, parenIndex: Int): Boolean {
            var i = parenIndex - 1
            while (i >= 0 && text[i].isWhitespace()) i--
            if (i < 0) return false
            val c = text[i]
            return c.isLetterOrDigit() || c == '_' || c == ')' || c == ']' || c == '>'
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
