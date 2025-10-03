package com.boostgo.customercare.ui

import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.boostgo.customercare.database.TestConfig
import com.boostgo.customercare.databinding.FragmentSettingsBinding
import com.boostgo.customercare.utils.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    companion object {
        fun newInstance(
            onCloseFragment: () -> Unit
        ): SettingsFragment {
            val fragment = SettingsFragment()
            fragment.onCloseFragment = onCloseFragment
            return fragment
        }
    }

    private lateinit var onCloseFragment: () -> Unit

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val testConfigViewModel: TestConfigViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        loadCurrentSettings()
    }

    private fun setupClickListeners() {
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        binding.btnCancel.setOnClickListener {
            closeFragment()
        }
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            try {
                testConfigViewModel.getTestConfig().collect { currentConfig ->
                    if (currentConfig != null) {
                        binding.etTestNumber.setText(currentConfig.testNumber)
                        binding.switchTestingEnabled.isChecked = currentConfig.isTestingEnabled
                        binding.etMessageTemplate.setText(currentConfig.messageTemplate)
                        binding.etTelegramBotToken.setText(currentConfig.telegramBotToken)
                        binding.etTelegramChatId.setText(currentConfig.telegramChatId)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error loading settings: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveSettings() {
        val testNumber = binding.etTestNumber.text.toString().trim()
        val isTestingEnabled = binding.switchTestingEnabled.isChecked
        val messageTemplate = binding.etMessageTemplate.text.toString().trim()
        val telegramBotToken = binding.etTelegramBotToken.text.toString().trim()
        val telegramChatId = binding.etTelegramChatId.text.toString().trim()

        if (testNumber.isEmpty()) {
            binding.etTestNumber.error = "Test number is required"
            return
        }

        if (messageTemplate.isEmpty()) {
            binding.etMessageTemplate.error = "Message template is required"
            return
        }

        try {
            val testConfig = TestConfig(
                testNumber = testNumber,
                isTestingEnabled = isTestingEnabled,
                messageTemplate = messageTemplate,
                telegramBotToken = telegramBotToken,
                telegramChatId = telegramChatId
            )
            viewLifecycleOwner.lifecycleScope.launch {
                testConfigViewModel.saveTestConfig(testConfig)
                Toast.makeText(requireContext(), "Settings saved successfully!", Toast.LENGTH_SHORT)
                    .show()
            }
            closeFragment()
        } catch (e: Exception) {
            Log.d("SettingFragment", "saveSettings: ", e)
            Toast.makeText(
                requireContext(),
                "Error saving settings: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun sendTestSms() {
        val testNumber = binding.etTestNumber.text.toString().trim()
        val testMessage = "Test message from CustomerCare app"

        if (testNumber.isEmpty()) {
            binding.etTestNumber.error = "Test number is required"
            return
        }

        if (!PermissionHelper.hasSmsPermission(requireContext())) {
            Toast.makeText(
                requireContext(),
                "SMS permission is required to send test message",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(testNumber, null, testMessage, null, null)
            Toast.makeText(requireContext(), "Test SMS sent to $testNumber", Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Error sending test SMS: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun closeFragment() {
        onCloseFragment()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
