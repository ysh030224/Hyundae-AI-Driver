package com.example.myapplication.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

class UncleVoiceSpeaker(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var tts: TextToSpeech? = null
    private var isLocalTtsReady = false
    private var mediaPlayer: MediaPlayer? = null

    init {
        ensureMaxVolume()
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.KOREAN)
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setPitch(0.92f)
                        tts?.setSpeechRate(0.95f)
                        isLocalTtsReady = true
                    }
                }
            }
        } catch (_: Exception) {
            isLocalTtsReady = false
        }
    }

    private fun ensureMaxVolume() {
        try {
            val streams = listOf(
                AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_ALARM,
                AudioManager.STREAM_SYSTEM
            )
            for (stream in streams) {
                val max = audioManager?.getStreamMaxVolume(stream) ?: continue
                val cur = audioManager.getStreamVolume(stream)
                if (cur < (max * 0.85).toInt()) {
                    audioManager.setStreamVolume(stream, (max * 0.9).toInt(), 0)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun speak(text: String, isAlert: Boolean = false) {
        ensureMaxVolume()
        scope.launch {
            playInCarChime(isAlert)

            if (isLocalTtsReady && tts != null) {
                val res = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "uncle_${System.currentTimeMillis()}")
                if (res == TextToSpeech.SUCCESS) return@launch
            }

            playCloudKoreanTts(text)
        }
    }

    private fun playCloudKoreanTts(text: String) {
        try {
            val cacheDir = File(context.cacheDir, "uncle_voice_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val fileKey = text.hashCode().toUInt().toString(16)
            val cachedMp3 = File(cacheDir, "voice_$fileKey.mp3")

            if (!cachedMp3.exists() || cachedMp3.length() < 200L) {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encoded&tl=ko&client=tw-ob"
                val conn = (URL(ttsUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { input ->
                        FileOutputStream(cachedMp3).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                conn.disconnect()
            }

            if (cachedMp3.exists() && cachedMp3.length() > 200L) {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                    } catch (_: Exception) {}

                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        setDataSource(cachedMp3.absolutePath)
                        prepare()
                        start()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun playInCarChime(isAlert: Boolean) {
        try {
            val sampleRate = 24000
            val durationMs = if (isAlert) 320 else 280
            val numSamples = (sampleRate * durationMs) / 1000
            val pcm = ShortArray(numSamples)

            val freq1 = if (isAlert) 880.0 else 587.33
            val freq2 = if (isAlert) 659.25 else 880.0

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val freq = if (i < numSamples / 2) freq1 else freq2
                val localIdx = if (i < numSamples / 2) i else (i - numSamples / 2)
                val halfLen = (numSamples / 2).toDouble()
                val envelope = (1.0 - (localIdx / halfLen)).coerceIn(0.0, 1.0)
                val sample = sin(2.0 * PI * freq * t) * envelope * 0.45
                pcm[i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }

            val bufferSize = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()
        } catch (_: Exception) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        try {
            tts?.stop()
            mediaPlayer?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
            mediaPlayer?.release()
        } catch (_: Exception) {}
    }
}
