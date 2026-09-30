package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.model.api.MediaStream

/**
 * The outcome of [SmartLanguageSelector.chooseSubtitle]
 *
 * @param stream the subtitle track to show or null if subtitles should be off
 */
data class SmartSubtitleDecision(
    val stream: MediaStream?,
)

/**
 * Picks audio & subtitle tracks from ordered language priority lists using what the media actually contains
 *
 * The subtitle languages are walked in priority order. For each language, if the media has a full (non-forced)
 * subtitle track in it, subtitles are used for that language. Otherwise, if the media has audio in it,
 * that audio is used instead so no subtitles are needed. Otherwise, the next language is tried.
 * When subtitles cover the language, audio follows its own priority list.
 *
 * Both functions return null when nothing matches, meaning the caller should use its normal (default) selection.
 */
object SmartLanguageSelector {
    /**
     * Choose the audio track or null if no track matches [audioLanguages] or [subtitleLanguages]
     *
     * 1. Walk [subtitleLanguages] in order: stop at one that has subtitles, but first use the audio of any earlier one
     *    that has audio (and no subtitles)
     * 2. Otherwise, the first of [audioLanguages] (by priority) that exists as audio
     */
    fun chooseAudio(
        audioStreams: List<MediaStream>,
        subtitleStreams: List<MediaStream>,
        audioLanguages: List<String>,
        subtitleLanguages: List<String>,
    ): MediaStream? {
        for (language in subtitleLanguages) {
            if (hasFullSubtitles(subtitleStreams, language)) break
            bestAudio(audioStreams, language)?.let { return it }
        }
        return audioLanguages.firstNotNullOfOrNull { bestAudio(audioStreams, it) }
    }

    /**
     * Choose the subtitle for the given audio language, or null if the [subtitleLanguages] don't apply to this media
     *
     * Walk [subtitleLanguages] in priority order:
     * - Media has subtitles in the language & it differs from the audio: show that subtitle track
     * - Media has subtitles in the language & it is the same as the audio: no subtitles, except forced ones
     * - Media has no subtitles in the language, but the audio is in it: no subtitles, except forced ones
     * - Otherwise, try the next language and if there are none left, null (use defaults)
     */
    fun chooseSubtitle(
        audioLanguage: String?,
        subtitleStreams: List<MediaStream>,
        subtitleLanguages: List<String>,
    ): SmartSubtitleDecision? {
        val audio = normalizeLanguage(audioLanguage)
        for (language in subtitleLanguages) {
            val sameAsAudio = audio != null && normalizeLanguage(language) == audio
            if (hasFullSubtitles(subtitleStreams, language) && !sameAsAudio) {
                return SmartSubtitleDecision(bestSubtitle(subtitleStreams, language))
            } else if (sameAsAudio) {
                return SmartSubtitleDecision(forcedSubtitle(subtitleStreams, audio))
            }
        }
        return null
    }

    private fun hasFullSubtitles(
        subtitleStreams: List<MediaStream>,
        language: String,
    ): Boolean = subtitleStreams.any { it.isFull && it.language.isLanguage(language) }

    private fun bestAudio(
        audioStreams: List<MediaStream>,
        language: String,
    ): MediaStream? =
        audioStreams
            .filter { it.language.isLanguage(language) }
            .sortedWith(compareByDescending<MediaStream> { it.isDefault }.thenByDescending { it.channels ?: 0 })
            .firstOrNull()

    private fun bestSubtitle(
        subtitleStreams: List<MediaStream>,
        language: String,
    ): MediaStream? =
        subtitleStreams
            .filter { it.isFull && it.language.isLanguage(language) }
            .sortedWith(compareByDescending<MediaStream> { it.isExternal }.thenByDescending { it.isDefault })
            .firstOrNull()

    /**
     * Forced/signs track in the audio's language, if any
     */
    private fun forcedSubtitle(
        subtitleStreams: List<MediaStream>,
        audioLanguage: String?,
    ): MediaStream? =
        audioLanguage?.let { audio ->
            subtitleStreams.firstOrNull { isForcedOrSigns(it) && it.language.isLanguage(audio) }
        }

    /** A track that is a complete translation, not forced or signs & songs only */
    private val MediaStream.isFull: Boolean get() = !isForcedOrSigns(this)

    private fun String?.isLanguage(language: String): Boolean {
        val normalized = normalizeLanguage(this)
        return normalized != null && normalized == normalizeLanguage(language)
    }

    /**
     * Lowercases the code and maps ISO 639-2/B codes (used by some tools, eg `fre`) to their 639-2/T equivalent
     * (used by Jellyfin's culture list, eg `fra`)
     */
    internal fun normalizeLanguage(language: String?): String? {
        val lang = language?.trim()?.lowercase()
        return if (lang.isNullOrBlank()) null else BIBLIOGRAPHIC_TO_TERMINOLOGY[lang] ?: lang
    }

    private val BIBLIOGRAPHIC_TO_TERMINOLOGY =
        mapOf(
            "alb" to "sqi",
            "arm" to "hye",
            "baq" to "eus",
            "bur" to "mya",
            "chi" to "zho",
            "cze" to "ces",
            "dut" to "nld",
            "fre" to "fra",
            "geo" to "kat",
            "ger" to "deu",
            "gre" to "ell",
            "ice" to "isl",
            "mac" to "mkd",
            "mao" to "mri",
            "may" to "msa",
            "per" to "fas",
            "rum" to "ron",
            "slo" to "slk",
            "tib" to "bod",
            "wel" to "cym",
        )
}
