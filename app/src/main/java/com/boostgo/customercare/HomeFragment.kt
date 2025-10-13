package com.boostgo.customercare

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.boostgo.customercare.database.SmsMessage
import com.boostgo.customercare.databinding.FragmentHomeBinding
import com.boostgo.customercare.ui.MessageAdapter
import com.boostgo.customercare.ui.MessageViewModel
import com.boostgo.customercare.utils.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var messageAdapter: MessageAdapter
    private val viewModel: MessageViewModel by viewModels()
    private var previousMessageCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        checkPermissions()
        updateServiceStatus()
        setupFilterSpinner()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter { message ->
            showMessageDialog(message)
        }

        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = messageAdapter
        }
    }

//    private fun setupViewModel() {
//        val database = SmsDatabase.getDatabase(requireContext())
//        val dao = database.smsMessageDao()
//
//        viewModel = ViewModelProvider(this)[MessageViewModel::class.java]
//        viewModel.init(dao)
//
//        // Observe delivered messages
//        viewModel.deliveredMessages.observe(viewLifecycleOwner) { messages ->
//            messageAdapter.submitList(messages)
//        }
//    }

    private fun setupClickListeners() {
        binding.btnToggleService.setOnClickListener {
            if (isServiceRunning()) {
                stopSmsService()
            } else {
                startSmsService()
            }
        }

        viewModel.loadDeliveredMessages()

        viewModel.deliveredMessages.observe(viewLifecycleOwner) { messages ->
            val currentMessageCount = messages.size
            val hasNewMessages = currentMessageCount > previousMessageCount
            
            messageAdapter.submitList(messages) {
                // Auto-scroll to top when new messages arrive
                if (hasNewMessages && currentMessageCount > 0) {
                    binding.recyclerViewMessages.smoothScrollToPosition(0)
                }
            }
            
            // Update message count display
            updateMessageCount(currentMessageCount)
            
            previousMessageCount = currentMessageCount
        }

        binding.btnClearAll.setOnClickListener {
            showClearAllConfirmDialog()
        }
        
        // Setup phone number filter
        binding.etPhoneFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.filterByPhoneNumber(s?.toString())
            }
        })
    }

    private fun checkPermissions() {
        if (!PermissionHelper.hasAllRequiredPermissions(requireContext())) {
            PermissionHelper.requestRequiredPermissions(requireActivity())
        }
        
        // Specifically check READ_SMS permission for reply message functionality
        if (!PermissionHelper.hasReadSmsPermission(requireContext())) {
            Log.w("HomeFragment", "READ_SMS permission not granted. SMS reply functionality will not work.")
            Toast.makeText(
                requireContext(),
                "READ_SMS permission required for SMS reply functionality",
                Toast.LENGTH_LONG
            ).show()
            PermissionHelper.requestReadSmsPermission(requireActivity())
        }

        if (!PermissionHelper.hasReceiveSmsPermission(requireContext())) {
            Log.w("HomeFragment", "RECEIVE_SMS permission not granted. SMS reply functionality will not work.")
            Toast.makeText(
                requireContext(),
                "RECEIVE_SMS permission required for SMS reply functionality",
                Toast.LENGTH_LONG
            ).show()
            PermissionHelper.requestReceiveSmsPermission(requireActivity())
        }
        
        // Check phone call permission for SMS service functionality
        if (!PermissionHelper.hasCallPhonePermission(requireContext())) {
            Log.w("HomeFragment", "CALL_PHONE permission not granted. SMS service may not work properly.")
            Toast.makeText(
                requireContext(),
                "CALL_PHONE permission required for SMS service functionality",
                Toast.LENGTH_LONG
            ).show()
            PermissionHelper.requestCallPhonePermission(requireActivity())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PermissionHelper.PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(requireContext(), "All permissions granted! SMS service and reply functionality enabled.", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    val deniedPermissions = PermissionHelper.getDeniedPermissions(requireContext())
                    val hasReadSms = PermissionHelper.hasReadSmsPermission(requireContext())
                    val hasCallPhone = PermissionHelper.hasCallPhonePermission(requireContext())
                    
                    if (!hasReadSms) {
                        Toast.makeText(
                            requireContext(),
                            "READ_SMS permission denied. SMS reply functionality will not work.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else if (!hasCallPhone) {
                        Toast.makeText(
                            requireContext(),
                            "CALL_PHONE permission denied. SMS service may not work properly.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Some permissions denied. SMS service may not work properly.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun startSmsService() {
        if (!PermissionHelper.hasSmsPermission(requireContext())) {
            Toast.makeText(
                requireContext(),
                "SMS permission is required to send messages",
                Toast.LENGTH_LONG
            ).show()
            PermissionHelper.requestRequiredPermissions(requireActivity())
            return
        }
        
        if (!PermissionHelper.hasCallPhonePermission(requireContext())) {
            Toast.makeText(
                requireContext(),
                "Phone call permission is required for SMS service",
                Toast.LENGTH_LONG
            ).show()
            PermissionHelper.requestCallPhonePermission(requireActivity())
            return
        }

        val intent = Intent(requireContext(), SmsService::class.java).apply {
            action = SmsService.ACTION_START_POLLING
        }
        requireContext().startService(intent)
        Toast.makeText(requireContext(), "SMS service started", Toast.LENGTH_SHORT).show()

        // Update status immediately to show "Starting"
        binding.tvServiceStatus.text = "Starting..."
        binding.tvServiceStatus.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
        binding.btnToggleService.text = getString(R.string.stop_service)

        // Update after a short delay to check actual service status
        binding.tvServiceStatus.postDelayed({
            updateServiceStatus()
        }, 1000)
        
        // Auto-scroll to top when service starts to show new messages
        binding.recyclerViewMessages.smoothScrollToPosition(0)
    }

    private fun stopSmsService() {
        val intent = Intent(requireContext(), SmsService::class.java).apply {
            action = SmsService.ACTION_STOP_POLLING
        }
        requireContext().startService(intent)
        Toast.makeText(requireContext(), "SMS service stopped", Toast.LENGTH_SHORT).show()

        // Update status immediately to show "Stopped"
        binding.tvServiceStatus.text = "Stopped"
        binding.tvServiceStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
        binding.btnToggleService.text = getString(R.string.start_service)

        // Also update after a short delay to ensure service has actually stopped
        binding.tvServiceStatus.postDelayed({
            updateServiceStatus()
        }, 1000)
    }

    private fun isServiceRunning(): Boolean {
        val activityManager =
            requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = activityManager.getRunningServices(Integer.MAX_VALUE)

        for (service in services) {
            if (SmsService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun updateServiceStatus() {
        val isRunning = isServiceRunning()

        if (isRunning) {
            binding.tvServiceStatus.text = "Running"
            binding.tvServiceStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            binding.btnToggleService.text = getString(R.string.stop_service)
        } else {
            binding.tvServiceStatus.text = "Stopped"
            binding.tvServiceStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
            binding.btnToggleService.text = getString(R.string.start_service)
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupFilterSpinner() {
        // Get available statuses from ViewModel
        val statuses = viewModel.getAvailableStatuses()

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, statuses)
        binding.autoCompleteFilterStatus.setAdapter(adapter)
        
        // Set default selection to "Sending"
        binding.autoCompleteFilterStatus.setText("Sending", false)
        // Apply the default filter
        viewModel.filterByStatus("Sending")

        // Set up AutoCompleteTextView selection listener
        binding.autoCompleteFilterStatus.setOnItemClickListener { _, _, position, _ ->
            val selectedStatus = statuses[position]
            if (selectedStatus == "All") {
                viewModel.filterByStatus(null)
            } else {
                viewModel.filterByStatus(selectedStatus)
            }
        }
    }

    private fun updateMessageCount(count: Int) {
        val countText = if (count == 1) "1 message" else "$count messages"
        binding.tvMessageCount.text = countText
    }

    private fun showClearAllConfirmDialog() {
        val dialogBuilder = AlertDialog.Builder(requireContext())
        dialogBuilder.setTitle("Xóa tất cả tin nhắn")
        dialogBuilder.setMessage("Bạn có chắc chắn muốn xóa tất cả tin nhắn không? Hành động này không thể hoàn tác.")
        
        dialogBuilder.setPositiveButton("Clear All") { dialog, _ ->
            // User confirmed, proceed with clearing
            viewModel.clearAllMessages()
            binding.autoCompleteFilterStatus.setText("Sending", false) // Reset to "Sending"
            binding.etPhoneFilter.text?.clear() // Clear phone filter
            viewModel.filterByStatus("Sending") // Apply the default filter
            previousMessageCount = 0 // Reset message count
            updateMessageCount(0) // Update count display
            dialog.dismiss()
        }
        
        dialogBuilder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        
        dialogBuilder.create().show()
    }

    private fun showMessageDialog(message: SmsMessage) {
        val dialogBuilder = AlertDialog.Builder(requireContext())
        dialogBuilder.setTitle("Message Details")
        
        val messageContent = """
Phone: ${message.phoneNumber}
Order ID: ${message.orderId}
Status: ${message.status}
Time: ${java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))}
            
Message:
${message.message}
        """.trimIndent()
        
        dialogBuilder.setMessage(messageContent)
        dialogBuilder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        
        dialogBuilder.create().show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove any pending callbacks when view is destroyed
        binding.tvServiceStatus.removeCallbacks(null)
        _binding = null
    }
}
