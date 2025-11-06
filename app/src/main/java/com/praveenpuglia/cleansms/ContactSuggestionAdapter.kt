package com.praveenpuglia.cleansms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactSuggestionAdapter(
    private var contacts: List<ContactSuggestion>,
    private val onContactClick: (ContactSuggestion) -> Unit
) : RecyclerView.Adapter<ContactSuggestionAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarImage: ImageView = itemView.findViewById(R.id.contact_suggestion_avatar_image)
        val avatarText: TextView = itemView.findViewById(R.id.contact_suggestion_avatar_text)
        val name: TextView = itemView.findViewById(R.id.contact_suggestion_name)
        val phone: TextView = itemView.findViewById(R.id.contact_suggestion_phone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_suggestion, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val contact = contacts[position]
        
        if (contact.isRawNumber) {
            holder.name.text = "Send SMS to ${contact.phoneNumber}"
            holder.phone.text = contact.phoneNumber
        } else {
            holder.name.text = contact.name
            holder.phone.text = contact.phoneNumber
        }
        
        // Set avatar
        if (!contact.photoUri.isNullOrBlank()) {
            try {
                val uri = android.net.Uri.parse(contact.photoUri)
                holder.avatarImage.setImageURI(uri)
                holder.avatarImage.visibility = View.VISIBLE
                holder.avatarText.visibility = View.GONE
            } catch (e: Exception) {
                setTextAvatar(holder, contact.name)
            }
        } else {
            setTextAvatar(holder, contact.name)
        }
        
        holder.itemView.setOnClickListener {
            onContactClick(contact)
        }
    }

    private fun setTextAvatar(holder: VH, name: String) {
        holder.avatarImage.visibility = View.GONE
        holder.avatarText.visibility = View.VISIBLE
    holder.avatarText.text = name.take(1).uppercase()
    }

    override fun getItemCount(): Int = contacts.size

    fun updateContacts(newContacts: List<ContactSuggestion>) {
        contacts = newContacts
        notifyDataSetChanged()
    }
}
