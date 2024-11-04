package com.duyth10.mlkit

import android.graphics.Point

// lưu trữ 4 điểm góc của mỗi ký tự
data class Quad(val topLeft: Point, val topRight: Point, val bottomRight: Point, val bottomLeft: Point)

