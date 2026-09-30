package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.PlaybackLanguageChoiceDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.JellyfinUserPreferences
import com.github.damontecres.wholphin.data.model.PlaybackLanguageChoice
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.DefaultUserConfiguration
import com.github.damontecres.wholphin.preferences.ExperimentalPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the smart language selection: the pure [SmartLanguageSelector] and how [StreamChoiceService] applies it
 */
class SmartLanguageSelectorTest {
    private val audioLangs = listOf("jpn", "eng", "spa")
    private val subLangs = listOf("spa")

    private fun chooseAudio(
        audio: List<MediaStream>,
        subs: List<MediaStream>,
        audioLangs: List<String> = this.audioLangs,
        subLangs: List<String> = this.subLangs,
    ) = SmartLanguageSelector.chooseAudio(audio, subs, audioLangs, subLangs)

    private fun chooseSubtitle(
        audioLang: String?,
        subs: List<MediaStream>,
        subLangs: List<String> = this.subLangs,
    ) = SmartLanguageSelector.chooseSubtitle(audioLang, subs, subLangs)

    // Scenario 1: has Japanese audio & Spanish subtitles -> Japanese audio with Spanish subtitles
    @Test
    fun japaneseAudioWithSpanishSubtitles() {
        val audio = listOf(audioTrack(1, "jpn"), audioTrack(2, "eng"))
        val subs = listOf(subtitle(3, "eng"), subtitle(4, "spa"))
        val chosenAudio = chooseAudio(audio, subs)
        assertEquals(1, chosenAudio?.index)
        assertEquals(4, chooseSubtitle(chosenAudio?.language, subs)?.stream?.index)
    }

    // Scenario 2: Spanish & English audio with Spanish subtitles -> English (higher audio priority) with Spanish subtitles
    @Test
    fun englishAudioWinsWhenSpanishSubtitlesExist() {
        val audio = listOf(audioTrack(1, "spa"), audioTrack(2, "eng"))
        val subs = listOf(subtitle(3, "spa"))
        val chosenAudio = chooseAudio(audio, subs)
        assertEquals(2, chosenAudio?.index)
        assertEquals(3, chooseSubtitle(chosenAudio?.language, subs)?.stream?.index)
    }

    // Scenario 3: Spanish & English audio without Spanish subtitles -> Spanish audio, no subtitles
    @Test
    fun spanishAudioIsFallbackWhenNoSpanishSubtitles() {
        val audio = listOf(audioTrack(1, "spa"), audioTrack(2, "eng"))
        val subs = listOf(subtitle(3, "eng"))
        val chosenAudio = chooseAudio(audio, subs)
        assertEquals(1, chosenAudio?.index)
        val decision = chooseSubtitle(chosenAudio?.language, subs)
        assertNotNull(decision)
        assertNull(decision?.stream)
    }

    // Scenario 4: no Spanish subtitles & no Spanish audio -> defaults (null means "use defaults" for subtitles)
    @Test
    fun defaultsWhenNoSpanishAtAll() {
        val audio = listOf(audioTrack(1, "fre"), audioTrack(2, "ger"))
        val subs = listOf(subtitle(3, "eng"))
        assertNull(chooseAudio(audio, subs))
        assertNull(chooseSubtitle("fre", subs))
    }

    @Test
    fun audioPriorityOrderWhenNoSpanishAudioOrSubtitles() {
        val audio = listOf(audioTrack(1, "eng"), audioTrack(2, "jpn"))
        val chosenAudio = chooseAudio(audio, emptyList())
        assertEquals(2, chosenAudio?.index)
        assertNull(chooseSubtitle(chosenAudio?.language, emptyList()))
    }

    @Test
    fun forcedSubtitlesDoNotCountAsSpanishSubtitles() {
        val audio = listOf(audioTrack(1, "spa"), audioTrack(2, "eng"))
        val subs = listOf(subtitle(3, "spa", forced = true), subtitle(4, "spa", title = "Signs & Songs"))
        // Only forced/signs, so Spanish subtitles are missing and Spanish audio is used
        val chosenAudio = chooseAudio(audio, subs)
        assertEquals(1, chosenAudio?.index)
        // The forced track is still used with Spanish audio
        assertEquals(3, chooseSubtitle(chosenAudio?.language, subs)?.stream?.index)
    }

