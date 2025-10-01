package com.boostgo.customercare.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boostgo.customercare.database.SmsMessage
import com.boostgo.customercare.repo.SmsMessageInterface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class MessageViewModel @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Inject constructor(private val smsMessageRepo: SmsMessageInterface) : ViewModel() {

    private val _allMessages = MutableLiveData<List<SmsMessage>>()
    private val _deliveredMessages = MutableLiveData<List<SmsMessage>>()
    val deliveredMessages: LiveData<List<SmsMessage>> = _deliveredMessages

    private var currentFilter: String? = null

    fun init() {
        loadDeliveredMessages()
    }

    private fun loadDeliveredMessages() {
        viewModelScope.launch {
            smsMessageRepo.getAllMessages().collect { messages ->
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
            smsMessageRepo.deleteAllMessages()
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

