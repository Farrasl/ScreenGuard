package com.example.screenguard

import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter(
    private var logs: MutableList<Map<String, Any>>,
    private val onClick: (Map<String, Any>) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val ID_DATE = View.generateViewId()
    private val ID_DETAIL = View.generateViewId()
    private val ID_DELETE = View.generateViewId()

    inner class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(ID_DATE)
        val tvDetail: TextView = view.findViewById(ID_DETAIL)
        val btnDelete: Button = view.findViewById(ID_DELETE)

        init {
            view.setOnClickListener {
                // Gunakan adapterPosition jika bindingAdapterPosition error
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onClick(logs[pos])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val context = parent.context

        // 1. KARTU DENGAN SUDUT HALUS & BAYANGAN RINGAN
        val cardView = CardView(context).apply {
            radius = 32f // Sudut lebih membulat (modern)
            elevation = 6f
            setCardBackgroundColor(Color.WHITE)
            useCompatPadding = true
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 8, 8, 16) // Spasi ekstra antar kartu
            }
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 48, 48, 48) // Ruang bernapas yang lega
            weightSum = 1f
        }

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.75f)
        }

        // 2. TIPOGRAFI YANG LEBIH RAPI
        val tvDate = TextView(context).apply {
            id = ID_DATE
            textSize = 16f
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B")) // text_main
        }
        val tvDetail = TextView(context).apply {
            id = ID_DETAIL
            textSize = 14f
            setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL)
            setTextColor(Color.parseColor("#64748B")) // text_secondary
            setPadding(0, 8, 0, 0)
        }

        textLayout.addView(tvDate)
        textLayout.addView(tvDetail)

        // 3. TOMBOL HAPUS MINIMALIS
        val btnDelete = Button(context).apply {
            id = ID_DELETE
            text = "HAPUS"
            textSize = 12f
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.parseColor("#EF4444")) // Red error color
            setBackgroundColor(Color.parseColor("#FFF1F2")) // Background merah sangat soft
            stateListAnimator = null

            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.25f)
            params.setMargins(16, 0, 0, 0)
            layoutParams = params
        }

        layout.addView(textLayout)
        layout.addView(btnDelete)
        cardView.addView(layout)
        return LogViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        val faceCount = (log["face_count"] as? Number)?.toInt() ?: 0

        holder.tvDate.text = "📅 ${log["timestamp"]}"
        holder.tvDetail.text = "Terdeteksi $faceCount wajah."

        holder.btnDelete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onDeleteClick(pos)
            }
        }
    }

    override fun getItemCount() = logs.size

    fun updateData(newLogs: List<Map<String, Any>>) {
        this.logs = newLogs.toMutableList()
        notifyDataSetChanged()
    }
}