package com.volya.vpn.ui

import androidx.activity.OnBackPressedCallback

import android.content.Intent
import android.content.res.ColorStateList
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.volya.vpn.AppConfig
import com.volya.vpn.R
import com.volya.vpn.extension.toast
import com.volya.vpn.extension.toastError
import com.volya.vpn.handler.AngConfigManager
import com.volya.vpn.handler.MmkvManager
import com.volya.vpn.handler.SettingsManager
import com.volya.vpn.handler.V2RayServiceManager
import com.volya.vpn.dto.ProfileItem
import com.volya.vpn.viewmodel.MainViewModel
import androidx.activity.viewModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private var isRunning: Boolean = false
    private var isConnecting: Boolean = false

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        } else {
            isConnecting = false
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener { handleFabAction() }

        findViewById<View>(R.id.btnSubscription).setOnClickListener {
            startActivity(Intent(this, SubSettingActivity::class.java))
        }

        findViewById<View>(R.id.tvTestState).setOnClickListener {
            if (isRunning) {
                findViewById<TextView>(R.id.tvTestState).text = getString(R.string.connection_test_testing)
                mainViewModel.testCurrentServerRealPing()
            }
        }

        setupServerList()
        checkServiceStatus()
    }

    private fun setupServerList() {
        val rv = findViewById<RecyclerView>(R.id.rvServers)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = ServerListAdapter(getServerList(), ::onServerSelected)
        rv.adapter = adapter
    }

    private fun getServerList(): List<ProfileItem> {
        val serverList = MmkvManager.decodeAllServerList()
        return serverList.mapNotNull { guid ->
            MmkvManager.decodeServerConfig(guid)
        }
    }

    private fun onServerSelected(guid: String) {
        MmkvManager.setSelectServer(guid)
        updateUI()
        if (isRunning) {
            V2RayServiceManager.stopVService(this)
            lifecycleScope.launch {
                delay(500)
                startV2Ray()
            }
        }
    }

    fun restartV2Ray() {
        if (isRunning) {
            V2RayServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    private fun handleFabAction() {
        if (isConnecting) return

        if (isRunning) {
            V2RayServiceManager.stopVService(this)
            isConnecting = false
            updateUI()
        } else {
            isConnecting = true
            updateUI()
            if (SettingsManager.isVpnMode()) {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    requestVpnPermission.launch(intent)
                }
            } else {
                startV2Ray()
            }
        }
    }

    private fun startV2Ray() {
        val selectedServer = MmkvManager.getSelectServer()
        if (selectedServer.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            isConnecting = false
            updateUI()
            return
        }
        V2RayServiceManager.startVService(this, selectedServer)
    }

    private fun checkServiceStatus() {
        mainViewModel.isRunning.observe(this) { running ->
            isRunning = running
            isConnecting = false
            updateUI()
        }
        mainViewModel.updateTestResultAction.observe(this) { result ->
            findViewById<TextView>(R.id.tvTestState).text = result
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
        mainViewModel.reloadServerList()
    }

    private fun updateUI() {
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvServerName = findViewById<TextView>(R.id.tvServerName)
        val tvSpeed = findViewById<TextView>(R.id.tvSpeed)
        val tvTestState = findViewById<TextView>(R.id.tvTestState)
        val progressBar = findViewById<View>(R.id.progressBar)

        val selectedGuid = MmkvManager.getSelectServer()
        val selectedServer = selectedGuid?.let { MmkvManager.decodeServerConfig(it) }

        if (isConnecting) {
            fab.setImageResource(R.drawable.ic_fab_check)
            fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_active))
            fab.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            tvStatus.text = "Подключение..."
            tvServerName.text = selectedServer?.remarks ?: ""
        } else if (isRunning) {
            fab.setImageResource(R.drawable.ic_stop_24dp)
            fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_active))
            fab.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            tvStatus.text = "Подключено"
            tvServerName.text = selectedServer?.remarks ?: ""
            tvSpeed.visibility = View.VISIBLE
        } else {
            fab.setImageResource(R.drawable.ic_play_24dp)
            fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_inactive))
            fab.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            tvStatus.text = "Нажмите для подключения"
            tvServerName.text = selectedServer?.remarks ?: "Нет сервера"
            tvSpeed.visibility = View.GONE
            tvSpeed.text = ""
        }
    }

    override fun onResume() {
        super.onResume()
        setupServerList()
        updateUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        checkServiceStatus()
    }

    fun importConfigViaSub() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (result.successCount > 0) {
                    toast(getString(R.string.title_update_config_count, result.configCount))
                } else {
                    toastError(R.string.toast_failure)
                }
                if (result.configCount > 0) {
                    mainViewModel.reloadServerList()
                    setupServerList()
                }
                hideLoading()
            }
        }
    }

    fun importBatchConfig(server: String?) {
        val currentSubId = mainViewModel.subscriptionId
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(server, currentSubId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                            setupServerList()
                        }
                        countSub > 0 -> setupServerList()
                        else -> toastError(R.string.toast_failure)
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    hideLoading()
                }
                Log.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }
}
