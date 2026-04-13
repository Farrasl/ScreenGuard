package com.example.screenguard

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.math.min

class DetailReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val tvTitle = TextView(this).apply {
            text = "Detail Ancaman"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 30)
        }

        val ivDetail = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, 0, 30)
        }

        val tvInfo = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setLineSpacing(12f, 1f)
        }

        val timestamp = intent.getStringExtra("timestamp")
        val faces = intent.getStringExtra("face_count")
        val response = intent.getStringExtra("response_time")
        val fps = intent.getStringExtra("fps")
        val path = intent.getStringExtra("image_path")

        tvInfo.text = """
            📅 Waktu: $timestamp
            👥 Jumlah Wajah: $faces
            ⚡ Respons Sistem: $response ms
            📷 FPS Rata-rata: $fps            
            
            (Ketuk gambar untuk tampilan layar penuh & zoom)
        """.trimIndent()

        if (!path.isNullOrEmpty()) {
            val imgFile = File(path)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                ivDetail.setImageBitmap(bitmap)

                ivDetail.setOnClickListener {
                    showImageDialog(path)
                }
            }
        }

        layout.addView(tvTitle)
        layout.addView(ivDetail)
        layout.addView(tvInfo)

        setContentView(layout)

        supportActionBar?.title = "ScreenGuard Detail"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun showImageDialog(path: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dialogImageView = ZoomImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setImageBitmap(BitmapFactory.decodeFile(path))
            setBackgroundColor(Color.BLACK)
        }

        // Klik sekali (tap) untuk menutup dialog
        dialogImageView.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(dialogImageView)
        dialog.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class ZoomImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatImageView(context, attrs), ScaleGestureDetector.OnScaleGestureListener {

    private var mScaleDetector: ScaleGestureDetector = ScaleGestureDetector(context, this)
    private var mMatrix = Matrix()
    private var mMatrixValues = FloatArray(9)

    // Variabel state
    private var saveScale = 1f
    private var mode = 0

    // Variabel sentuhan
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var startTouchX = 0f
    private var startTouchY = 0f

    private var initialFitDone = false

    init {
        scaleType = ImageView.ScaleType.MATRIX
        // Mengaktifkan clickable agar onClickListener (untuk dismiss dialog) berfungsi
        isClickable = true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!initialFitDone && drawable != null) {
            val d = drawable
            val viewW = width.toFloat()
            val viewH = height.toFloat()
            val drawW = d.intrinsicWidth.toFloat()
            val drawH = d.intrinsicHeight.toFloat()

            if (drawW > 0 && drawH > 0) {
                // Kalkulasi skala agar pas di layar (Fit Center)
                val scale = min(viewW / drawW, viewH / drawH)

                // Kalkulasi posisi tengah
                val dx = (viewW - drawW * scale) / 2f
                val dy = (viewH - drawH * scale) / 2f

                mMatrix.setScale(scale, scale)
                mMatrix.postTranslate(dx, dy)

                imageMatrix = mMatrix
                saveScale = 1f
                initialFitDone = true
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mScaleDetector.onTouchEvent(event)

        val currPointX = event.x
        val currPointY = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = currPointX
                lastTouchY = currPointY
                startTouchX = currPointX
                startTouchY = currPointY
                mode = 1 // DRAG
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == 1) { // Sedang menggeser
                    val deltaX = currPointX - lastTouchX
                    val deltaY = currPointY - lastTouchY

                    // Lakukan pergeseran (sementara)
                    mMatrix.postTranslate(deltaX, deltaY)

                    // LANGSUNG PERBAIKI POSISI (Cek Batas)
                    checkMatrixBounds()

                    lastTouchX = currPointX
                    lastTouchY = currPointY
                }
            }
            MotionEvent.ACTION_UP -> {
                mode = 0
                // Deteksi Klik (Tap) untuk menutup dialog
                val xDiff = Math.abs(currPointX - startTouchX)
                val yDiff = Math.abs(currPointY - startTouchY)
                if (xDiff < 10 && yDiff < 10) {
                    performClick()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                mode = 0
            }
        }
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        var scaleFactor = detector.scaleFactor
        val prevScale = saveScale
        saveScale *= scaleFactor

        // Batas Zoom: Min 1x, Max 5x
        if (saveScale < 1f) {
            saveScale = 1f
            scaleFactor = 1f / prevScale
        } else if (saveScale > 5f) {
            saveScale = 5f
            scaleFactor = 5f / prevScale
        }

        // Jika gambar pas layar (1x), paksa ke tengah dan reset zoom
        if (saveScale == 1f) {
            fitToCenter()
        } else {
            mMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            checkMatrixBounds() // Cek batas setelah zoom
        }

        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        mode = 2 // ZOOM
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    // --- FUNGSI UTAMA PENGECEKAN BATAS (NO BLACK GAP) ---
    private fun checkMatrixBounds() {
        val rect = getRect() // Ambil koordinat gambar saat ini
        var deltaX = 0f
        var deltaY = 0f

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // --- Logika Vertikal (Y) ---
        if (rect.height() < viewHeight) {
            // Jika gambar lebih kecil dari tinggi layar -> Selalu letakkan di tengah vertikal
            deltaY = (viewHeight - rect.height()) / 2 - rect.top
        } else {
            // Jika gambar lebih tinggi dari layar -> Jangan biarkan ada celah di atas/bawah
            if (rect.top > 0) {
                deltaY = -rect.top // Koreksi: Geser ke atas jika ada celah di atas
            } else if (rect.bottom < viewHeight) {
                deltaY = viewHeight - rect.bottom // Koreksi: Geser ke bawah jika ada celah di bawah
            }
        }

        // --- Logika Horizontal (X) ---
        if (rect.width() < viewWidth) {
            // Jika gambar lebih kecil dari lebar layar -> Selalu letakkan di tengah horizontal
            deltaX = (viewWidth - rect.width()) / 2 - rect.left
        } else {
            // Jika gambar lebih lebar dari layar -> Jangan biarkan ada celah di kiri/kanan
            if (rect.left > 0) {
                deltaX = -rect.left // Koreksi: Geser ke kiri
            } else if (rect.right < viewWidth) {
                deltaX = viewWidth - rect.right // Koreksi: Geser ke kanan
            }
        }

        // Terapkan koreksi
        mMatrix.postTranslate(deltaX, deltaY)
        imageMatrix = mMatrix
    }

    // Mengembalikan gambar ke posisi awal (Fit Center)
    private fun fitToCenter() {
        if (drawable == null) return
        val d = drawable
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val drawW = d.intrinsicWidth.toFloat()
        val drawH = d.intrinsicHeight.toFloat()

        val scale = min(viewW / drawW, viewH / drawH)
        val dx = (viewW - drawW * scale) / 2f
        val dy = (viewH - drawH * scale) / 2f

        mMatrix.setScale(scale, scale)
        mMatrix.postTranslate(dx, dy)
        imageMatrix = mMatrix
        saveScale = 1f
    }

    // Helper untuk mendapatkan koordinat gambar (RectF) dari Matrix saat ini
    private fun getRect(): RectF {
        val r = RectF()
        if (drawable != null) {
            mMatrix.getValues(mMatrixValues)
            // intrinsicWidth/Height adalah ukuran asli gambar (pixel resolusi asli)
            r.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
            mMatrix.mapRect(r)
        }
        return r
    }
}