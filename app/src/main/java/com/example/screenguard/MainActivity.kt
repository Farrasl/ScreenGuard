package com.example.screenguard

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
// PENTING: Menggunakan SwitchCompat untuk mencocokkan XML
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.ExperimentalGetImage
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    // Perubahan tipe data dari Switch ke SwitchCompat
    private lateinit var switchProtection: SwitchCompat
    private lateinit var statusAdminTextView: TextView
    private lateinit var btnViewReport: Button

    private lateinit var rgPreventionMode: RadioGroup
    private lateinit var rbLock: RadioButton
    private lateinit var rbNotification: RadioButton

    private lateinit var prefs: PreferencesHelper
    private val ADMIN_INTENT_REQUEST = 1001

    private val devicePolicyManager: DevicePolicyManager by lazy {
        getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val deviceAdminComponent: ComponentName by lazy {
        // Pastikan nama class Receiver sesuai dengan yang ada di AndroidManifest.xml
        ComponentName(this, MyDeviceAdminReceiver::class.java)
    }

    private val requiredPermissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.FOREGROUND_SERVICE,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                checkDeviceAdminPermission()
            } else {
                Toast.makeText(this, "Izin diperlukan agar proteksi dapat berjalan.", Toast.LENGTH_LONG).show()
                switchProtection.isChecked = false
            }
        }

    @SuppressLint("UnsafeOptInUsageError")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesHelper(this)

        // Inisialisasi menggunakan SwitchCompat
        switchProtection = findViewById(R.id.switchProtection)
        statusAdminTextView = findViewById(R.id.tv_status_admin)
        btnViewReport = findViewById(R.id.btn_view_report)

        rgPreventionMode = findViewById(R.id.rg_prevention_mode)
        rbLock = findViewById(R.id.rb_lock)
        rbNotification = findViewById(R.id.rb_notification)

        val currentMode = prefs.getPreventionMode()
        if (currentMode == PreferencesHelper.MODE_NOTIFICATION) {
            rbNotification.isChecked = true
        } else {
            rbLock.isChecked = true
        }

        rgPreventionMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_notification) {
                PreferencesHelper.MODE_NOTIFICATION
            } else {
                PreferencesHelper.MODE_LOCK
            }
            prefs.setPreventionMode(mode)
            checkDeviceAdminStatus()
        }

        // Status awal switch berdasarkan service yang berjalan
        switchProtection.isChecked = prefs.isProtectionEnabled() && DetectionService.isServiceRunning

        switchProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.setProtectionEnabled(isChecked)
            if (isChecked) {
                if (allPermissionsGranted()) {
                    checkDeviceAdminPermission()
                } else {
                    permissionLauncher.launch(requiredPermissions)
                }
            } else {
                stopProtection()
            }
            checkDeviceAdminStatus()
        }

        btnViewReport.setOnClickListener {
            val intent = Intent(this, ReportActivity::class.java)
            startActivity(intent)
        }

        checkDeviceAdminStatus()
    }

    @OptIn(ExperimentalGetImage::class)
    override fun onResume() {
        super.onResume()
        switchProtection.isChecked = DetectionService.isServiceRunning && prefs.isProtectionEnabled()
        checkDeviceAdminStatus()
    }

    private fun checkDeviceAdminPermission() {
        val isLockMode = prefs.getPreventionMode() == PreferencesHelper.MODE_LOCK

        if (isLockMode && !devicePolicyManager.isAdminActive(deviceAdminComponent)) {
            AlertDialog.Builder(this)
                .setTitle("Izin Device Admin")
                .setMessage("Aplikasi memerlukan izin Device Admin untuk mengunci layar otomatis.")
                .setPositiveButton("Izinkan") { _, _ -> requestDeviceAdminPermission() }
                .setNegativeButton("Batal") { _, _ -> switchProtection.isChecked = false }
                .setCancelable(false)
                .show()
        } else {
            startProtection()
        }
    }

    private fun requestDeviceAdminPermission() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Proteksi layar aktif.")
        }
        startActivityForResult(intent, ADMIN_INTENT_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADMIN_INTENT_REQUEST) {
            if (resultCode == RESULT_OK) startProtection() else switchProtection.isChecked = false
            checkDeviceAdminStatus()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startProtection() {
        val isLockMode = prefs.getPreventionMode() == PreferencesHelper.MODE_LOCK
        val isAdminActive = devicePolicyManager.isAdminActive(deviceAdminComponent)

        if (allPermissionsGranted() && (!isLockMode || isAdminActive)) {
            if (!DetectionService.isServiceRunning) {
                val intent = Intent(this, DetectionService::class.java)
                ContextCompat.startForegroundService(this, intent)
            }
        } else {
            switchProtection.isChecked = false
            if (!allPermissionsGranted()) {
                permissionLauncher.launch(requiredPermissions)
            } else if (isLockMode && !isAdminActive) {
                checkDeviceAdminPermission()
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun stopProtection() {
        if (DetectionService.isServiceRunning) {
            stopService(Intent(this, DetectionService::class.java))
        }
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkDeviceAdminStatus() {
        val isLockMode = prefs.getPreventionMode() == PreferencesHelper.MODE_LOCK

        // Selalu tampilkan status agar user tidak bingung
        statusAdminTextView.visibility = View.VISIBLE

        if (isLockMode) {
            if (devicePolicyManager.isAdminActive(deviceAdminComponent)) {
                statusAdminTextView.text = "● Device Admin Aktif"
                statusAdminTextView.setTextColor(ContextCompat.getColor(this, R.color.success))
            } else {
                statusAdminTextView.text = "○ Admin Diperlukan untuk Auto-Lock"
                statusAdminTextView.setTextColor(ContextCompat.getColor(this, R.color.error))
            }
        } else {
            statusAdminTextView.text = "Mode Notifikasi (Tanpa Admin)"
            statusAdminTextView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    // --- LOG ADAPTER UNTUK RECYCLERVIEW ---
    private val ID_DATE = View.generateViewId()
    private val ID_DETAIL = View.generateViewId()
    private val ID_IMAGE = View.generateViewId()

    private inner class LogAdapter(private val logs: List<Map<String, Any>>) :
        RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

        inner class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate: TextView = view.findViewById(ID_DATE)
            val tvDetail: TextView = view.findViewById(ID_DETAIL)
            val ivProof: ImageView = view.findViewById(ID_IMAGE)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val context = parent.context
            val cardView = CardView(context).apply {
                radius = 15f
                elevation = 10f
                useCompatPadding = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val linearLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(30, 30, 30, 30)
            }
            val text1 = TextView(context).apply { id = ID_DATE; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.BLACK) }
            val text2 = TextView(context).apply { id = ID_DETAIL; textSize = 14f; setTextColor(Color.DKGRAY) }
            val image = ImageView(context).apply {
                id = ID_IMAGE
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 500).apply { setMargins(0, 20, 0, 0) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
            }
            linearLayout.addView(text1); linearLayout.addView(text2); linearLayout.addView(image)
            cardView.addView(linearLayout)
            return LogViewHolder(cardView)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val log = logs[position]
            holder.tvDate.text = "📅 ${log["timestamp"]}"
            holder.tvDetail.text = "👥 Wajah: ${log["face_count"]} | ⚡ Respons: ${log["response_time_ms"]}ms"
            val path = log["screenshot_path"]?.toString()
            if (!path.isNullOrEmpty()) {
                val imgFile = File(path)
                if (imgFile.exists()) {
                    holder.ivProof.visibility = View.VISIBLE
                    holder.ivProof.setImageBitmap(BitmapFactory.decodeFile(path))
                } else { holder.ivProof.visibility = View.GONE }
            } else { holder.ivProof.visibility = View.GONE }
        }

        override fun getItemCount() = logs.size
    }
}