package rikka.shizuku.server;

import moe.shizuku.server.ClientManager;
import static rikka.shizuku.server.ServerConstants.MANAGER_APPLICATION_ID;
import java.util.List;
import rikka.hidden.compat.PackageManagerApis;
import rikka.shizuku.ShizukuApiConstants;

public class ShizukuClientManager extends ClientManager<ShizukuConfigManager> {

    public ShizukuClientManager(ShizukuConfigManager configManager) {
        super(configManager);
    }

    @Override
    public ClientRecord findClient(int uid, int pid) {
        ClientRecord record = super.findClient(uid, pid);
        if (record == null) {
            List<String> packages = PackageManagerApis.getPackagesForUidNoThrow(uid);
            if (packages != null && packages.contains(MANAGER_APPLICATION_ID)) {
                record = new ClientRecord(uid, pid, null, MANAGER_APPLICATION_ID, ShizukuApiConstants.SERVER_VERSION);
                record.allowed = true;
            }
        }
        return record;
    }
}


