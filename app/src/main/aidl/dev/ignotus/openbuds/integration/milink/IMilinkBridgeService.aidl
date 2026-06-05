package dev.ignotus.openbuds.integration.milink;

import android.os.Bundle;
import dev.ignotus.openbuds.integration.milink.IMilinkBridgeCallback;
import dev.ignotus.openbuds.integration.milink.IMilinkTransportProxyCallback;

interface IMilinkBridgeService {
    Bundle openSession();
    Bundle getAdapterStatus(String token);
    Bundle getAuthorizedDevices(String token);
    Bundle getDeviceSnapshot(String token, String mac);
    Bundle executeCommand(String token, String mac, in Bundle command);
    void registerCallback(String token, IMilinkBridgeCallback callback);
    void unregisterCallback(String token, IMilinkBridgeCallback callback);

    Bundle openTransportProxySession();
    Bundle registerTransportProxy(String token, String mac, in Bundle capabilities, IMilinkTransportProxyCallback callback);
    Bundle publishTransportProxySnapshot(String token, in Bundle snapshot);
    Bundle unregisterTransportProxy(String token, String mac);
}