    @Test
    fun spanishAudioAndSubtitlesButOtherAudioHasPriority() {
        val audio = listOf(audioTrack(1, "spa"), audioTrack(2, "jpn"))
        val subs = listOf(subtitle(3, "spa"))
        val chosenAudio = chooseAudio(audio, subs)
        assertEquals(2, chosenAudio?.index)
        assertEquals(3, chooseSubtitle(chosenAudio?.language, subs)?.stream?.index)
    }

    @Test
    fun spanishAudioWithSpanishSubtitlesAvailableTurnsSubtitlesOff() {
        // Eg the user picked Spanish audio for this episode
        val subs = listOf(subtitle(3, "spa"))
        val decision = chooseSubtitle("spa", subs)
        assertNotNull(decision)
        assertNull(decision?.stream)
    }

    @Test
    fun subtitlePriorityOrder() {
        val subLangs = listOf("spa", "eng")
        val audio = listOf(audioTrack(1, "jpn"))
        val subs = listOf(subtitle(2, "eng"), subtitle(3, "spa"))
        assertEquals(3, chooseSubtitle("jpn", subs, subLangs)?.stream?.index)
        // Without Spanish, falls to English
        assertEquals(2, chooseSubtitle("jpn", subs.filter { it.language == "eng" }, subLangs)?.stream?.index)
        assertEquals(1, chooseAudio(audio, subs, subLangs = subLangs)?.index)
    }

    @Test
    fun higherPrioritySubtitleLanguageAudioBeatsLowerPrioritySubtitles() {
        // Spanish is preferred over English: no Spanish subtitles, but Spanish audio exists, so use it instead of English subs
        val subLangs = listOf("spa", "eng")
        val audio = listOf(audioTrack(1, "jpn"), audioTrack(2, "spa"))
        val subs = listOf(subtitle(3, "eng"))
        val chosenAudio = chooseAudio(audio, subs, subLangs = subLangs)
        assertEquals(2, chosenAudio?.index)
        val decision = chooseSubtitle(chosenAudio?.language, subs, subLangs)
        assertNotNull(decision)
        assertNull(decision?.stream)
    }

    @Test
    fun bibliographicAndTerminologyCodesMatch() {
        val audio = listOf(audioTrack(1, "fre"))
        assertEquals(1, chooseAudio(audio, emptyList(), audioLangs = listOf("fra"))?.index)
        val subs = listOf(subtitle(2, "ger"))
        assertEquals(2, chooseSubtitle("jpn", subs, listOf("deu"))?.stream?.index)
    }

    @Test
    fun prefersDefaultThenMoreChannelsWithinLanguage() {
        val audio =
            listOf(
                audioTrack(1, "eng", channels = 6),
                audioTrack(2, "eng", channels = 2, default = true),
                audioTrack(3, "eng", channels = 8),
            )
        assertEquals(2, chooseAudio(audio, emptyList(), listOf("eng"), emptyList())?.index)
        assertEquals(3, chooseAudio(audio.filter { !it.isDefault }, emptyList(), listOf("eng"), emptyList())?.index)
    }

    // StreamChoiceService integration

    @Test
    fun serviceIgnoresSmartSelectionWhenDisabled() {
        val audio = listOf(audioTrack(1, "spa"), audioTrack(2, "eng", default = true))
        val subs = listOf(subtitle(3, "eng"))
        // Enabled flag off, lists set
        val chosen = service().chooseAudioStream(audio, null, null, prefs(enabled = false), subs)
        assertEquals(2, chosen?.index)
    }

    @Test
    fun serviceIgnoresSmartSelectionWhenExperimentalDisabled() {
        val audio = listOf(audioTrack(1, "spa"), audioTrack(2, "eng", default = true))
        val subs = listOf(subtitle(3, "eng"))
        val chosen = service().chooseAudioStream(audio, null, null, prefs(experimental = false), subs)
        assertEquals(2, chosen?.index)
    }

