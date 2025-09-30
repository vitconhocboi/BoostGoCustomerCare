package com.boostgo.customercare

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.boostgo.customercare.database.SmsMessage
import com.boostgo.customercare.database.SmsDatabase
import com.boostgo.customercare.ui.MessageAdapter
import com.boostgo.customercare.ui.MessageViewModel
import com.boostgo.customercare.utils.PermissionHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.Toast
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.app.ActivityManager
import android.content.Context

class HomeActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var viewModel: MessageViewModel
    private lateinit var fabNewSms: FloatingActionButton
    private lateinit var btnClearAll: MaterialButton
    private lateinit var btnToggleService: MaterialButton
    private lateinit var tvServiceStatus: TextView
    private lateinit var spinnerFilterStatus: Spinner
    private lateinit var btnClearFilter: MaterialButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        
        initializeViews()
        setupRecyclerView()
        setupViewModel()
        setupClickListeners()
        checkPermissions()
        updateServiceStatus()
        setupFilterSpinner()
    }
    
    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewMessages)
        btnClearAll = findViewById(R.id.btnClearAll)
        btnToggleService = findViewById(R.id.btnToggleService)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        spinnerFilterStatus = findViewById(R.id.spinnerFilterStatus)
        btnClearFilter = findViewById(R.id.btnClearFilter)
    }
    
    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter { message ->
            // Handle message click if needed
        }
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = messageAdapter
        }
    }
    
    private fun setupViewModel() {
        val database = SmsDatabase.getDatabase(this)
        val dao = database.smsMessageDao()

        viewModel = ViewModelProvider(this)[MessageViewModel::class.java]
        viewModel.init(dao)
        
        // Observe delivered messages
        viewModel.deliveredMessages.observe(this) { messages ->
            messageAdapter.submitList(messages)
        }
    }
    
    private fun setupClickListeners() {
        btnClearAll.setOnClickListener {
            viewModel.clearAllMessages()
        }
        
        btnToggleService.setOnClickListener {
            if (isServiceRunning()) {
                stopSmsService()
            } else {
                startSmsService()
            }
        }
        
        btnClearFilter.setOnClickListener {
            viewModel.clearFilter()
            spinnerFilterStatus.setSelection(0) // Reset to "All"
        }
    }
    
    private fun checkPermissions() {
        if (!PermissionHelper.hasAllRequiredPermissions(this)) {
            PermissionHelper.requestRequiredPermissions(this)
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
                    Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Permissions denied. SMS service may not work properly.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun startSmsService() {
        if (!PermissionHelper.hasSmsPermission(this)) {
            Toast.makeText(this, "SMS permission is required to send messages", Toast.LENGTH_LONG).show()
            PermissionHelper.requestRequiredPermissions(this)
            return
        }
        
        val intent = Intent(this, SmsService::class.java).apply {
            action = SmsService.ACTION_START_POLLING
        }
        startService(intent)
        Toast.makeText(this, "SMS service started", Toast.LENGTH_SHORT).show()
        
        // Update status immediately to show "Starting"
        tvServiceStatus.text = "Starting..."
        tvServiceStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
        btnToggleService.text = getString(R.string.stop_service)
        
        // Update after a short delay to check actual service status
        tvServiceStatus.postDelayed({
            updateServiceStatus()
        }, 1000)
    }
    
    private fun stopSmsService() {
        val intent = Intent(this, SmsService::class.java).apply {
            action = SmsService.ACTION_STOP_POLLING
        }
        startService(intent)
        Toast.makeText(this, "SMS service stopped", Toast.LENGTH_SHORT).show()
        
        // Update status immediately to show "Stopped"
        tvServiceStatus.text = "Stopped"
        tvServiceStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        btnToggleService.text = getString(R.string.start_service)
        
        // Also update after a short delay to ensure service has actually stopped
        tvServiceStatus.postDelayed({
            updateServiceStatus()
        }, 1000)
    }
    
    private fun isServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
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
            tvServiceStatus.text = "Running"
            tvServiceStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnToggleService.text = getString(R.string.stop_service)
        } else {
            tvServiceStatus.text = "Stopped"
            tvServiceStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnToggleService.text = getString(R.string.start_service)
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }
    
    private fun setupFilterSpinner() {
        // Get available statuses from ViewModel
        val statuses = viewModel.getAvailableStatuses()
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statuses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilterStatus.adapter = adapter
        
        // Set up spinner selection listener
        spinnerFilterStatus.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
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
    
    override fun onDestroy() {
        super.onDestroy()
        // Remove any pending callbacks when activity is destroyed
        tvServiceStatus.removeCallbacks(null)
    }
}
