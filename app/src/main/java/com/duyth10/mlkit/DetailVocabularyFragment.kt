package com.duyth10.mlkit

import android.content.ComponentName
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.duyth10.mlkit.model.DictionaryResponse
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DetailVocabularyFragment : Fragment() {
    private val viewModel: DetailVocabularyViewModel by viewModels()
    private lateinit var wordEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var saveNote: Button
    private lateinit var definitionTextView: TextView
    private lateinit var errorTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var volume: ImageView
    private var mediaPlayer: MediaPlayer? = null

    private var currentTranslator: Translator? = null

    var phoneticsTextSend: String? = ""
    var phoneticsAudioSend: String? = ""
    var wordSend: String? = ""
    var meaningsBasic: String? = ""


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_detail_vocabulary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)




        wordEditText = view.findViewById(R.id.wordEditText)
        searchButton = view.findViewById(R.id.searchButton)
        definitionTextView = view.findViewById(R.id.definitionTextView)
        progressBar = view.findViewById(R.id.progressBar)
        errorTextView = view.findViewById(R.id.errorTextView)
        volume = view.findViewById(R.id.volume)
        saveNote = view.findViewById(R.id.noteButton)

        wordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            ) {
                searchButton.performClick()
                true
            } else {
                false
            }
        }

        searchButton.setOnClickListener {
            val word = wordEditText.text.toString().trim()
            if (word.isNotEmpty()) {
                viewModel.fetchWordDefinition(word)

            } else {
                showToast("Please enter a word to search")
            }
        }

        viewModel.wordDefinition.observe(viewLifecycleOwner, Observer { resource ->
            when (resource) {
                is Resource.Success -> {
                    progressBar.visibility = View.GONE
                    errorTextView.visibility = View.GONE
                    definitionTextView.visibility = View.VISIBLE
                    volume.visibility = View.VISIBLE
                    saveNote.visibility = View.VISIBLE
                    resource.data?.let { displayWordDefinitions(it) }
                }

                is Resource.Error -> {
                    definitionTextView.visibility = View.GONE
                    errorTextView.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    volume.visibility = View.GONE
                    saveNote.visibility = View.GONE
                    showToast(resource.message ?: "Unknown error")
                }

                is Resource.Loading -> {
                    progressBar.visibility = View.VISIBLE
                }
            }
        })

        saveNote.setOnClickListener {


            val intent = Intent()
            intent.setComponent(
                ComponentName(
                    "com.duyth10.learnvocabulary",
                    "com.duyth10.learnvocabulary.MainActivity"
                )
            )

            intent.putExtra("wordSend", wordSend)
            intent.putExtra("phoneticsTextSend", phoneticsTextSend)
            intent.putExtra("phoneticsAudioSend", phoneticsAudioSend)
            intent.putExtra("meaningsBasic", meaningsBasic)

            requireActivity().startActivity(intent)
        }

    }

    private fun displayWordDefinitions(definitions: List<DictionaryResponse>) {
        val textBuilder = StringBuilder()

        definitions.forEach { dictionaryResponse ->
            wordSend = dictionaryResponse.word
            textBuilder.append("Word: ${dictionaryResponse.word}\n")

            phoneticsAudioSend = definitions.firstOrNull()?.phonetics?.firstOrNull { it.audio.isNotEmpty() }?.audio
            textBuilder.append("Phonetics:\n")
            dictionaryResponse.phonetics.firstOrNull()?.let { phonetic ->
                textBuilder.append("  - ${phonetic.text}\n")
                phoneticsTextSend = phonetic.text
            }
          translateText(wordSend!!){ meaning ->
            meaningsBasic = meaning
            }.toString()
            Log.d("meaningbacsic",meaningsBasic!!)

            textBuilder.append("Meanings:\n")
            dictionaryResponse.meanings.forEach { meaning ->
                textBuilder.append("- ${meaning.partOfSpeech}:\n")

                translateText(meaning.partOfSpeech) { partOfSpeech ->
                    textBuilder.append(" - $partOfSpeech\n")
                    definitionTextView.text = textBuilder.toString()
                }

                meaning.definitions.forEachIndexed { index, definition ->
                    textBuilder.append("    Definition: ${definition.definition}\n")

                    translateText(definition.definition) { translatedDefinition ->
                        textBuilder.append("     Definition: $translatedDefinition\n")
                        definitionTextView.text = textBuilder.toString()
                    }

                    definition.example?.let { example ->
                        textBuilder.append("    Example: $example\n")

                        translateText(example) { translatedExample ->
                            textBuilder.append("     Example: $translatedExample\n")
                            definitionTextView.text = textBuilder.toString()
                        }
                    }
                }
                textBuilder.append("\n")
            }
        }

        definitionTextView.text = textBuilder.toString()

        volume.setOnClickListener {
            val audioUrl =
                definitions.firstOrNull()?.phonetics?.firstOrNull { it.audio.isNotEmpty() }?.audio
            if (audioUrl != null) {
                Log.d("DetailVocabularyFragment", "Audio URL found: $audioUrl")
                playAudioFromUrl(audioUrl)
            } else {
                Log.d("DetailVocabularyFragment", "No audio URL found")
                Toast.makeText(
                    requireContext(),
                    "No audio available for this word",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun translateText(text: String, onTranslationComplete: (String) -> Unit) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.VIETNAMESE)
            .build()

        currentTranslator?.close()
        val translator: Translator = Translation.getClient(options)

        translator.downloadModelIfNeeded().addOnSuccessListener {
            translator.translate(text).addOnSuccessListener { translatedText ->
                onTranslationComplete(translatedText)
            }.addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(), "Lỗi dịch: ${e.message}", Toast.LENGTH_SHORT
                ).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(
                requireContext(), "Lỗi tải ngôn ngữ: ${e.message}", Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun playAudioFromUrl(url: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    Toast.makeText(requireContext(), "Audio completed", Toast.LENGTH_SHORT).show()
                }
                setOnErrorListener { _, what, extra ->
                    Toast.makeText(
                        requireContext(),
                        "Error playing audio: what=$what extra=$extra",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("DetailVocabularyFragment", "Error in playAudioFromUrl: ${e.message}", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        mediaPlayer = null
    }

}
