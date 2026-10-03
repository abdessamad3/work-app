package com.prayerwakeup.app.data.voice

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Records one short clip at a time to a temp file, then commits it to its final destination. */
@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var tempFile: File? = null
    private var previewPlayer: MediaPlayer? = null

    fun start(): Boolean {
        cancel()
        val temp = File(context.cacheDir, "voice_recording_tmp.m4a")
        return runCatching {
            @Suppress("DEPRECATION")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(temp.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            tempFile = temp
            true
        }.getOrElse {
            recorder = null
            tempFile = null
            false
        }
    }

    /** Stops the in-progress recording and copies it to [destination]. Returns whether it saved. */
    fun stopAndSave(destination: File): Boolean {
        val stopped = runCatching { recorder?.apply { stop(); release() } }.isSuccess
        recorder = null
        val temp = tempFile
        tempFile = null
        if (!stopped || temp == null || !temp.exists()) {
            temp?.delete()
            return false
        }
        return runCatching {
            destination.parentFile?.mkdirs()
            temp.copyTo(destination, overwrite = true)
            temp.delete()
            true
        }.getOrElse { false }
    }

    fun cancel() {
        runCatching { recorder?.apply { stop(); release() } }
        recorder = null
        tempFile?.delete()
        tempFile = null
    }

    fun playPreview(file: File, onDone: () -> Unit) {
        stopPreview()
        val player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { release(); onDone() }
                setOnErrorListener { _, _, _ -> release(); onDone(); true }
                prepare()
                start()
            }
        }.getOrNull()
        if (player == null) {
            onDone()
        } else {
            previewPlayer = player
        }
    }

    fun stopPreview() {
        runCatching { previewPlayer?.stop(); previewPlayer?.release() }
        previewPlayer = null
    }
}
