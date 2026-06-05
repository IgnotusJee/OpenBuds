package dev.ignotus.openbuds.integration.milink;

import android.os.Bundle;

interface IMilinkTransportProxyCallback {
    Bundle executeProxyCommand(String mac, in Bundle command);
}
