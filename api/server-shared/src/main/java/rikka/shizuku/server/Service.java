package rikka.shizuku.server;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SELinux;
import android.os.SystemProperties;
import android.system.Os;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;

import af.shizuku.server.IRemoteProcess;
import af.shizuku.server.IShizukuApplication;
import af.shizuku.server.IShizukuService;
import af.shizuku.server.IShizukuServiceConnection;
import af.shizuku.server.IVirtualMachineManager;
import af.shizuku.server.IStorageProxy;
import af.shizuku.server.IAICorePlus;
import af.shizuku.server.IAIAutomationBridge;
import af.shizuku.server.IWindowManagerPlus;
import af.shizuku.server.IContinuityBridge;
import af.shizuku.server.IOverlayManagerPlus;
import af.shizuku.server.INetworkGovernorPlus;
import af.shizuku.server.IActivityManagerPlus;
import java.util.List;
import rikka.hidden.compat.PermissionManagerApis;
import rikka.rish.RishConfig;
import rikka.rish.RishService;
import rikka.shizuku.ShizukuApiConstants;
import rikka.shizuku.server.api.RemoteProcessHolder;
import rikka.shizuku.server.util.Logger;
import rikka.shizuku.server.util.OsUtils;
import af.shizuku.common.util.UserHandleCompat;

