package com.volya.vpn.handler

import android.content.Context
import android.util.Log
import com.volya.vpn.AppConfig
import com.volya.vpn.util.FileLogger
import com.volya.vpn.util.Utils
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * V2Ray Native Library Manager
 *
 * Thread-safe singleton wrapper for Libv2ray native methods.
 * Provides initialization protection and unified API for V2Ray core operations.
 */
object V2RayNativeManager {
    private val initialized = AtomicBoolean(false)

    /**
     * Initialize V2Ray core environment.
     * This method is thread-safe and ensures initialization happens only once.
     * Subsequent calls will be ignored silently.
     *
     */
    fun initCoreEnv(context: Context?) {
        if (initialized.compareAndSet(false, true)) {
            try {
                Seq.setContext(context?.applicationContext)
                // Copy geo files from APK assets to user asset directory
                if (context != null) {
                    copyGeoFilesFromAssets(context)
                }
                val assetPath = Utils.userAssetPath(context)
                val deviceId = Utils.getDeviceIdForXUDPBaseKey()
                FileLogger.info("initCoreEnv: assetPath=$assetPath")
                Libv2ray.initCoreEnv(assetPath, deviceId)
                Log.i(AppConfig.TAG, "V2Ray core environment initialized successfully")
            } catch (e: Exception) {
                FileLogger.error("Failed to initialize V2Ray core environment", e)
                Log.e(AppConfig.TAG, "Failed to initialize V2Ray core environment", e)
                initialized.set(false)
                throw e
            }
        } else {
            Log.d(AppConfig.TAG, "V2Ray core environment already initialized, skipping")
        }
    }

    /**
     * Copies geo files from APK assets to the user asset directory.
     * Xray core requires geoip.dat and geosite.dat to be in the user asset directory.
     */
    private fun copyGeoFilesFromAssets(context: Context) {
        try {
            val assetDir = Utils.userAssetPath(context)
            if (assetDir.isEmpty()) {
                FileLogger.error("copyGeoFilesFromAssets: asset path is empty")
                return
            }

            val dir = File(assetDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val geoFiles = listOf("geoip.dat", "geosite.dat")
            geoFiles.forEach { fileName ->
                val targetFile = File(dir, fileName)
                if (targetFile.exists()) {
                    FileLogger.info("copyGeoFilesFromAssets: $fileName already exists at ${targetFile.length()} bytes")
                } else {
                    FileLogger.info("copyGeoFilesFromAssets: copying $fileName...")
                    context.assets.open(fileName).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    FileLogger.info("copyGeoFilesFromAssets: $fileName copied (${targetFile.length()} bytes)")
                }
            }
        } catch (e: Exception) {
            FileLogger.error("copyGeoFilesFromAssets: failed", e)
            Log.e(AppConfig.TAG, "Failed to copy geo files from assets", e)
        }
    }


    /**
     * Get V2Ray core version.
     *
     * @return Version string of the V2Ray core
     */
    fun getLibVersion(): String {
        return try {
            Libv2ray.checkVersionX()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to check V2Ray version", e)
            "Unknown"
        }
    }

    /**
     * Measure outbound connection delay.
     *
     * @param config The configuration JSON string
     * @param testUrl The URL to test against
     * @return Delay in milliseconds, or -1 if test failed
     */
    fun measureOutboundDelay(config: String, testUrl: String): Long {
        return try {
            Libv2ray.measureOutboundDelay(config, testUrl)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to measure outbound delay", e)
            -1L
        }
    }

    /**
     * Create a new core controller instance.
     *
     * @param handler The callback handler for core events
     * @return A new CoreController instance
     */
    fun newCoreController(handler: CoreCallbackHandler): CoreController {
        return try {
            Libv2ray.newCoreController(handler)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to create core controller", e)
            throw e
        }
    }
}
