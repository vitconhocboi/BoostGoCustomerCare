package com.boostgo.customercare.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boostgo.customercare.database.SmsMessage
import com.boostgo.customercare.database.SmsMessageDao
import kotlinx.coroutines.launch

class MessageViewModel : ViewModel() {

    private lateinit var smsMessageDao: SmsMessageDao

    private val _allMessages = MutableLiveData<List<SmsMessage>>()
    private val _deliveredMessages = MutableLiveData<List<SmsMessage>>()
    val deliveredMessages: LiveData<List<SmsMessage>> = _deliveredMessages

    private var currentFilter: String? = null

    fun init(dao: SmsMessageDao) {
        smsMessageDao = dao
        loadDeliveredMessages()
    }

    private fun loadDeliveredMessages() {
        viewModelScope.launch {
            smsMessageDao.getAllMessages().collect { messages ->
                _allMessages.value = messages
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val allMessages = _allMessages.value ?: emptyList()
        val filteredMessages = if (currentFilter == null || currentFilter == "All") {
            allMessages
        } else {
            allMessages.filter { it.status == currentFilter }
        }
        _deliveredMessages.value = filteredMessages
    }

    fun clearAllMessages() {
        viewModelScope.launch {
            smsMessageDao.deleteAllMessages()
        }
    }

    fun updateMessageStatus(message: SmsMessage, status: String) {
        viewModelScope.launch {
            val updatedMessage = message.copy(
                status = status,
                deliveredAt = if (status == "Delivered") System.currentTimeMillis() else null
            )
            smsMessageDao.updateMessage(updatedMessage)
        }
    }

    fun filterByStatus(status: String?) {
        currentFilter = status
        applyFilter()
    }

    fun clearFilter() {
        currentFilter = null
        applyFilter()
    }

    fun getAvailableStatuses(): List<String> {
        return listOf("All", "Sending", "Delivered", "Failed")
    }
}

