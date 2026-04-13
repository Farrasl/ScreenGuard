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
        val cardView = CardView(context).apply {
            radius = 15f
            elevation = 8f
            useCompatPadding = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 40, 40, 40)
            weightSum = 1f
        }

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f)
        }

        val tvDate = TextView(context).apply {
            id = ID_DATE
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }
        val tvDetail = TextView(context).apply {
            id = ID_DETAIL
            textSize = 14f
        }

        textLayout.addView(tvDate)
        textLayout.addView(tvDetail)

        val btnDelete = Button(context).apply {
            id = ID_DELETE
            text = "Hapus"
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)
            // Menghapus bayangan tombol agar rapi
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.3f)
        }

        layout.addView(textLayout)
        layout.addView(btnDelete)
        cardView.addView(layout)
        return LogViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        holder.tvDate.text = "📅 ${log["timestamp"]}"
        holder.tvDetail.text = "Terdeteksi ${log["face_count"]} wajah."

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