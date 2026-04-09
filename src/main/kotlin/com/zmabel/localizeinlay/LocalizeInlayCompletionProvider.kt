package com.zmabel.localizeinlay

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import java.util.*

class LocalizeInlayCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val editor = parameters.getEditor() ?: return

        // 获取当前光标位置的文本
        val caretOffset = editor.caretModel.offset
        val document = editor.document ?: return
        val text = document.text

        // 查找当前光标前的字符串
        val query = extractQuery(text, caretOffset)
        if (query.isNotEmpty()) {
            // 根据查询字符串查找匹配的sn值
            val matches = SnJsonConfigMatcher.findSnByString(query)

            // 添加匹配的结果到补全列表
            try {
                for ((sn, str) in matches) {
                    if (sn.isNotEmpty() && str.isNotEmpty()) {
                        val element = LookupElementBuilder.create(str)
                            .withTypeText(sn, true)
                            .withTailText("Localize Inlay")
                            .withBaseLookupString(sn)
                        result.addElement(element)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
    
    private fun extractQuery(text: String, caretOffset: Int): String {
        val sb = StringBuilder()
        var i = caretOffset - 1
        
        // 向前搜索，直到遇到非字母数字字符
        while (i >= 0 && (text[i].isLetterOrDigit() || text[i] in "_- ")) {
            sb.insert(0, text[i])
            i--
        }
        
        return sb.toString().trim()
    }
}

class LocalizeInlayCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            com.intellij.patterns.PlatformPatterns.psiElement(),
            LocalizeInlayCompletionProvider()
        )
    }
}
