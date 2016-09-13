package kr.ac.kmu.ncs.dronecontroller.discovery;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.parrot.arsdk.ardiscovery.receivers.ARDiscoveryServicesDevicesListUpdatedReceiver;
import com.parrot.arsdk.ardiscovery.receivers.ARDiscoveryServicesDevicesListUpdatedReceiverDelegate;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by NCS-KSW on 2016-09-12.
 */
public class DroneDiscoverer {
    private static final String TAG = "DroneDiscoverer";

    public interface Listener {
        void onDroneListUpdated(List<ARDiscoveryDeviceService> droneList);
    }

    private final List<Listener> mListeners;

    private final Context mCtx;

    private ARDiscoveryService mArDiscoveryService;
    private ServiceConnection mArDiscoveryServiceConnection;
    private final ARDiscoveryServicesDevicesListUpdatedReceiver mArDiscoveryServicesDevicesListUpdatedReceiver;

    private final List<ARDiscoveryDeviceService> mMatchingDrones;

    private boolean mStartDiscoveryAfterConnection;

    /*
    Constructor
     */
    public DroneDiscoverer(Context ctx) {
        mCtx = ctx;

        mListeners = new ArrayList<>();
        mMatchingDrones = new ArrayList<>();
        mArDiscoveryServicesDevicesListUpdatedReceiver = new ARDiscoveryServicesDevicesListUpdatedReceiver(mDiscoveryListener);
    }

    /*
    Add a Listener
     */
    public void addListener(Listener listener) {
        mListeners.add(listener);

        notifyServiceDiscovered(mMatchingDrones);
    }

    /*
    Remove a Listener
     */
    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    /*
    Setup the drone discoverer
     */
    public void setup() {
        //  register receivers
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager.getInstance(mCtx);
        localBroadcastMgr.registerReceiver(mArDiscoveryServicesDevicesListUpdatedReceiver, new IntentFilter(ARDiscoveryService.kARDiscoveryServiceNotificationServicesDevicesListUpdated));

        //  create the service connection
        if (mArDiscoveryServiceConnection == null) {
            mArDiscoveryServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder service) {
                    mArDiscoveryService = ((ARDiscoveryService.LocalBinder) service).getService();

                    if (mStartDiscoveryAfterConnection) {
                        startDiscovering();
                        mStartDiscoveryAfterConnection = false;
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    mArDiscoveryService = null;
                }
            };
        }

        //  if the discovery service doensn't exists, bind to it
        if (mArDiscoveryService == null) {
            Intent intent = new Intent(mCtx, ARDiscoveryService.class);
            mCtx.bindService(intent, mArDiscoveryServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    /*
    Cleanup the object
     */
    public void cleanup() {
        //  close discovery service
        stopDiscovering();

        Log.d(TAG, "closeServices ...");

        if (mArDiscoveryService != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mArDiscoveryService.stop();

                    mCtx.unbindService(mArDiscoveryServiceConnection);
                    mArDiscoveryService = null;
                }
            }).start();
        }

        //  unregister receivers
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager.getInstance(mCtx);
        localBroadcastMgr.unregisterReceiver(mArDiscoveryServicesDevicesListUpdatedReceiver);
    }

    /*
    Start discovering Parrot drones
     */
    public void startDiscovering() {
        if (mArDiscoveryService != null) {
            Log.i(TAG, "Start discoverting");
            mDiscoveryListener.onServicesDevicesListUpdated();
            mArDiscoveryService.start();
            mStartDiscoveryAfterConnection = false;
        } else {
            mStartDiscoveryAfterConnection = true;
        }
    }

    /*
    Stop discovering parrot drones
     */
    public void stopDiscovering() {
        if (mArDiscoveryService != null) {
            Log.i(TAG, "Stop discovering");
            mArDiscoveryService.stop();
        }
        mStartDiscoveryAfterConnection = false;
    }

    private void notifyServiceDiscovered(List<ARDiscoveryDeviceService> droneList) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onDroneListUpdated(droneList);
        }
    }

    private final ARDiscoveryServicesDevicesListUpdatedReceiverDelegate mDiscoveryListener = new ARDiscoveryServicesDevicesListUpdatedReceiverDelegate() {
        @Override
        public void onServicesDevicesListUpdated() {
            if (mArDiscoveryService != null) {
                mMatchingDrones.clear();
                List<ARDiscoveryDeviceService> deviceList = mArDiscoveryService.getDeviceServicesArray();

                if(deviceList != null){
                    for (ARDiscoveryDeviceService service : deviceList){
                        mMatchingDrones.add(service);
                    }
                }
                notifyServiceDiscovered(mMatchingDrones);
            }
        }
    };
}