    @Test
    fun serviceAppliesSmartSelectionWhenEnabled() {
        val audio = listOf(audioTrack(1, "spa"), audioTrack(2, "eng", default = true))
        val subs = listOf(subtitle(3, "eng"))
        val prefs = prefs()
        val service = service()
        val chosenAudio = service.chooseAudioStream(audio, null, null, prefs, subs)
        assertEquals(1, chosenAudio?.index)
        // Spanish audio & no Spanish subtitles: subtitles off, even though the English track is default
        val chosenSubs =
            service.chooseSubtitleStream(chosenAudio?.language, subs.map { it.copy(isDefault = true) }, null, null, prefs)
        assertNull(chosenSubs)
    }

    @Test
    fun serviceShowsSpanishSubtitlesWithJapaneseAudio() {
        val audio = listOf(audioTrack(1, "eng"), audioTrack(2, "jpn"))
        val subs = listOf(subtitle(3, "eng", default = true), subtitle(4, "spa"))
        val prefs = prefs()
        val service = service()
        val chosenAudio = service.chooseAudioStream(audio, null, null, prefs, subs)
        assertEquals(2, chosenAudio?.index)
        assertEquals(4, service.chooseSubtitleStream(chosenAudio?.language, subs, null, null, prefs)?.index)
    }

    @Test
    fun serviceKeepsManualChoicesOverSmartSelection() {
        val audio = listOf(audioTrack(1, "spa"), audioTrack(2, "eng"))
        val subs = listOf(subtitle(3, "spa"), subtitle(4, "eng"))
        val prefs = prefs()
        val service = service()
        // Manually chosen tracks for this item
        val itemPlayback = itemPlayback(audioIndex = 1, subtitleIndex = 4)
        assertEquals(1, service.chooseAudioStream(audio, itemPlayback, null, prefs, subs)?.index)
        assertEquals(4, service.chooseSubtitleStream("spa", subs, itemPlayback, null, prefs)?.index)
        // Series level choices
        val plc = PlaybackLanguageChoice(1, UUID.randomUUID(), audioLanguage = "eng", subtitleLanguage = "eng", subtitlesDisabled = false)
        assertEquals(2, service.chooseAudioStream(audio, null, plc, prefs, subs)?.index)
        assertEquals(4, service.chooseSubtitleStream("eng", subs, null, plc, prefs)?.index)
        val disabled = PlaybackLanguageChoice(1, UUID.randomUUID(), subtitlesDisabled = true)
        assertNull(service.chooseSubtitleStream("jpn", subs, null, disabled, prefs))
    }

    private fun service(): StreamChoiceService {
        val serverRepository = mockk<ServerRepository>()
        every { serverRepository.currentUserDto } returns
            ServerUserConfig(
                id = UUID.randomUUID(),
                configuration = DefaultUserConfiguration,
            )
        return StreamChoiceService(serverRepository, mockk<PlaybackLanguageChoiceDao>())
    }

    private fun prefs(
        experimental: Boolean = true,
        enabled: Boolean = true,
    ) = UserPreferences(
        AppPreferences
            .newBuilder()
            .setExperimentalPreferences(
                ExperimentalPreferences
                    .newBuilder()
                    .setEnabled(experimental)
                    .setSmartLanguageEnabled(enabled)
                    .addAllSmartAudioLanguages(audioLangs)
                    .addAllSmartSubtitleLanguages(subLangs)
                    .build(),
            ).build(),
        JellyfinUserPreferences(),
    )

    private fun itemPlayback(
        audioIndex: Int,
        subtitleIndex: Int,
    ) = ItemPlayback(
        rowId = 1,
        userId = 1,
        itemId = UUID.randomUUID(),
        sourceId = UUID.randomUUID(),
        audioIndex = audioIndex,
        subtitleIndex = subtitleIndex,
    )
}

private fun audioTrack(
    index: Int,
    lang: String?,
    default: Boolean = false,
    channels: Int? = 2,
): MediaStream =
    MediaStream(
        type = MediaStreamType.AUDIO,
        language = lang,
        isDefault = default,
        isForced = false,
        isHearingImpaired = false,
        isInterlaced = false,
        index = index,
        isExternal = false,
        isTextSubtitleStream = false,
        supportsExternalStream = false,
        channels = channels,
    )
