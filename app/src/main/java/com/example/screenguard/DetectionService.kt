package com.example.screenguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@ExperimentalGetImage
class DetectionService : LifecycleService() {

    private val NOTIFICATION_ID = 101
    private val CHANNEL_ID = "ShoulderSurfingChannel"
    private val ALERT_CHANNEL_ID = "SecurityAlertChannel"

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var prefs: PreferencesHelper

    private val devicePolicyManager: DevicePolicyManager by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val deviceAdminComponent: ComponentName by lazy { ComponentName(this, MyDeviceAdminReceiver::class.java) }

    private var frameCount = 0
    private var startTime: Long = 0
    private var isLocking = false

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var currentLightLevel: Float = 0f

    private val lightListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                currentLightLevel = event.values[0]
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    companion object {
        @Volatile var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        isServiceRunning = true
        prefs = PreferencesHelper(this)
        startTime = System.currentTimeMillis()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        lightSensor?.let {
            sensorManager.registerListener(lightListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startDetectionForeground()
        return START_STICKY
    }

    private fun startDetectionForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Shoulder Surfing Monitor", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScreenGuard Aktif")
            .setContentText("Memantau tatapan mencurigakan ke layar...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        startCameraAndDetection()
    }

    private fun startCameraAndDetection() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val faceDetector = FaceDetection.getClient(FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.15f)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build())

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (isLocking) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val inferenceStart = System.currentTimeMillis()
                frameCount++

                imageProxy.image?.let { mediaImage ->
                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    faceDetector.process(inputImage)
                        .addOnSuccessListener { faces ->

                            var maxFaceAreaRatio = 0f

                            val peopleFacingAtScreen = faces.filter { face ->
                                val hasFaceStructure = face.getLandmark(FaceLandmark.NOSE_BASE) != null ||
                                        face.getLandmark(FaceLandmark.MOUTH_BOTTOM) != null

                                val isFacingScreen = Math.abs(face.headEulerAngleY) < 36

                                val eyeThreshold = if (currentLightLevel < 50f) {
                                    0.4f
                                } else {
                                    0.8f
                                }
                                val leftEye = face.leftEyeOpenProbability ?: 0f
                                val rightEye = face.rightEyeOpenProbability ?: 0f
                                val isEyeOpen = (leftEye > eyeThreshold || rightEye > eyeThreshold)

                                // Filter jarak via ukuran bounding box
                                val frameWidth = imageProxy.width.toFloat()
                                val frameHeight = imageProxy.height.toFloat()
                                val faceWidth = face.boundingBox.width().toFloat()
                                val faceHeight = face.boundingBox.height().toFloat()

                                // Rasio luas wajah terhadap frame
                                val faceAreaRatio = (faceWidth * faceHeight) / (frameWidth * frameHeight)

                                val isCloseEnough = faceAreaRatio > 0.005f

                                hasFaceStructure && isFacingScreen && isEyeOpen && isCloseEnough
                            }.size

                            if (peopleFacingAtScreen > 1 && !isLocking) {
                                isLocking = true

                                val screenshotPath = saveImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                triggerSecurityAction()

                                val responseTime = System.currentTimeMillis() - inferenceStart
                                logDetectionData(peopleFacingAtScreen, responseTime, calculateFps(), screenshotPath, currentLightLevel)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("ScreenGuard", "Face detection failed", e)
                        }
                        .addOnCompleteListener { imageProxy.close() }
                } ?: imageProxy.close()
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)
            } catch (e: Exception) {
                Log.e("ScreenGuard", "Binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun calculateFps(): Float {
        val elapsed = System.currentTimeMillis() - startTime
        return if (elapsed > 0) frameCount * 1000f / elapsed else 0f
    }

    private fun logDetectionData(faceCount: Int, responseTime: Long, fps: Float, screenshotPath: String?, lux: Float) {
        val lightDesc = when {
            lux < 50 -> "Gelap (%.1f Lux)".format(lux)
            lux < 300 -> "Normal (%.1f Lux)".format(lux)
            else -> "Terang (%.1f Lux)".format(lux)
        }

        val logMap = mapOf(
            "timestamp" to java.text.DateFormat.getDateTimeInstance().format(java.util.Date()),
            "face_count" to faceCount,
            "response_time_ms" to responseTime,
            "fps_avg" to "%.2f".format(fps),
            "screenshot_path" to (screenshotPath ?: ""),
            "light_lux" to lightDesc,
        )
        prefs.addDetectionLog(logMap)
    }

    private fun saveImage(mediaImage: android.media.Image, rotation: Int): String? {
        try {
            val yBuffer = mediaImage.planes[0].buffer
            val uBuffer = mediaImage.planes[1].buffer
            val vBuffer = mediaImage.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 90, out)

            val imageBytes = out.toByteArray()
            val rawBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            val matrix = Matrix().apply {
                postRotate(rotation.toFloat())
                postScale(-1f, 1f)
            }
            val finalBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)

            val fileName = "proof_${System.currentTimeMillis()}.jpg"
            val outputFile = File(filesDir, fileName)

            FileOutputStream(outputFile).use {
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            return outputFile.absolutePath
        } catch (e: Exception) {
            Log.e("ScreenGuard", "Gagal simpan gambar: ${e.message}")
            return null
        }
    }

    private fun triggerSecurityAction() {
        val mode = prefs.getPreventionMode()

        if (mode == PreferencesHelper.MODE_LOCK) {
            if (devicePolicyManager.isAdminActive(deviceAdminComponent)) {
                devicePolicyManager.lockNow()
                stopSelf()
            } else {
                isLocking = false
            }
        } else {
            showSecurityAlertNotification()
            Handler(Looper.getMainLooper()).postDelayed({
                isLocking = false
            }, 5000)
        }
    }

    private fun showSecurityAlertNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi saat ancaman shoulder surfing terdeteksi"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning) // Ikon peringatan
            .setContentTitle("⚠️ BAHAYA TERDETEKSI!")
            .setContentText("Ada orang lain yang melihat layar Anda!")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Agar muncul pop-up (Heads-up)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(999, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        cameraExecutor.shutdown()
        // Hentikan sensor cahaya agar hemat baterai saat service mati
        sensorManager.unregisterListener(lightListener)
    }
}