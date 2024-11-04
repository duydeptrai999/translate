package com.duyth10.mlkit

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.text.Text

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint để bôi đen văn bản
    private val paint = Paint().apply {
        color = Color.BLACK // Màu đen để che văn bản
        style = Paint.Style.FILL
    }

    // Biến lưu trữ các khối văn bản và kích thước ảnh gốc
    private var textBlocks: List<Text.TextBlock>? = null
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    // Matrix để xử lý việc chuyển đổi tọa độ
    private val matrix = Matrix()

    // Hàm cập nhật các khối văn bản và kích thước hình ảnh gốc
    fun updateTextBlocks(textBlocks: List<Text.TextBlock>, imageWidth: Int, imageHeight: Int) {
        this.textBlocks = textBlocks
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight

        // Tính toán tỉ lệ chuyển đổi từ kích thước ảnh gốc sang kích thước của view
        calculateTransformationMatrix()
        invalidate() // Yêu cầu vẽ lại view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Nếu không có textBlocks, thoát ra
        if (textBlocks == null) return

        // Duyệt qua tất cả các khối văn bản (block)
        for (block in textBlocks!!) {
            // Duyệt qua các dòng văn bản (line)
            for (line in block.lines) {
                // Lấy các điểm góc của dòng văn bản
                val cornerPoints = line.cornerPoints
                if (cornerPoints != null && cornerPoints.size == 4) {
                    // Tạo một mảng để chứa các điểm sau khi áp dụng Matrix
                    val transformedPoints = FloatArray(8) // 4 điểm (x, y) = 8 phần tử

                    // Chuyển đổi điểm góc thành tọa độ float và áp dụng Matrix
                    for (i in cornerPoints.indices) {
                        transformedPoints[i * 2] = cornerPoints[i].x.toFloat()
                        transformedPoints[i * 2 + 1] = cornerPoints[i].y.toFloat()
                    }

                    // Áp dụng phép biến đổi Matrix cho các điểm góc
                    matrix.mapPoints(transformedPoints)

                    // Tạo một Path để vẽ đa giác bôi đen theo các điểm góc đã chuyển đổi
                    val path = Path()
                    path.moveTo(transformedPoints[0], transformedPoints[1])

                    // Nối các điểm góc thành một đa giác
                    for (i in 1 until cornerPoints.size) {
                        path.lineTo(transformedPoints[i * 2], transformedPoints[i * 2 + 1])
                    }

                    // Đóng đường path để tạo thành đa giác
                    path.close()

                    // Vẽ đa giác lên canvas để che văn bản
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    // Tính toán Matrix để chuyển đổi từ ảnh gốc sang kích thước của view
    private fun calculateTransformationMatrix() {
        matrix.reset()

        // Tính tỉ lệ giữa kích thước ảnh và view
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val scaleX = viewWidth / imageWidth.toFloat()
        val scaleY = viewHeight / imageHeight.toFloat()

        // Áp dụng tỉ lệ theo cả chiều ngang và chiều dọc
        matrix.setScale(scaleX, scaleY)
    }
}
