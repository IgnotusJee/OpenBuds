package dev.ignotus.openbuds.integration.milink;

import android.os.Bundle;

interface IMilinkBridgeCallback {
    void onSnapshotChanged(in Bundle snapshot);
    void onAdapterStatusChanged(in Bundle status);
}
