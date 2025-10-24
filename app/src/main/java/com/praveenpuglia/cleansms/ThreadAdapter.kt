package com.praveenpuglia.cleansms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.*
import androidx.core.net.toUri

class ThreadAdapter(private var items: List<ThreadItem>) : RecyclerView.Adapter<ThreadAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarImage: ImageView = itemView.findViewById(R.id.thread_avatar_image)
        val avatarText: TextView = itemView.findViewById(R.id.thread_avatar_text)
        val name: TextView = itemView.findViewById(R.id.thread_name)
        val date: TextView = itemView.findViewById(R.id.thread_date)
        val snippet: TextView = itemView.findViewById(R.id.thread_snippet)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_thread, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.contactName ?: item.nameOrAddress
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        holder.date.text = df.format(Date(item.date))
        holder.snippet.text = item.snippet

        // If MainActivity enriched with photoUri/name, use them
        val photo = item.contactPhotoUri
        val contactName = item.contactName

        if (!photo.isNullOrEmpty()) {
            try {
                holder.avatarImage.setImageURI(photo.toUri())
                holder.avatarImage.visibility = View.VISIBLE
                holder.avatarText.visibility = View.GONE
                return
            } catch (_: Exception) {
                // fall through to initials
            }
        }

        if (!contactName.isNullOrEmpty()) {
            val initial = contactName.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
            holder.avatarText.text = initial
            holder.avatarText.visibility = View.VISIBLE
            holder.avatarImage.visibility = View.GONE
            return
        }

        // Fallback: if the nameOrAddress contains letters (alphanumeric sender ID), show first letter
        val raw = item.nameOrAddress
        val hasLetters = raw.any { it.isLetter() }
        if (hasLetters) {
            val firstLetter = raw.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
            holder.avatarText.text = firstLetter
            holder.avatarText.visibility = View.VISIBLE
            holder.avatarImage.visibility = View.GONE
            return
        }

        // Final fallback
        holder.avatarText.text = "#"
        holder.avatarText.visibility = View.VISIBLE
        holder.avatarImage.visibility = View.GONE
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ThreadItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
