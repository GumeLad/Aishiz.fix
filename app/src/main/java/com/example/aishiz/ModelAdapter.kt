package com.example.aishiz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aishiz.databinding.ItemModelBinding

class ModelAdapter(
    private val models: MutableList<ModelInfo>,
    private var selectedId: String?,
    private val onSelect: (ModelInfo) -> Unit
) : RecyclerView.Adapter<ModelAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return VH(ItemModelBinding.inflate(inflater, parent, false))
    }

    override fun getItemCount(): Int = models.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val model = models[position]
        holder.bind(model, model.id == selectedId)
        holder.itemView.setOnClickListener {
            selectedId = model.id
            notifyDataSetChanged()
            onSelect(model)
        }
    }

    fun setModels(newModels: List<ModelInfo>, newSelectedId: String?) {
        models.clear()
        models.addAll(newModels)
        selectedId = newSelectedId
        notifyDataSetChanged()
    }

    fun getSelected(): ModelInfo? = models.firstOrNull { it.id == selectedId }

    class VH(private val binding: ItemModelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(model: ModelInfo, isSelected: Boolean) {
            binding.modelName.text = model.name
            binding.root.isChecked = isSelected
        }
    }
}
