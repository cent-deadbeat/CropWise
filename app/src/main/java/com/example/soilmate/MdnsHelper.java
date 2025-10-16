package com.example.soilmate;
import android.util.Log;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

public class MdnsHelper {
    private static final String TAG = "MdnsHelper";
    private static final String SERVICE_TYPE = "_thingsboard._tcp.local.";

    private JmDNS jmdns;
    private MdnsDiscoveryCallback callback;

    public interface MdnsDiscoveryCallback {
        void onServiceDiscovered(String ip, int port);
        void onDiscoveryFailed(String error);
    }

    public void startDiscovery(MdnsDiscoveryCallback callback) {
        this.callback = callback;

        new Thread(() -> {
            try {
                InetAddress addr = getInetAddress();
                jmdns = JmDNS.create(addr);

                jmdns.addServiceListener(SERVICE_TYPE, new ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent event) {
                        Log.d(TAG, "Service added: " + event.getName());
                        jmdns.requestServiceInfo(event.getType(), event.getName(), true);
                    }

                    @Override
                    public void serviceRemoved(ServiceEvent event) {
                        Log.d(TAG, "Service removed: " + event.getName());
                    }

                    @Override
                    public void serviceResolved(ServiceEvent event) {
                        ServiceInfo info = event.getInfo();
                        if (info != null && info.getInetAddresses() != null) {
                            // Try all addresses until we find a usable one
                            for (InetAddress addr : info.getInetAddresses()) {
                                String ip = addr.getHostAddress();
                                // Skip IPv6 link-local and loopback
                                if (!ip.startsWith("fe80:") && !ip.startsWith("127.")) {
                                    callback.onServiceDiscovered(ip, info.getPort());
                                    return;
                                }
                            }
                        }
                        callback.onDiscoveryFailed("No usable IP address found");
                    }
                });

                Log.d(TAG, "mDNS discovery started");

            } catch (IOException e) {
                Log.e(TAG, "mDNS discovery failed", e);
                if (callback != null) {
                    callback.onDiscoveryFailed(e.getMessage());
                }
            }
        }).start();
    }

    public void stopDiscovery() {
        new Thread(() -> {
            if (jmdns != null) {
                try {
                    jmdns.close();
                    Log.d(TAG, "mDNS discovery stopped");
                } catch (IOException e) {
                    Log.e(TAG, "Error stopping mDNS", e);
                }
            }
        }).start();
    }

    private InetAddress getInetAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress address : Collections.list(ni.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && address.isSiteLocalAddress()) {
                        return address;
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Error getting network address", e);
        }
        return null;
    }


}
