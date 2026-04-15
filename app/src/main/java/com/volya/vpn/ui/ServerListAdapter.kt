package com.volya.vpn.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.volya.vpn.R
import com.volya.vpn.dto.ProfileItem
import com.volya.vpn.handler.MmkvManager

class ServerListAdapter(
    private val servers: List<ProfileItem>,
    private val onServerSelected: (String) -> Unit
) : RecyclerView.Adapter<ServerListAdapter.ServerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val context = parent.context
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val tvName = TextView(context).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            id = View.generateViewId()
        }
        val tvDetails = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
            id = View.generateViewId()
        }
        layout.addView(tvName)
        layout.addView(tvDetails)
        return ServerViewHolder(layout, tvName, tvDetails)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = servers[position]
        holder.tvName.text = server.remarks
        holder.tvDetails.text = "${server.server}:${server.serverPort}"

        val isSelected = MmkvManager.getSelectServer() == server.subscriptionId
            .takeIf { false } ?: (MmkvManager.getSelectServer() == getServerGuid(position))

        holder.itemView.setOnClickListener {
            val guid = getServerGuid(position)
            if (guid.isNotBlank()) onServerSelected(guid)
        }
    }

    private fun getServerGuid(position: Int): String {
        if (position < 0 || position >= servers.size) return ""
        val allServers = MmkvManager.decodeAllServerList()
        return allServers.getOrNull(position) ?: ""
    }

    override fun getItemCount(): Int = servers.size

    class ServerViewHolder(
        itemView: View,
        val tvName: TextView,
        val tvDetails: TextView
    ) : RecyclerView.ViewHolder(itemView)
}
