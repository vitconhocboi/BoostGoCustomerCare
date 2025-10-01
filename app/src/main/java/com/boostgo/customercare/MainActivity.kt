package com.boostgo.customercare

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
import com.boostgo.customercare.databinding.ActivityMainBinding
import com.boostgo.customercare.databinding.FragmentHomeBinding
import com.boostgo.customercare.ui.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private val mHomeFragment by lazy {
        HomeFragment()
    }

    private val mSettingFragment by lazy {
        SettingsFragment.newInstance(onCloseFragment = { showFragment(mHomeFragment) })
    }
    private var mListFragment = arrayListOf<Fragment>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check internet connectivity
        checkInternetConnection()

        mListFragment.add(mHomeFragment)
        mListFragment.add(mSettingFragment)
        showFragment(mHomeFragment)

        // Setup settings button click listener
        binding.lnSetting.setOnClickListener {
            showFragment(mSettingFragment)
        }
    }

    @SuppressLint("CommitTransaction")
    fun showFragment(fragment: Fragment) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        val otherFragment = mListFragment.filter { it != fragment }

        if (fragment.isAdded) {
            fragmentTransaction.show(fragment)
        } else {
            fragmentTransaction.add(binding.fragmentContainer.id, fragment)
        }

        otherFragment.forEach {
            if (it.isAdded) {
                fragmentTransaction.hide(it)
            }
        }

        lifecycleScope.launch {
            lifecycle.whenResumed {
                fragmentTransaction.commitNow()
            }
        }
    }

    private fun checkInternetConnection() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        
        val isConnected = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        
        if (isConnected) {
            Log.d("MainActivity", "✅ Internet connection available")
        } else {
            Log.w("MainActivity", "⚠️ No internet connection detected")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
