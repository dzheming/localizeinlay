package com.zmabel.localizeinlay

import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.declarative.InlayProviderInfo
import com.intellij.lang.Language

class LocalizeInlayHintsProviderFactory : InlayHintsProviderFactory {
    private val providerInfo = InlayProviderInfo(
        LocalizeInlayHintsProviderPlus(),
        PROVIDER_ID,
        setOf(),
        true,
        "Localize Argument Inlay"
    )

    override fun getProviderInfo(language: Language, providerId: String): InlayProviderInfo = providerInfo

    override fun getProvidersForLanguage(language: Language): List<InlayProviderInfo> {
        return if (SUPPORTED_LANGUAGE_IDS.contains(language.id.uppercase())) listOf(providerInfo) else emptyList()
    }

    override fun getSupportedLanguages(): Set<Language> = emptySet()

    companion object {
        const val PROVIDER_ID: String = "localize.argument.inlay.multi"

        // Java / C++ / C# (Rider 常见语言 ID)。
        private val SUPPORTED_LANGUAGE_IDS = setOf(
            "JAVA",
            "C",
            "CPP",
            "C++",
            "OBJECTIVEC",
            "C#",
            "CSHARP"
        )
    }
}

