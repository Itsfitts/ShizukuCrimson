package af.shizuku.manager.utils

import android.content.Context
import android.util.Log
import af.shizuku.manager.ShizukuSettings
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.concurrent.thread

object LogcatLogger {
    private const val TAG = "LogcatLogger"
    private var activeProcess: Process? = null

    fun getLogFile(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, "logcat.txt")
    }

    @Synchronized
    fun start(context: Context) {
        if (activeProcess != null) {
            return
        }

        val logFile = getLogFile(context)
        try {
            if (logFile.exists()) {
                logFile.delete()
            }
            logFile.createNewFile()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create log file", e)
            return
        }

        thread {
            try {
                // If Shizuku is running, we run logcat through Shizuku so we can see system-wide logs (e.g. server process).
                // Otherwise, we run logcat locally.
                val cmd = arrayOf("logcat", "-f", logFile.absolutePath, "-v", "time")
                val proc = if (Shizuku.pingBinder()) {
                    Log.i(TAG, "Starting logcat via Shizuku server to capture system-wide logs")
                    Shizuku.newProcess(cmd, null, null)
                } else {
                    Log.i(TAG, "Starting local logcat process")
                    Runtime.getRuntime().exec(cmd)
                }

                synchronized(this) {
                    activeProcess = proc
                }

                proc.waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Error running logcat process", e)
            } finally {
                synchronized(this) {
                    activeProcess = null
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        activeProcess?.let {
            Log.i(TAG, "Stopping active logcat process")
            it.destroy()
            activeProcess = null
        }
    }

    @Synchronized
    fun clear(context: Context) {
        stop()
        val logFile = getLogFile(context)
        if (logFile.exists()) {
            logFile.delete()
        }
        // Also clear the logcat buffer
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.newProcess(arrayOf("logcat", "-c"), null, null).waitFor()
            } else {
                Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
            }
        } catch (ignored: Exception) {}
        
        // Restart if enabled
        if (ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.Keys.KEY_ENABLE_LOGCAT_LOGGER, false)) {
            start(context)
        }
    }

    fun init(context: Context) {
        val enabled = ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.Keys.KEY_ENABLE_LOGCAT_LOGGER, false)
        if (enabled) {
            start(context)
        }
    }
}
