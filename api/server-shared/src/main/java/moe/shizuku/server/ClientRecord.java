package moe.shizuku.server;

import static rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED;

import android.os.Bundle;

import moe.shizuku.server.IShizukuApplication;
import moe.shizuku.server.util.Logger;

public class ClientRecord {

    protected static final Logger LOGGER = new Logger("ClientRecord");

    public final int uid;
    public final int pid;
    public final IShizukuApplication client;
    public final String packageName;
    public final int apiVersion;
    public boolean allowed;

    public ClientRecord(int uid, int pid, IShizukuApplication client, String packageName, int apiVersion) {
        this.uid = uid;
        this.pid = pid;
        this.client = client;
        this.packageName = packageName;
        this.allowed = false;
        this.apiVersion = apiVersion;
    }

    public void dispatchRequestPermissionResult(int requestCode, boolean allowed) {
        if (client == null) {
            LOGGER.w("dispatchRequestPermissionResult skipped: no client binder for (uid=%d, pid=%d, package=%s)", uid, pid, packageName);
            return;
        }
        Bundle reply = new Bundle();
        reply.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed);
        try {
            client.dispatchRequestPermissionResult(requestCode, reply);
        } catch (Throwable e) {
            LOGGER.w(e, "dispatchRequestPermissionResult failed for client (uid=%d, pid=%d, package=%s)", uid, pid, packageName);
        }
    }
}
