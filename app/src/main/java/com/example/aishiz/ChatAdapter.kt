package com.example.aishiz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aishiz.databinding.ItemMessageAssistantBinding
import com.example.aishiz.databinding.ItemMessageUserBinding

class ChatAdapter(
    private val items: MutableList<ChatMessage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_ASSISTANT = 2
        private const val PAYLOAD_TEXT = "payload_text"
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position].role) {
            Role.USER -> TYPE_USER
            Role.ASSISTANT -> TYPE_ASSISTANT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserVH(ItemMessageUserBinding.inflate(inflater, parent, false))
            else -> AssistantVH(ItemMessageAssistantBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bind(holder, items[position])
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_TEXT)) {
            when (holder) {
                is UserVH -> holder.binding.messageText.text = items[position].text
                is AssistantVH -> holder.binding.messageText.text = items[position].text
            }
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun bind(holder: RecyclerView.ViewHolder, msg: ChatMessage) {
        when (holder) {
            is UserVH -> holder.binding.messageText.text = msg.text
            is AssistantVH -> holder.binding.messageText.text = msg.text
        }
    }

    fun add(msg: ChatMessage) {
        items.add(msg)
        notifyItemInserted(items.lastIndex)
    }

    fun replaceAll(newItems: List<ChatMessage>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun updateLastAssistantText(newText: String) {
        val lastIdx = items.indexOfLast { it.role == Role.ASSISTANT }
        if (lastIdx >= 0) {
            items[lastIdx] = items[lastIdx].copy(text = newText)
            notifyItemChanged(lastIdx, PAYLOAD_TEXT)
        }
    }

    fun removeLastAssistantIfEmpty() {
        val lastIdx = items.lastIndex
        if (lastIdx >= 0 &&
            items[lastIdx].role == Role.ASSISTANT &&
            items[lastIdx].text.isBlank()
        ) {
            items.removeAt(lastIdx)
            notifyItemRemoved(lastIdx)
        }
    }

    private class UserVH(val binding: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class AssistantVH(val binding: ItemMessageAssistantBinding) :
        RecyclerView.ViewHolder(binding.root)
}
