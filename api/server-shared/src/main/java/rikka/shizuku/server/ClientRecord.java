package rikka.shizuku.server;

import static rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED;

import android.os.Bundle;

import af.shizuku.server.IShizukuApplication;
import rikka.shizuku.server.util.Logger;

public class ClientRecord {

    protected static final Logger LOGGER = new Logger("ClientRecord");

    public final int uid;
    public final int pid;
    public final IShizukuApplication client;
    public final String packageName;
    public final int apiVersion;
    public final String descriptor;
    public boolean allowed;

    public ClientRecord(int uid, int pid, IShizukuApplication client, String packageName, int apiVersion) {
        this.uid = uid;
        this.pid = pid;
        this.client = client;
        this.packageName = packageName;
        this.allowed = false;
        this.apiVersion = apiVersion;

        String desc = "af.shizuku.server.IShizukuApplication";
        try {
            String remoteDesc = client.asBinder().getInterfaceDescriptor();
            if (remoteDesc != null && !remoteDesc.isEmpty()) {
                desc = remoteDesc;
            }
        } catch (Throwable ignored) {
        }
        this.descriptor = desc;
    }

    public void dispatchRequestPermissionResult(int requestCode, boolean allowed) {
        Bundle reply = new Bundle();
        reply.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed);
        try {
            if ("af.shizuku.server.IShizukuApplication".equals(this.descriptor)) {
                client.dispatchRequestPermissionResult(requestCode, reply);
            } else {
                android.os.Parcel data = android.os.Parcel.obtain();
                try {
                    data.writeInterfaceToken(this.descriptor);
                    data.writeInt(requestCode);
                    data.writeInt(1);
                    reply.writeToParcel(data, 0);
                    client.asBinder().transact(2 /* TRANSACTION_dispatchRequestPermissionResult */, data, null, android.os.IBinder.FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }
        } catch (Throwable e) {
            LOGGER.w(e, "dispatchRequestPermissionResult failed for client (uid=%d, pid=%d, package=%s)", uid, pid, packageName);
        }
    }
}
