package com.boostgo.customercare.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.boostgo.customercare.R
import com.boostgo.customercare.database.SmsMessage
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val onMessageClick: (SmsMessage) -> Unit
) : ListAdapter<SmsMessage, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPhoneNumber: TextView = itemView.findViewById(R.id.tvPhoneNumber)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        
        fun bind(message: SmsMessage) {
            tvPhoneNumber.text = message.phoneNumber
            tvMessage.text = message.message
            tvStatus.text = message.status
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            tvTimestamp.text = dateFormat.format(Date(message.timestamp))
            
            // Set status color
            when (message.status) {
                "Delivered" -> {
                    tvStatus.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                }
                "Failed" -> {
                    tvStatus.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                }
                "Sending" -> {
                    tvStatus.setTextColor(itemView.context.getColor(android.R.color.holo_orange_dark))
                }
                else -> {
                    tvStatus.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                }
            }
            
            itemView.setOnClickListener {
                onMessageClick(message)
            }
        }
    }
    
    class MessageDiffCallback : DiffUtil.ItemCallback<SmsMessage>() {
        override fun areItemsTheSame(oldItem: SmsMessage, newItem: SmsMessage): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: SmsMessage, newItem: SmsMessage): Boolean {
            return oldItem == newItem
        }
    }
}
