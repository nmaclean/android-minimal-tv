package com.nmaclean.minimaltv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceAssistantManager(
    private val context: Context,
    private val onStatusChanged: (String, Boolean) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onStatusChanged("Listening...", true)
                    }
                    override fun onBeginningOfSpeech() {
                        onStatusChanged("Listening...", true)
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        onStatusChanged("Processing...", true)
                    }
                    override fun onError(error: Int) {
                        onStatusChanged("", false)
                    }
                    override fun onResults(results: Bundle?) {
                        onStatusChanged("", false)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull()?.trim() ?: ""
                        handleSpokenText(spokenText)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (_: Exception) {
            speechRecognizer = null
        }
    }

    fun handleAssistIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_ASSIST || action == Intent.ACTION_VOICE_COMMAND) {
            intent.action = null
            startListening()
        }
    }

    fun startListening() {
        onStatusChanged("Listening...", true)
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            onStatusChanged("", false)
        }
    }

    fun stopListening() {
        onStatusChanged("Processing...", true)
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
    }

    private fun handleSpokenText(spokenText: String) {
        if (spokenText.isNotEmpty()) {
            val query = if (spokenText.lowercase().contains("youtube")) {
                spokenText.lowercase().replace("youtube", "").trim()
            } else {
                spokenText
            }
            searchYouTube(query)
        }
    }

    /** Opens a YouTube (Android TV) search for [query]; returns false if YouTube isn't available. */
    fun searchYouTube(query: String): Boolean {
        val ytSearch = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
            setPackage("com.google.android.youtube.tv")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return try {
            context.startActivity(ytSearch)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}
