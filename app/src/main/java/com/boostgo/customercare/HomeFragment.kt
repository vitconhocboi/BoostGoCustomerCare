package com.boostgo.customercare

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
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
            // Handle message click if needed
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

        binding.btnClearFilter.setOnClickListener {
            viewModel.clearFilter()
            binding.spinnerFilterStatus.setSelection(0) // Reset to "All"
        }
    }

    private fun checkPermissions() {
        if (!PermissionHelper.hasAllRequiredPermissions(requireContext())) {
            PermissionHelper.requestRequiredPermissions(requireActivity())
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
                    Toast.makeText(requireContext(), "Permissions granted!", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Permissions denied. SMS service may not work properly.",
                        Toast.LENGTH_LONG
                    ).show()
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

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, statuses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilterStatus.adapter = adapter

        // Set up spinner selection listener
        binding.spinnerFilterStatus.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedStatus = statuses[position]
                    if (selectedStatus == "All") {
                        viewModel.filterByStatus(null)
                    } else {
                        viewModel.filterByStatus(selectedStatus)
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                    // Do nothing
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove any pending callbacks when view is destroyed
        binding.tvServiceStatus.removeCallbacks(null)
        _binding = null
    }
}