public abstract class Service<
        UserServiceMgr extends UserServiceManager,
        ClientMgr extends ClientManager<ConfigMgr>,
        ConfigMgr extends ConfigManager> extends IShizukuService.Stub {

    private final UserServiceMgr userServiceManager;
    private final ConfigMgr configManager;
    private final ClientMgr clientManager;
    private final RishService rishService;

    protected static final Logger LOGGER = new Logger("Service");

    public Service() {
        RishConfig.init(ShizukuApiConstants.BINDER_DESCRIPTOR, 30000);

        userServiceManager = onCreateUserServiceManager();
        configManager = onCreateConfigManager();
        clientManager = onCreateClientManager();
        rishService = new RishService() {

            @Override
            public void enforceCallingPermission(String func) {
                Service.this.enforceCallingPermission(func);
            }
        };
    }

    public abstract UserServiceMgr onCreateUserServiceManager();

    public abstract ClientMgr onCreateClientManager();

    public abstract ConfigMgr onCreateConfigManager();

    public final UserServiceMgr getUserServiceManager() {
        return userServiceManager;
    }

    public final ClientMgr getClientManager() {
        return clientManager;
    }

    public ConfigMgr getConfigManager() {
        return configManager;
    }

    public boolean checkCallerManagerPermission(String func, int callingUid, int callingPid) {
        return false;
    }

    public final void enforceManagerPermission(String func) {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        if (callingPid == Os.getpid()) {
            return;
        }

        if (checkCallerManagerPermission(func, callingUid, callingPid)) {
            return;
        }

        String msg = "Permission Denial: " + func + " from pid="
                + Binder.getCallingPid()
                + " is not manager ";
        LOGGER.w(msg);
        throw new SecurityException(msg);
    }

    public boolean checkCallerPermission(String func, int callingUid, int callingPid, @Nullable ClientRecord clientRecord) {
        return false;
    }

    public final void enforceCallingPermission(String func) {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        if (callingUid == OsUtils.getUid()) {
            return;
        }

        ClientRecord clientRecord = clientManager.findClient(callingUid, callingPid);

        if (checkCallerPermission(func, callingUid, callingPid, clientRecord)) {
            return;
        }

        if (clientRecord == null) {
            String msg = "Permission Denial: " + func + " from pid="
                    + Binder.getCallingPid()
                    + " is not an attached client";
            LOGGER.w(msg);
            throw new SecurityException(msg);
        }

        if (!clientRecord.allowed) {
            String msg = "Permission Denial: " + func + " from pid="
                    + Binder.getCallingPid()
                    + " requires permission";
            LOGGER.w(msg);
            throw new SecurityException(msg);
        }
    }

    public final void transactRemote(Parcel data, Parcel reply, int flags) throws RemoteException {
        enforceCallingPermission("transactRemote");

        IBinder targetBinder = data.readStrongBinder();
        int targetCode = data.readInt();
        int targetFlags;

        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        ClientRecord clientRecord = clientManager.findClient(callingUid, callingPid);

        String descriptor = null;
        if (targetBinder != null) {
            try {
                descriptor = targetBinder.getInterfaceDescriptor();
            } catch (Throwable ignored) {}
        }

        boolean hasFlags = false;
        if (descriptor != null) {
            int pos = data.dataPosition();
            data.setDataPosition(pos + 4);
            try {
                String testDescriptor = data.readString();
                if (descriptor.equals(testDescriptor)) {
                    hasFlags = true;
                }
            } catch (Throwable ignored) {}
            data.setDataPosition(pos);
        } else {
            hasFlags = (clientRecord != null && clientRecord.apiVersion >= 13);
        }

        if (hasFlags) {
            targetFlags = data.readInt();
        } else {
            targetFlags = flags;
        }
        
        // AIDL Logging (Issue #199)
        if (checkPlusFeatureEnabled("binder_logging")) {
            LOGGER.i("AIDL: uid=%d pkg=%s descriptor=%s code=%d", 
                callingUid, (clientRecord != null ? clientRecord.packageName : "unknown"), 
                descriptor, targetCode);
        }

        // Binder Firewall (Issue #199)
        if (checkPlusFeatureEnabled("binder_firewall")) {
            if (isBinderCallBlocked(callingUid, descriptor, targetCode)) {
                LOGGER.w("Firewall: Blocked transaction %s code %d from uid %d", descriptor, targetCode, callingUid);
                throw new SecurityException("Binder Firewall: Transaction blocked by Shizuku+ policy");
            }
        }

        // Shadow Binder (Issue #199) - optional interception point
        if (checkPlusFeatureEnabled("shadow_binder")) {
            if (handleShadowBinderTransaction(targetBinder, targetCode, data, reply, targetFlags)) {
                return;
            }
        }

        Parcel newData = Parcel.obtain();
        try {
            newData.appendFrom(data, data.dataPosition(), data.dataAvail());
        } catch (Throwable tr) {
            LOGGER.w(tr, "appendFrom");
            return;
        }
        
        long startTime = System.nanoTime();
        try {
            long id = Binder.clearCallingIdentity();
            targetBinder.transact(targetCode, newData, reply, targetFlags);
            Binder.restoreCallingIdentity(id);
        } finally {
            newData.recycle();
            long durationNs = System.nanoTime() - startTime;
            if (checkPlusFeatureEnabled("binder_profiler")) {
                recordTransactionMetrics(clientRecord, descriptor, targetCode, durationNs);
            }
        }
    }

    /**
     * System Health & Binder Profiler (Issue #211)
     */
    protected void recordTransactionMetrics(ClientRecord clientRecord, String descriptor, int code, long durationNs) {
        String pkg = clientRecord != null ? clientRecord.packageName : "unknown";
        LOGGER.i("PROFILER: pkg=%s desc=%s code=%d latency_ms=%.2f", pkg, descriptor, code, durationNs / 1000000.0f);
    }

    /**
     * Shadow Binder (Issue #199)
     * Allows intercepting and mocking system binder calls.
     */
    protected boolean handleShadowBinderTransaction(IBinder target, int code, Parcel data, Parcel reply, int flags) {
        // To be implemented by subclasses if shadow binder is active
        return false;
    }

    /**
     * Binder Firewall (Issue #199)
     * Checks if a binder transaction should be blocked based on UID and descriptor.
     */
    protected boolean isBinderCallBlocked(int uid, String descriptor, int code) {
        // Example: Block apps from turning off Bluetooth or WiFi via Shizuku if they aren't authorized
        if (descriptor == null) return false;
        
        // Stub for dynamic policy checking
        return false;
    }

    @Override
    public final int getVersion() {
        return ShizukuApiConstants.SERVER_VERSION;
    }

    @Override
    public final int getUid() {
        return Os.getuid();
    }

    @Override
    public final int checkPermission(String permission) throws RemoteException {
        if ("android.permission.GRANT_RUNTIME_PERMISSIONS".equals(permission)) {
            return android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return PermissionManagerApis.checkPermission(permission, Os.getuid());
    }

    @Override
    public final String getSELinuxContext() {
        enforceCallingPermission("getSELinuxContext");

        try {
            return SELinux.getContext();
        } catch (Throwable tr) {
            throw new IllegalStateException(tr.getMessage());
        }
    }

    @Override
    public final String getSystemProperty(String name, String defaultValue) {
        enforceCallingPermission("getSystemProperty");

        try {
            return SystemProperties.get(name, defaultValue);
        } catch (Throwable tr) {
            throw new IllegalStateException(tr.getMessage());
        }
    }

    @Override
    public final void setSystemProperty(String name, String value) {
        enforceCallingPermission("setSystemProperty");

        try {
            SystemProperties.set(name, value);
        } catch (Throwable tr) {
            throw new IllegalStateException(tr.getMessage());
        }
    }

    @Override
    public final int removeUserService(IShizukuServiceConnection conn, Bundle options) {
        enforceCallingPermission("removeUserService");

        return userServiceManager.removeUserService(conn, options);
    }

    @Override
    public final int addUserService(IShizukuServiceConnection conn, Bundle options) {
        enforceCallingPermission("addUserService");

        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        int callingApiVersion;

        ClientRecord clientRecord = clientManager.findClient(callingUid, callingPid);
        if (clientRecord == null) {
            callingApiVersion = ShizukuApiConstants.SERVER_VERSION;
        } else {
            callingApiVersion = clientRecord.apiVersion;
        }
        return userServiceManager.addUserService(conn, options, callingApiVersion);
    }

    @Override
    public void attachUserService(IBinder binder, Bundle options) {
        userServiceManager.attachUserService(binder, options);
    }

    @Override
    public final boolean checkSelfPermission() {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        if (callingUid == OsUtils.getUid() || callingPid == OsUtils.getPid()) {
            return true;
        }

        ClientRecord clientRecord = clientManager.findClient(callingUid, callingPid);
        if (clientRecord != null) {
            return clientRecord.allowed;
        }

        ConfigPackageEntry entry = configManager.find(callingUid);
        return entry != null && entry.isAllowed();
    }

    @Override
    public final void requestPermission(int requestCode) {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        int userId = UserHandleCompat.getUserId(callingUid);

        if (callingUid == OsUtils.getUid() || callingPid == OsUtils.getPid()) {
            return;
        }

        ClientRecord clientRecord = clientManager.requireClient(callingUid, callingPid);

        if (clientRecord.allowed) {
            clientRecord.dispatchRequestPermissionResult(requestCode, true);
            return;
        }

        ConfigPackageEntry entry = configManager.find(callingUid);
        if (entry != null && entry.isDenied()) {
            clientRecord.dispatchRequestPermissionResult(requestCode, false);
            return;
        }

        showPermissionConfirmation(requestCode, clientRecord, callingUid, callingPid, userId);
    }

    public abstract void showPermissionConfirmation(
            int requestCode, @NonNull ClientRecord clientRecord, int callingUid, int callingPid, int userId);

    @Override
    public final boolean shouldShowRequestPermissionRationale() {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        if (callingUid == OsUtils.getUid() || callingPid == OsUtils.getPid()) {
            return true;
        }

        ConfigPackageEntry entry = configManager.find(callingUid);
        return entry != null && entry.isDenied();
    }

    public IRemoteProcess newProcessInternal(String[] cmd, String[] env, String dir) {
        enforceCallingPermission("newProcess");

        LOGGER.d("newProcess: uid=%d, cmd=%s, env=%s, dir=%s", Binder.getCallingUid(), Arrays.toString(cmd), Arrays.toString(env), dir);

        java.lang.Process process;
        try {
            process = Runtime.getRuntime().exec(cmd, env, dir != null ? new File(dir) : null);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }

        ClientRecord clientRecord = clientManager.findClient(Binder.getCallingUid(), Binder.getCallingPid());
        IBinder token = clientRecord != null ? clientRecord.client.asBinder() : null;

        return new RemoteProcessHolder(process, token);
    }

    public boolean checkPlusFeatureEnabled(String key) {
        return true;
    }

    private static String enforceAndReadDescriptor(Parcel data) {
        int pos = data.dataPosition();
        try {
            data.enforceInterface("moe.shizuku.server.IShizukuService");
            return "moe.shizuku.server.IShizukuService";
        } catch (SecurityException ignored) {}
        
        data.setDataPosition(pos);
        try {
            data.enforceInterface("rikka.shizuku.IShizukuService");
            return "rikka.shizuku.IShizukuService";
        } catch (SecurityException ignored) {}
        
        data.setDataPosition(pos);
        try {
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR);
            return ShizukuApiConstants.BINDER_DESCRIPTOR;
        } catch (SecurityException ignored) {}
        
        data.setDataPosition(pos);
        return "";
    }

    @CallSuper
    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        // Support legacy interface tokens from existing Shizuku apps
        data.setDataPosition(0);
        String descriptor = enforceAndReadDescriptor(data);
        boolean isLegacy = "moe.shizuku.server.IShizukuService".equals(descriptor) || "rikka.shizuku.IShizukuService".equals(descriptor);
        boolean isNew = ShizukuApiConstants.BINDER_DESCRIPTOR.equals(descriptor);

        if (isLegacy || isNew) {
            if (code == ShizukuApiConstants.BINDER_TRANSACTION_transact) {
                transactRemote(data, reply, flags);
                return true;
            }

            if (isLegacy) {
                // Manually handle all methods for legacy callers to avoid descriptor mismatch in super.onTransact
                switch (code) {
                    case 2: // getVersion
                        reply.writeNoException();
                        reply.writeInt(getVersion());
                        return true;
                    case 3: // getUid
                        reply.writeNoException();
                        reply.writeInt(getUid());
                        return true;
                    case 4: // checkPermission
                        reply.writeNoException();
                        reply.writeInt(checkPermission(data.readString()));
                        return true;
                    case 7: // newProcess
                        String[] cmd = data.createStringArray();
                        String[] env = data.createStringArray();
                        String dir = data.readString();
                        IRemoteProcess process = newProcess(cmd, env, dir);
                        reply.writeNoException();
                        reply.writeStrongBinder(process != null ? process.asBinder() : null);
                        return true;
                    case 8: // getSELinuxContext
                        reply.writeNoException();
                        reply.writeString(getSELinuxContext());
                        return true;
                    case 9: // getSystemProperty
                        String getPropName = data.readString();
                        String getPropDefault = data.readString();
                        String getPropResult = getSystemProperty(getPropName, getPropDefault);
                        reply.writeNoException();
                        reply.writeString(getPropResult);
                        return true;
                    case 10: // setSystemProperty
                        String setPropName = data.readString();
                        String setPropValue = data.readString();
                        setSystemProperty(setPropName, setPropValue);
                        reply.writeNoException();
                        return true;
                    case 11: // addUserService
                        IShizukuServiceConnection connAdd = IShizukuServiceConnection.Stub.asInterface(data.readStrongBinder());
                        Bundle optionsAdd = data.readInt() != 0 ? Bundle.CREATOR.createFromParcel(data) : null;
                        int resultAdd = addUserService(connAdd, optionsAdd);
                        reply.writeNoException();
                        reply.writeInt(resultAdd);
                        return true;
                    case 12: // removeUserService
                        IShizukuServiceConnection connRemove = IShizukuServiceConnection.Stub.asInterface(data.readStrongBinder());
                        Bundle optionsRemove = data.readInt() != 0 ? Bundle.CREATOR.createFromParcel(data) : null;
                        int resultRemove = removeUserService(connRemove, optionsRemove);
                        reply.writeNoException();
                        reply.writeInt(resultRemove);
                        return true;
                    case 14: // legacy requestPermission (v13+) OR legacy attachApplication (pre-v13)
                        if (data.dataAvail() <= 8) {
                            int requestCode = data.readInt();
                            requestPermission(requestCode);
                            reply.writeNoException();
                            return true;
                        } else {
                            IBinder binder = data.readStrongBinder();
                            String packageName = data.readString();
                            Bundle args = new Bundle();
                            args.putString(ShizukuApiConstants.ATTACH_APPLICATION_PACKAGE_NAME, packageName);
                            args.putInt(ShizukuApiConstants.ATTACH_APPLICATION_API_VERSION, -1);
                            attachApplication(IShizukuApplication.Stub.asInterface(binder), args);
                            reply.writeNoException();
                            return true;
                        }
                    case 15: // legacy checkSelfPermission
                        boolean resultSelf = checkSelfPermission();
                        reply.writeNoException();
                        reply.writeInt(resultSelf ? 1 : 0);
                        return true;
                    case 16: // legacy shouldShowRequestPermissionRationale
                        boolean resultRationale = shouldShowRequestPermissionRationale();
                        reply.writeNoException();
                        reply.writeInt(resultRationale ? 1 : 0);
                        return true;
                    case 17:
                    case 18: // legacy attachApplication (v13+)
                        IBinder binder17 = data.readStrongBinder();
                        Bundle args17 = data.readInt() != 0 ? Bundle.CREATOR.createFromParcel(data) : null;
                        attachApplication(IShizukuApplication.Stub.asInterface(binder17), args17);
                        reply.writeNoException();
                        return true;
                    case 107: {
                        reply.writeNoException();
                        IVirtualMachineManager vmm = getVirtualMachineManager();
                        reply.writeStrongBinder(vmm != null ? vmm.asBinder() : null);
                        return true;
                    }
                    case 108: {
                        reply.writeNoException();
                        IStorageProxy sp = getStorageProxy();
                        reply.writeStrongBinder(sp != null ? sp.asBinder() : null);
                        return true;
                    }
                    case 109: {
                        reply.writeNoException();
                        IAICorePlus aic = getAICorePlus();
                        reply.writeStrongBinder(aic != null ? aic.asBinder() : null);
                        return true;
                    }
                    case 110: {
                        reply.writeNoException();
                        IWindowManagerPlus wmp = getWindowManagerPlus();
                        reply.writeStrongBinder(wmp != null ? wmp.asBinder() : null);
                        return true;
                    }
                    case 111: {
                        reply.writeNoException();
                        IContinuityBridge cb = getContinuityBridge();
                        reply.writeStrongBinder(cb != null ? cb.asBinder() : null);
                        return true;
                    }
                    case 112: {
                        String key = data.readString();
                        boolean enabled = data.readInt() != 0;
                        updatePlusFeatureEnabled(key, enabled);
                        reply.writeNoException();
                        return true;
                    }
                    case 113: {
                        reply.writeNoException();
                        IOverlayManagerPlus omp = getOverlayManagerPlus();
                        reply.writeStrongBinder(omp != null ? omp.asBinder() : null);
                        return true;
                    }
                    case 114: {
                        reply.writeNoException();
                        INetworkGovernorPlus ngp = getNetworkGovernorPlus();
                        reply.writeStrongBinder(ngp != null ? ngp.asBinder() : null);
                        return true;
                    }
                    case 115: {
                        reply.writeNoException();
                        IActivityManagerPlus amp = getActivityManagerPlus();
                        reply.writeStrongBinder(amp != null ? amp.asBinder() : null);
                        return true;
                    }
                    case 116: {
                        String key = data.readString();
                        String value = data.readString();
                        setPlusSetting(key, value);
                        reply.writeNoException();
                        return true;
                    }
                    case 117: {
                        String pkg = data.readString();
                        elevateApp(pkg);
                        reply.writeNoException();
                        return true;
                    }
                    case 118: {
                        List<String> logs = getRecentLogs();
                        reply.writeNoException();
                        reply.writeStringList(logs);
                        return true;
                    }
                    case 119: {
                        String key = data.readString();
                        String val = getPlusSetting(key);
                        reply.writeNoException();
                        reply.writeString(val);
                        return true;
                    }
                    case 120: {
                        String key = data.readString();
                        boolean enabled = isPlusFeatureEnabled(key);
                        reply.writeNoException();
                        reply.writeInt(enabled ? 1 : 0);
                        return true;
                    }
                    case 121: {
                        IAIAutomationBridge bridge = IAIAutomationBridge.Stub.asInterface(data.readStrongBinder());
                        registerAIAutomationBridge(bridge);
                        reply.writeNoException();
                        return true;
                    }
                }
            } else {
                // Shizuku+ specific handling for code 14, 17, and 18
                if (code == 14 /* requestPermission */) {
                    requestPermission(data.readInt());
                    reply.writeNoException();
                    return true;
                } else if (code == 17 || code == 18 /* attachApplication v13+ */) {
                    IBinder binder = data.readStrongBinder();
                    Bundle args = data.readInt() != 0 ? Bundle.CREATOR.createFromParcel(data) : null;
                    attachApplication(IShizukuApplication.Stub.asInterface(binder), args);
                    reply.writeNoException();
                    return true;
                }
            }
        }

        data.setDataPosition(0);
        try {
            if (rishService.onTransact(code, data, reply, flags)) {
                return true;
            }
        } catch (SecurityException ignored) {
            // enforceInterface throws this if the descriptor doesn't match IRishService
        }
        data.setDataPosition(0);
        return super.onTransact(code, data, reply, flags);
    }
}
