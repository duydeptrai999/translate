package com.duyth10.mlkit

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.Image
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import com.duyth10.mlkit.databinding.FragmentCameraBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.pow


class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private lateinit var imageCapture: ImageCapture
    private var capturedBitmap: Bitmap? = null
    private var currentTranslator: Translator? = null

    private var recognizedTextBlocks = mutableListOf<Quad>() // Các vị trí văn bản đã nhận dạng
    private var originalTextBlocks = mutableListOf<String>() // Văn bản gốc đã nhận dạng

    private lateinit var camera: Camera
    private lateinit var cameraControl: CameraControl
    private lateinit var cameraInfo: CameraInfo
    private lateinit var scaleGestureDetector: ScaleGestureDetector


    private val REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.CAMERA,


        )

    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startCamera()
        } else {
            Log.d("permissions", "Permissions denied")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestMultiplePermissions.launch(REQUIRED_PERMISSIONS)
        }

        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

        binding.closeButton.setOnClickListener {
            binding.capturedImageContainer.visibility = View.GONE
            binding.captureButton.visibility = View.VISIBLE
            startCamera()
        }

        binding.openGalleryButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    100
                )
            }
            openGallery()

        }

        binding.sourceLangSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    retranslateText()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

            }
        binding.targetLangSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>, view: View?, position: Int, id: Long
                ) {
                    retranslateText()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        binding.iconSwap.setOnClickListener {
            swapLanguages()
            retranslateText()
        }
//        setLatestImageToImageView(binding.openGalleryButton)

    }

    // chưa làm được hiển thị  ảnh cuối lên image
