package af.shizuku.manager.shell

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Parcel
import timber.log.Timber
import af.shizuku.manager.utils.Logger.LOGGER
import rikka.shizuku.Shizuku
import af.shizuku.manager.ShizukuSettings

object ShellBinderRequestHandler {

    fun handleRequest(context: Context, intent: Intent, requireAuth: Boolean = false): Boolean {
        val interfaceToken = when (intent.action) {
            "rikka.shizuku.intent.action.REQUEST_BINDER" -> "rikka.shizuku.IShizukuService"
            "moe.shizuku.privileged.api.intent.action.REQUEST_BINDER" -> "moe.shizuku.server.IShizukuService"
            "af.shizuku.manager.action.REQUEST_BINDER" -> "af.shizuku.server.IShizukuService"
            else -> return false
        }

        if (requireAuth) {
            val rawToken = intent.getStringExtra("auth")
            val authToken = if (rawToken != null) af.shizuku.manager.utils.IntentCrypto.decrypt(rawToken) else null
            val expectedToken = ShizukuSettings.getAuthToken()
            if (authToken != expectedToken) {
                return false
            }
        }

        val binder = intent.getBundleExtra("data")?.getBinder("binder") ?: return false
        val shizukuBinder = Shizuku.getBinder()
        if (shizukuBinder == null) {
            LOGGER.e("shizuku binder is null")
            return false
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(interfaceToken)
            data.writeStrongBinder(shizukuBinder)
            binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY)
            return true
        } catch (e: Exception) {
            LOGGER.e(e, "transact")
            return false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
