package com.example.screenguard

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // Pastikan import ini ada
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ReportActivity : AppCompatActivity() {
    private lateinit var adapter: LogAdapter
    private lateinit var prefs: PreferencesHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        prefs = PreferencesHelper(this)

        val btnClearAll: Button = findViewById(R.id.btnClearAll)
        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewLogs)

        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true
            stackFromEnd = true
        }

        loadLogs(recyclerView)

        btnClearAll.setOnClickListener {
            val logCount = prefs.getDetectionLogsList().size
            if (logCount > 0) {
                showDeleteConfirmation("Kosongkan semua histori?") {
                    prefs.clearLogs()
                    finish()
                }
            }
        }
    }

    private fun loadLogs(recyclerView: RecyclerView) {
        val logsList = prefs.getDetectionLogsList()

        if (logsList.isEmpty()) {
            Toast.makeText(this, "Belum ada histori ancaman.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        adapter = LogAdapter(logsList.toMutableList(),
            { log ->
                val faceCount = (log["face_count"] as? Number)?.toInt()?.toString() ?: "0"
                val responseTime = (log["response_time_ms"] as? Number)?.toLong()?.toString() ?: "0"

                val intent = Intent(this, DetailReportActivity::class.java).apply {
                    putExtra("timestamp", log["timestamp"].toString())

                    putExtra("face_count", faceCount)
                    putExtra("response_time", responseTime)

                    putExtra("fps", log["fps_avg"].toString())
                    putExtra("image_path", log["screenshot_path"].toString())
                    putExtra("light_lux", log["light_lux"]?.toString() ?: "Tidak diketahui")
                    putExtra("estimated_distance", log["estimated_distance"]?.toString() ?: "Tidak diketahui")
                }
                startActivity(intent)
            },
            // Callback Hapus Item (Delete per item)
            { position ->
                showDeleteConfirmation("Hapus catatan ancaman ini?") {
                    prefs.removeLogAt(position)

                    val updatedList = prefs.getDetectionLogsList()
                    if (updatedList.isEmpty()) {
                        finish()
                    } else {
                        adapter.updateData(updatedList)
                        Toast.makeText(this, "Log berhasil dihapus", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        recyclerView.adapter = adapter
    }

    // --- Helper Function untuk Menampilkan Dialog ---
    private fun showDeleteConfirmation(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Hapus")
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_alert) // Ikon peringatan standar Android
            .setPositiveButton("Hapus") { dialog, _ ->
                onConfirm() // Jalankan fungsi penghapusan
                dialog.dismiss()
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss() // Tutup dialog tanpa melakukan apa-apa
            }
            .create()
            .show()
    }
}