//    private fun setLatestImageToImageView(imageView: ImageView) {
//        val contentResolver: ContentResolver = requireContext().contentResolver
//        val imageUri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//
//        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
//        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC" // Sắp xếp theo thời gian thêm vào
//
//        contentResolver.query(imageUri, projection, null, null, sortOrder)?.use { cursor ->
//            if (cursor.moveToFirst()) {
//                // Lấy ID của ảnh
//                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
//                val id = cursor.getLong(idColumn)
//
//                // Tạo URI cho ảnh dựa vào ID
//                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
//
//                // Giải mã URI thành Bitmap
//                val bitmap: Bitmap? = MediaStore.Images.Media.getBitmap(contentResolver, uri)
//
//                // Hiển thị ảnh lên ImageView
//                imageView.setImageBitmap(bitmap)
//            }
//        }
//    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture =
                ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind tất cả các trường hợp sử dụng trước đó
                cameraProvider.unbindAll()

                // Bind lại preview, imageCapture và nhận đối tượng Camera
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo

                // Khởi tạo ScaleGestureDetector
                setupPinchToZoom()

            } catch (e: Exception) {
                Log.e("CameraFragment", "Failed to bind camera", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun setupPinchToZoom() {
        scaleGestureDetector = ScaleGestureDetector(requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val zoomState = cameraInfo.zoomState.value ?: return false
                    val currentZoomRatio = zoomState.zoomRatio
                    val delta = detector.scaleFactor
                    cameraControl.setZoomRatio(currentZoomRatio * delta)
                    return true
                }
            })

        // Lắng nghe các sự kiện cảm ứng trên PreviewView
        binding.previewView.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)

            // Check if the event is an ACTION_UP (indicating the end of a touch gesture)
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick() // Call performClick() to handle accessibility
            }

            true // Return true to indicate the event was handled
        }

    }


    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        // take picture truyền 3 tham số (call back lưu fail or success , executor , call back sử lý kết quả ảnh sau khi chụp)
        imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    capturedBitmap = bitmap
                    recognizeTextFromBitmap(bitmap)
                    displayCapturedImage(bitmap)
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraFragment", "Error capturing image", exception)
                }
            })
    }

    private fun displayCapturedImage(bitmap: Bitmap) {
        binding.capturedImageView.setImageBitmap(bitmap)
        binding.capturedImageContainer.visibility = View.VISIBLE
        binding.captureButton.visibility = View.GONE
        binding.openGalleryButton.visibility = View.GONE

    }


    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val planeProxy = imageProxy.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun recognizeTextFromBitmap(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image).addOnSuccessListener { visionText ->
            processTextRecognitionResult(visionText)

        }.addOnFailureListener { e ->
            Log.e("CameraFragment", "Text recognition failed", e)
        }
    }

    private fun processTextRecognitionResult(text: Text) {
        recognizedTextBlocks.clear()
        originalTextBlocks.clear()

        for (block in text.textBlocks) {
            val blockText = block.text // Lấy toàn bộ đoạn văn bản của block
            originalTextBlocks.add(blockText) // Lưu đoạn văn bản

            // Lấy các điểm góc của block để vẽ khung bao quanh
            val cornerPoints = block.cornerPoints
            if (cornerPoints != null && cornerPoints.size == 4) {
                recognizedTextBlocks.add(
                    Quad(
                        cornerPoints[0], cornerPoints[1], cornerPoints[2], cornerPoints[3]
                    )
                )
            }
        }

        retranslateText()
    }

    private fun translateText(
        sourceLang: String,
        targetLang: String,
        originalTexts: List<String>,
        recognizedTextElements: List<Quad>
    ) {
        val options =
            TranslatorOptions.Builder().setSourceLanguage(sourceLang).setTargetLanguage(targetLang)
                .build()

        currentTranslator?.close()

        val translator = Translation.getClient(options)

        translator.downloadModelIfNeeded().addOnSuccessListener {
            val translatedTexts = mutableListOf<String>()
            var completedTranslations = 0

            for ((index, text) in originalTexts.withIndex()) {
                translator.translate(text).addOnSuccessListener { translatedText ->
                    translatedTexts.add(translatedText)
                    completedTranslations++

                    // Khi dịch xong tất cả các đoạn, ta sẽ hiển thị
                    if (completedTranslations == originalTexts.size) {
                        drawTranslatedTextWithRotation(
                            recognizedTextElements, translatedTexts
                        )
                    }
                }.addOnFailureListener { e ->
                    Log.e("CameraFragment", "Translation failed", e)
                }
            }
        }.addOnFailureListener { e ->
            Log.e("CameraFragment", "Model download failed", e)
        }
    }

    private fun drawTranslatedTextWithRotation(
        recognizedTextElements: List<Quad>, translatedTexts: List<String>
    ) {
        // Tạo một bản sao của bitmap gốc để vẽ lên
        val bitmapWithTranslations = capturedBitmap?.copy(Bitmap.Config.ARGB_8888, true) ?: return
        val canvas = Canvas(bitmapWithTranslations)

        // Vẽ nền đen bán trong suốt phía sau văn bản
        val paintBackground = Paint().apply {
            color = Color.argb(180, 0, 0, 0) // Màu nền đen bán trong suốt
            style = Paint.Style.FILL
        }

        // Vẽ văn bản với màu trắng
        val paintText = Paint().apply {
            color = Color.WHITE // Màu văn bản
            textSize = 45f // Kích thước phông chữ
            textAlign = Paint.Align.LEFT
            style = Paint.Style.FILL
            setShadowLayer(1.5f, 1.0f, 1.0f, Color.BLACK) // Thêm hiệu ứng bóng cho văn bản
        }

        val padding = 20f // Thêm khoảng đệm để đảm bảo khung không bị dính vào văn bản

        for ((index, quad) in recognizedTextElements.withIndex()) {
            val translatedText = translatedTexts.getOrNull(index) ?: ""

            // Tính góc quay từ các điểm góc
            val deltaX = quad.topRight.x - quad.topLeft.x
            val deltaY = quad.topRight.y - quad.topLeft.y
            val angle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()

            // Tính chiều rộng và chiều cao của khung chữ dựa trên các điểm góc
            val width = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
            val height = Math.sqrt(
                ((quad.bottomLeft.x - quad.topLeft.x).toDouble()
                    .pow(2.0) + (quad.bottomLeft.y - quad.topLeft.y).toDouble().pow(2.0))
            ).toFloat()

            val centerX = (quad.topLeft.x + quad.bottomRight.x) / 2
            val centerY = (quad.topLeft.y + quad.bottomRight.y) / 2

            canvas.save()

            // Di chuyển canvas đến giữa khối văn bản
            canvas.translate(centerX.toFloat(), centerY.toFloat())

            // Xoay canvas theo góc của khối văn bản
            canvas.rotate(angle)

            // Vẽ hình chữ nhật nền quanh văn bản với khoảng đệm
            val left = -width / 2 - padding
            val top = -height / 2 - padding
            val right = width / 2 + padding
            val bottom = height / 2 + padding

            // Vẽ nền cho văn bản với khoảng đệm
            canvas.drawRect(left, top, right, bottom, paintBackground)

            // Điều chỉnh kích thước phông chữ nếu không vừa với khung chữ
            var currentTextSize = paintText.textSize
            val maxTextHeight = bottom - top - padding
            paintText.textSize = currentTextSize

            // Chia văn bản thành các dòng và điều chỉnh kích thước nếu cần
            var textLines = breakTextIntoLines(translatedText, right - left, paintText)
            while (textLines.size * (paintText.textSize + 10) > maxTextHeight && currentTextSize > 20) {
                currentTextSize -= 2f
                paintText.textSize = currentTextSize
                textLines = breakTextIntoLines(translatedText, right - left, paintText)
            }

            // Nếu văn bản vẫn không vừa, cắt ngắn và thêm "..."
            if (textLines.size * (paintText.textSize + 10) > maxTextHeight) {
                val truncatedLines = mutableListOf<String>()
                var currentHeight = 0f

                for (line in textLines) {
                    currentHeight += paintText.textSize + 10
                    if (currentHeight > maxTextHeight) {
                        if (truncatedLines.isNotEmpty()) {
                            truncatedLines[truncatedLines.size - 1] += "..."
                        }
                        break
                    }
                    truncatedLines.add(line)
                }
                textLines = truncatedLines
            }

            var currentY = top + 45f // Điều chỉnh vị trí Y bắt đầu cho văn bản

            for (line in textLines) {
                // Vẽ từng dòng văn bản với khoảng cách dòng
                canvas.drawText(
                    line, left + 10, currentY, paintText
                ) // Điều chỉnh vị trí X để căn chỉnh văn bản
                currentY += paintText.textSize + 10 // Khoảng cách giữa các dòng
            }

            // Khôi phục canvas sau khi xoay và dịch
            canvas.restore()
        }

        // Hiển thị hình ảnh đã được vẽ văn bản
        displayCapturedImage(bitmapWithTranslations)
    }



    private fun breakTextIntoLines(text: String, boxWidth: Float, paintText: Paint): List<String> {
        val lines = mutableListOf<String>()
        var currentLine = ""
        val words = text.split(" ")  // Tách văn bản thành các từ

        for (word in words) {
            // Kiểm tra nếu thêm từ này vào dòng hiện tại thì chiều rộng của dòng có vượt quá giới hạn không
            val potentialLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val lineWidth = paintText.measureText(potentialLine)

            if (lineWidth <= boxWidth) {
                // Nếu chiều rộng của dòng vẫn trong giới hạn thì thêm từ vào dòng
                currentLine = potentialLine
            } else {
                // Nếu chiều rộng vượt quá giới hạn thì kết thúc dòng hiện tại và bắt đầu dòng mới
                lines.add(currentLine)
                currentLine = word
            }
        }

        // Thêm dòng cuối cùng vào danh sách
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines
    }


    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Sử dụng ContentResolver để mở stream và giải mã thành Bitmap
            val inputStream = requireContext().contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            capturedBitmap = bitmap
            displayCapturedImage(bitmap)
            recognizeTextFromBitmap(bitmap)
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
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

    private fun retranslateText() {
        val sourceLang = getLanguageCode(binding.sourceLangSpinner.selectedItem.toString())
        val targetLang = getLanguageCode(binding.targetLangSpinner.selectedItem.toString())

        if (sourceLang != null && targetLang != null && originalTextBlocks.isNotEmpty()) {
            translateText(sourceLang, targetLang, originalTextBlocks, recognizedTextBlocks)
        }
    }

    private fun swapLanguages() {

        val sourceLangPosition = binding.sourceLangSpinner.selectedItemPosition
        val targetLangPosition = binding.targetLangSpinner.selectedItemPosition

        binding.sourceLangSpinner.setSelection(targetLangPosition)
        binding.targetLangSpinner.setSelection(sourceLangPosition)
    }

}
