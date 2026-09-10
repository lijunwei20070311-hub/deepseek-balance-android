package com.dsh.deepseekbalance.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dsh.deepseekbalance.R
import com.dsh.deepseekbalance.api.PlatformApi

/** 本月分模型用量列表。 */
class ModelUsageAdapter : RecyclerView.Adapter<ModelUsageAdapter.Holder>() {

    private val items = mutableListOf<PlatformApi.ModelUsage>()

    fun submit(list: List<PlatformApi.ModelUsage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_usage, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = item.model
        holder.tokens.text = Fmt.tokens(item.tokens) + " tokens"
        holder.detail.text = buildString {
            append("请求 ${item.requests}")
            if (item.cost > 0) append(" · ¥%.4f".format(item.cost))
        }
    }

    class Holder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_model_name)
        val tokens: TextView = view.findViewById(R.id.tv_model_tokens)
        val detail: TextView = view.findViewById(R.id.tv_model_detail)
    }
}
