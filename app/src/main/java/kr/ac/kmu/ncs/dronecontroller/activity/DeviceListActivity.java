package kr.ac.kmu.ncs.dronecontroller.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.parrot.arsdk.ARSDK;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;

import java.util.ArrayList;
import java.util.List;

import kr.ac.kmu.ncs.dronecontroller.Constants;
import kr.ac.kmu.ncs.dronecontroller.R;
import kr.ac.kmu.ncs.dronecontroller.discovery.DroneDiscoverer;

/**
 * Created by NCS-KSW on 2016-09-12.
 */
public class DeviceListActivity extends AppCompatActivity {
    public static final String TAG = "DeviceListActivity";

    public DroneDiscoverer mDroneDiscoverer;

    private final List<ARDiscoveryDeviceService> mDronesList = new ArrayList<>();

    /*
    Loads the native libraries, it's mandatory!
     */
    static {
        ARSDK.loadSDKLibs();
    }

    /*
    OnCreate
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        final ListView listView = (ListView) findViewById(R.id.lv_device_list);

        //  assign adapter to ListView
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // launch the activity related to the type
                Intent intent = null;

                ARDiscoveryDeviceService service = (ARDiscoveryDeviceService) mAdapter.getItem(position);
                ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(service.getProductID());

                switch (product) {
                    case ARDISCOVERY_PRODUCT_BEBOP_2:
                        intent = new Intent(DeviceListActivity.this, BebopActivity.class);
                    default:
                        Log.e(TAG, "The type " + product + " is not supported by this app");
                }

                if(intent != null){
                    intent.putExtra(Constants.EXTRA_DEVICE_SERVICE, service);
                    startActivity(intent);
                }
            }
        });

        mDroneDiscoverer = new DroneDiscoverer(this);
    }

    /*
    onResume
     */
    @Override
    protected void onResume() {
        super.onResume();

        //  setup the drone discoverer and register as listener
        mDroneDiscoverer.setup();
        mDroneDiscoverer.addListener(mDiscovererListener);

        //  start discovering
        mDroneDiscoverer.startDiscovering();
    }

    /*
    onPause
     */
    @Override
    protected void onPause() {
        super.onPause();

        //  clean the drone discovere object
        mDroneDiscoverer.stopDiscovering();
        mDroneDiscoverer.cleanup();
        mDroneDiscoverer.removeListener(mDiscovererListener);
    }

    /*
    Discoverer Listener
     */
    private final DroneDiscoverer.Listener mDiscovererListener = new DroneDiscoverer.Listener() {
        @Override
        public void onDroneListUpdated(List<ARDiscoveryDeviceService> droneList) {
            mDronesList.clear();
            mDronesList.addAll(droneList);

            mAdapter.notifyDataSetChanged();
        }
    };

    static class ViewHolder{
        public TextView textView;
    }

    private final BaseAdapter mAdapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return mDronesList.size();
        }

        @Override
        public Object getItem(int position) {
            return mDronesList.get(position);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;

            // reuse views
            if(rowView == null){
                LayoutInflater inflater = getLayoutInflater();
                rowView = inflater.inflate(android.R.layout.simple_list_item_1, null);

                //  configure view holder
                ViewHolder viewHolder = new ViewHolder();
                viewHolder.textView = (TextView) rowView.findViewById(android.R.id.text1);
                rowView.setTag(viewHolder);
            }

            //  fill data
            ViewHolder holder = (ViewHolder) rowView.getTag();
            ARDiscoveryDeviceService service = (ARDiscoveryDeviceService)getItem(position);
            holder.textView.setText(service.getName());

            return rowView;
        }
    };
}
