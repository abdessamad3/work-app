package com.prayerwakeup.app.data.voice

import android.content.Context
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.ScriptLine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the user's own recorded clips live on disk — one per [ScriptLine] category, plus one per
 * [Prayer] name (spliced in after the OPENING clip so it doesn't have to be re-recorded per
 * prayer). Recording is entirely optional and per-clip: any category without a file simply falls
 * back to synthesized speech in ConversationManager.
 */
@Singleton
class RecordedVoiceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val directory: File
        get() = File(context.filesDir, "recorded_voice").apply { mkdirs() }

    fun fileFor(line: ScriptLine): File = File(directory, "${line.name}.m4a")

    fun fileForPrayerName(prayer: Prayer): File = File(directory, "PRAYER_${prayer.name}.m4a")

    fun hasRecording(line: ScriptLine): Boolean = fileFor(line).exists()

    fun hasPrayerName(prayer: Prayer): Boolean = fileForPrayerName(prayer).exists()

    fun recordedCount(): Int =
        ScriptLine.entries.count { hasRecording(it) } + Prayer.entries.count { hasPrayerName(it) }

    fun totalCount(): Int = ScriptLine.entries.size + Prayer.entries.size
}
