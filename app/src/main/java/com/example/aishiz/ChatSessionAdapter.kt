package com.example.aishiz

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aishiz.databinding.ItemChatSessionBinding

class ChatSessionAdapter(
    private val onSelect: (ChatSession) -> Unit,
    private val onDelete: (ChatSession) -> Unit
) : RecyclerView.Adapter<ChatSessionAdapter.ViewHolder>() {

    private val items = mutableListOf<ChatSession>()
    private var selectedId: String? = null

    fun submitItems(sessions: List<ChatSession>, selected: String?) {
        items.clear()
        items.addAll(sessions)
        selectedId = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = items[position]
        holder.binding.chatTitle.text = session.title
        holder.binding.chatUpdated.text = DateUtils.getRelativeTimeSpanString(
            session.updatedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.binding.root.isChecked = session.id == selectedId
        holder.binding.root.setOnClickListener { onSelect(session) }
        holder.binding.root.setOnLongClickListener {
            onDelete(session)
            true
        }
    }

    class ViewHolder(val binding: ItemChatSessionBinding) :
        RecyclerView.ViewHolder(binding.root)
}
