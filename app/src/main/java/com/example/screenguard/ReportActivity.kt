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
        prefs = PreferencesHelper(this)

        // --- Setup Layout Programmatic ---
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val tvTitle = TextView(this).apply {
            text = "Histori Ancaman"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 32)
        }

        val btnClearAll = Button(this).apply {
            text = "Hapus Semua Histori"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.RED)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ReportActivity).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        rootLayout.addView(tvTitle)
        rootLayout.addView(btnClearAll) // Tombol ditaruh di atas list
        rootLayout.addView(recyclerView)
        setContentView(rootLayout)

        loadLogs(recyclerView)

        // --- UPDATE 1: Konfirmasi Hapus Semua ---
        btnClearAll.setOnClickListener {
            val logCount = prefs.getDetectionLogsList().size
            if (logCount > 0) {
                showDeleteConfirmation("Apakah Anda yakin ingin menghapus SEMUA ($logCount) histori ancaman? Data tidak dapat dikembalikan.") {
                    // Aksi ini hanya dijalankan jika user menekan "Ya"
                    prefs.clearLogs()
                    Toast.makeText(this, "Semua histori telah dihapus permanen.", Toast.LENGTH_SHORT).show()
                    finish() // Tutup activity karena data kosong
                }
            } else {
                Toast.makeText(this, "Data sudah kosong.", Toast.LENGTH_SHORT).show()
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
                val intent = Intent(this, DetailReportActivity::class.java).apply {
                    putExtra("timestamp", log["timestamp"].toString())
                    putExtra("face_count", log["face_count"].toString())
                    putExtra("response_time", log["response_time_ms"].toString())
                    putExtra("fps", log["fps_avg"].toString())
                    putExtra("image_path", log["screenshot_path"].toString())
                }
                startActivity(intent)
            },
            // Callback Hapus Item (Delete per item)
            { position ->
                // --- UPDATE 2: Konfirmasi Hapus Satu Item ---
                showDeleteConfirmation("Hapus catatan ancaman ini?") {
                    // Aksi ini hanya dijalankan jika user menekan "Ya"
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