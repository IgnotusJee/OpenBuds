package dev.ignotus.openbuds.integration.milink;

import android.os.Bundle;
import dev.ignotus.openbuds.integration.milink.IMilinkBridgeCallback;

interface IMilinkBridgeService {
    Bundle openSession();
    Bundle getAdapterStatus(String token);
    Bundle getAuthorizedDevices(String token);
    Bundle getDeviceSnapshot(String token, String mac);
    void registerCallback(String token, IMilinkBridgeCallback callback);
    void unregisterCallback(String token, IMilinkBridgeCallback callback);
}
