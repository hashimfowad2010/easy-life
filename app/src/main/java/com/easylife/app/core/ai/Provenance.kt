package com.easylife.app.core.ai

import com.easylife.app.core.i18n.SProvenance
import com.easylife.app.core.i18n.Txt

/**
 * Where a value came from. Stored as text with every saved record and shown in the UI (spec rule 7).
 */
enum class Provenance(val label: Txt) {
    ON_DEVICE(SProvenance.onDevice),
    GEMINI_FREE(SProvenance.gemini),
    CLAUDE(SProvenance.claude),
    DEMO(SProvenance.demo),
    MANUAL(SProvenance.manual),
    IMPORTED(SProvenance.imported),
    COMMUNITY(SProvenance.community),
    ;

    val isSmartAi: Boolean get() = this == GEMINI_FREE || this == CLAUDE

    companion object {
        fun of(provider: ProviderId): Provenance = when (provider) {
            ProviderId.ON_DEVICE -> ON_DEVICE
            ProviderId.GEMINI_FREE -> GEMINI_FREE
            ProviderId.CLAUDE -> CLAUDE
            ProviderId.MOCK -> DEMO
        }

        fun parse(value: String?): Provenance = entries.firstOrNull { it.name == value } ?: ON_DEVICE
    }
}
