package com.duyth10.mlkit

import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.duyth10.mlkit.databinding.FragmentMainBinding
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private var currentTranslator: Translator? = null

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerLauncher: ActivityResultLauncher<Intent>

    private var inputTextBlocks: String? = null

    private var textToSpeechInputText: TextToSpeech? = null
    private var textToSpeechOutputText: TextToSpeech? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        _binding?.let { binding ->
        textToSpeechInputText = TextToSpeech(requireContext(), TextToSpeech.OnInitListener {
            textToSpeechInputText?.let { it1 ->
                onInit(it, binding.sourceLangSpinner.selectedItem.toString(),
                    it1
                )
            }
        })
        textToSpeechOutputText = TextToSpeech(requireContext(), TextToSpeech.OnInitListener {
            textToSpeechOutputText?.let { it1 ->
                onInit(it, binding.targetLangSpinner.selectedItem.toString(),
                    it1
                )
            }
        })
            }

        binding.iconSwap.setOnClickListener {
            swapLanguages()
            retranslateText()
        }

        binding.btnCamera.setOnClickListener {
            (activity as MainActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CameraFragment()).addToBackStack(null).commit()
        }

        binding.inputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val sourceLang = getLanguageCode(binding.sourceLangSpinner.selectedItem.toString())
                val targetLang = getLanguageCode(binding.targetLangSpinner.selectedItem.toString())

                if (sourceLang != null && targetLang != null) {
                    translateText(sourceLang, targetLang, binding.inputText.text.toString())
                } else {
                    Toast.makeText(requireContext(), "Ngôn ngữ không hợp lệ", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            override fun afterTextChanged(s: Editable?) {
                retranslateText()

            }
        })

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())

        speechRecognizerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val resultText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                resultText?.get(0)?.let { spokenText ->

                    val sourceLang =
                        getLanguageCode(binding.sourceLangSpinner.selectedItem.toString())
                    val targetLang =
                        getLanguageCode(binding.targetLangSpinner.selectedItem.toString())

                    inputTextBlocks = spokenText
                    binding.inputText.setText(spokenText)
                    if (sourceLang != null) {
                        if (targetLang != null) {
                            translateText(sourceLang, targetLang, spokenText)
                        }
                    }
                }
            }
        }
        binding.btnMic.setOnClickListener {
            startSpeechToText()
        }

        binding.targetLangSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    retranslateText()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    TODO("Not yet implemented")
                }
            }
        binding.speaker.setOnClickListener {
            val languageCode = binding.sourceLangSpinner.selectedItem.toString()
            setTextToSpeechLanguage(languageCode,textToSpeechInputText!!)

            val text = binding.inputText.text.toString()
            if (text.isNotEmpty()) {
                speak(text, textToSpeechInputText!!)
            }
        }
        binding.speaker1.setOnClickListener {
            val languageCode = binding.targetLangSpinner.selectedItem.toString()
            setTextToSpeechLanguage(languageCode,textToSpeechOutputText!!)

            val text = binding.outputText.text.toString()
            if (text.isNotEmpty()) {
                speak(text, textToSpeechOutputText!!)
            }
        }

        binding.copyInput.setOnClickListener{
            val textToCopy = binding.inputText.text.toString()
            copyText(textToCopy)
        }
        binding.copyOutput.setOnClickListener{
            val textToCopy = binding.outputText.text.toString()
            copyText(textToCopy)
        }


    }

    private fun copyText(textCopy: String){
        if (textCopy.isNotEmpty()) {
            // Tạo ClipboardManager
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            // Tạo ClipData chứa văn bản cần sao chép
            val clip = ClipData.newPlainText("Copied Text", textCopy)

            // Đặt văn bản vào clipboard
            clipboard.setPrimaryClip(clip)

            // Thông báo cho người dùng biết đã sao chép thành công
            Toast.makeText(requireContext(), "Văn bản đã được sao chép", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Không có văn bản để sao chép", Toast.LENGTH_SHORT).show()
        }
    }
    private fun setTextToSpeechLanguage(languageCode: String, textToSpeech: TextToSpeech) {
        // Kiểm tra và thiết lập ngôn ngữ
        val locale = when (languageCode) {
            "English" -> Locale.US
            "Vietnamese" -> Locale("vi", "VN")
            "French" -> Locale.FRENCH
            "Spanish" -> Locale("es", "ES")
            "Japanese" -> Locale.JAPANESE
            else -> null
        }

        val result = locale?.let { textToSpeech.setLanguage(it) }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("MainFragment", "Ngôn ngữ không được hỗ trợ")
        }
    }

    private fun onInit(status: Int, languageCode: String,textToSpeech: TextToSpeech) {
        if (status == TextToSpeech.SUCCESS) {

            setTextToSpeechLanguage(languageCode,textToSpeech)
        } else {
            Log.e("MainFragment", "Khởi tạo TextToSpeech thất bại")
        }
    }

    private fun speak(text: String,textToSpeech: TextToSpeech) {
        textToSpeech?.let {
            if (it.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE) {
                it.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                Log.e("MainFragment", "Ngôn ngữ không khả dụng để phát âm")
            }
        }
    }


    private fun getLanguageCode(language: String): String? {
        return when (language) {
            "English" -> TranslateLanguage.ENGLISH
            "Vietnamese" -> TranslateLanguage.VIETNAMESE
            "French" -> TranslateLanguage.FRENCH
            "Spanish" -> TranslateLanguage.SPANISH
            "Japanese" -> TranslateLanguage.JAPANESE
            else -> null
        }
    }

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something")
        }

        // Sử dụng ActivityResultLauncher để bắt đầu nhận diện giọng nói
        speechRecognizerLauncher.launch(intent)
    }


    private fun translateText(sourceLang: String, targetLang: String, text: String) {
        val options =
            TranslatorOptions.Builder().setSourceLanguage(sourceLang).setTargetLanguage(targetLang)
                .build()

        // Hủy translator cũ để tiết kiệm tài nguyên
        currentTranslator?.close()

        val translator: Translator = Translation.getClient(options)

        // Tải ngôn ngữ nếu cần
        translator.downloadModelIfNeeded().addOnSuccessListener {
                val text = binding.inputText.text.toString()

                // Dịch văn bản
                translator.translate(text).addOnSuccessListener { translatedText ->
                        binding.outputText.text = translatedText
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


    private fun swapLanguages() {

        val sourceLangPosition = binding.sourceLangSpinner.selectedItemPosition
        val targetLangPosition = binding.targetLangSpinner.selectedItemPosition

        binding.sourceLangSpinner.setSelection(targetLangPosition)
        binding.targetLangSpinner.setSelection(sourceLangPosition)

        val currentInputText = binding.inputText.text.toString()
        val currentOutputText = binding.outputText.text.toString()

        binding.inputText.setText(currentOutputText)
        binding.outputText.text = currentInputText


    }

    private fun retranslateText() {
        val sourceLang = getLanguageCode(binding.sourceLangSpinner.selectedItem.toString())
        val targetLang = getLanguageCode(binding.targetLangSpinner.selectedItem.toString())

        val textToTranslate = binding.inputText.text.toString()

        if (sourceLang != null && targetLang != null && textToTranslate.isNotEmpty()) {
            translateText(sourceLang, targetLang, textToTranslate)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        currentTranslator?.close()
        textToSpeechInputText?.shutdown()
        _binding = null
    }

}
