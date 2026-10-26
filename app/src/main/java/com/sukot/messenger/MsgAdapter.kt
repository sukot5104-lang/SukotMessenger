package com.sukot.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MsgAdapter : RecyclerView.Adapter<MsgAdapter.VH>() {

    class Row(val msg: Msg?, val label: String?)

    private val rows = ArrayList<Row>()

    fun submit(list: List<Msg>) {
        rows.clear()
        var prev: Long? = null
        for (m in list) {
            val label = Util.dateLabel(m.ts, prev)
            if (label != null) rows.add(Row(null, label))
            rows.add(Row(m, null))
            prev = m.ts
        }
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvText: TextView? = v.findViewById(R.id.tvText)
        val tvMeta: TextView? = v.findViewById(R.id.tvMeta)
        val tvDate: TextView? = v.findViewById(R.id.tvDate)
    }

    override fun getItemViewType(position: Int): Int {
        val r = rows[position]
        return if (r.msg == null) 2 else if (r.msg.outgoing) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = when (viewType) {
            1 -> R.layout.item_msg_out
            2 -> R.layout.item_date
            else -> R.layout.item_msg_in
        }
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = rows[position]
        if (r.msg == null) {
            h.tvDate?.text = r.label
            return
        }
        val m = r.msg
        h.tvText?.text = m.text
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.ts))
        h.tvMeta?.text = if (m.outgoing) {
            when (m.status) {
                "pending" -> "$time ..."
                "sent" -> "$time ✓"
                else -> "$time ✓✓"
            }
        } else {
            time
        }
        h.itemView.setOnLongClickListener {
            val cm = h.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("msg", m.text))
            Toast.makeText(h.itemView.context, R.string.msg_copied, Toast.LENGTH_SHORT).show()
            true
        }
    }
}
