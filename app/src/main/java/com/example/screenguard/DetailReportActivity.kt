package com.example.screenguard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.io.File
import kotlin.math.min

class DetailReportActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_report)

        val ivDetail: ImageView = findViewById(R.id.ivDetailProof)
        val tvTime: TextView = findViewById(R.id.tvValTime)
        val tvFaces: TextView = findViewById(R.id.tvValFaces)
        val tvLight: TextView = findViewById(R.id.tvValLight)
        val tvResp: TextView = findViewById(R.id.tvValResp)
        val tvfps: TextView = findViewById(R.id.tvValfps)
        tvTime.text = intent.getStringExtra("timestamp")
        tvFaces.text = intent.getStringExtra("face_count")
        tvLight.text = intent.getStringExtra("light_lux")
        tvResp.text = "${intent.getStringExtra("response_time")} ms"
        tvfps.text = intent.getStringExtra("fps")

        val path = intent.getStringExtra("image_path")
        if (!path.isNullOrEmpty()) {
            val imgFile = File(path)
            if (imgFile.exists()) {
                ivDetail.setImageBitmap(BitmapFactory.decodeFile(path))
                ivDetail.setOnClickListener { showImageDialog(path) }
            }
        }

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

// ====================================================================================
// KELAS ZOOM IMAGE VIEW TETAP SAMA (TIDAK ADA PERUBAHAN LOGIKA)
// ====================================================================================
class ZoomImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatImageView(context, attrs), ScaleGestureDetector.OnScaleGestureListener {

    private var mScaleDetector: ScaleGestureDetector = ScaleGestureDetector(context, this)
    private var mMatrix = Matrix()
    private var mMatrixValues = FloatArray(9)

    private var saveScale = 1f
    private var mode = 0

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var startTouchX = 0f
    private var startTouchY = 0f

    private var initialFitDone = false

    init {
        scaleType = ImageView.ScaleType.MATRIX
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
                val scale = min(viewW / drawW, viewH / drawH)
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
                if (mode == 1) {
                    val deltaX = currPointX - lastTouchX
                    val deltaY = currPointY - lastTouchY
                    mMatrix.postTranslate(deltaX, deltaY)
                    checkMatrixBounds()
                    lastTouchX = currPointX
                    lastTouchY = currPointY
                }
            }
            MotionEvent.ACTION_UP -> {
                mode = 0
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

        if (saveScale < 1f) {
            saveScale = 1f
            scaleFactor = 1f / prevScale
        } else if (saveScale > 5f) {
            saveScale = 5f
            scaleFactor = 5f / prevScale
        }

        if (saveScale == 1f) {
            fitToCenter()
        } else {
            mMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            checkMatrixBounds()
        }
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        mode = 2 // ZOOM
        return true
    }
    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    private fun checkMatrixBounds() {
        val rect = getRect()
        var deltaX = 0f
        var deltaY = 0f
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (rect.height() < viewHeight) {
            deltaY = (viewHeight - rect.height()) / 2 - rect.top
        } else {
            if (rect.top > 0) {
                deltaY = -rect.top
            } else if (rect.bottom < viewHeight) {
                deltaY = viewHeight - rect.bottom
            }
        }

        if (rect.width() < viewWidth) {
            deltaX = (viewWidth - rect.width()) / 2 - rect.left
        } else {
            if (rect.left > 0) {
                deltaX = -rect.left
            } else if (rect.right < viewWidth) {
                deltaX = viewWidth - rect.right
            }
        }
        mMatrix.postTranslate(deltaX, deltaY)
        imageMatrix = mMatrix
    }

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

    private fun getRect(): RectF {
        val r = RectF()
        if (drawable != null) {
            mMatrix.getValues(mMatrixValues)
            r.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
            mMatrix.mapRect(r)
        }
        return r
    }
}