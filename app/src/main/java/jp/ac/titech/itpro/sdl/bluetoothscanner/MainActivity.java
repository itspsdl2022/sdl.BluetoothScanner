package jp.ac.titech.itpro.sdl.bluetoothscanner;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();

    private final static int REQ_ENABLE_BLUETOOTH = 1111;
    private final static int REQ_PERMISSIONS = 2222;
    private final static String[] PERMISSIONS = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private final static String KEY_DEVLIST = "MainActivity.devices";

    private ProgressBar scanProgress;
    private ListView devicesView;
    private ArrayAdapter<BluetoothDevice> devicesAdapter;
    private ArrayList<BluetoothDevice> devices = null;

    private BluetoothAdapter bluetoothAdapter;
    private BroadcastReceiver scanReceiver;
    private IntentFilter scanFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        // bluetooth device list
        if (savedInstanceState != null) {
            devices = savedInstanceState.getParcelableArrayList(KEY_DEVLIST);
        }
        if (devices == null) {
            devices = new ArrayList<>();
        }

        devicesAdapter = new ArrayAdapter<BluetoothDevice>(this, 0, devices) {
            @Override
            public @NonNull
            View getView(int pos, @Nullable View view, @NonNull ViewGroup parent) {
                if (view == null) {
                    LayoutInflater inflater = LayoutInflater.from(getContext());
                    view = inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
                }
                TextView nameView = view.findViewById(android.R.id.text1);
                TextView addrView = view.findViewById(android.R.id.text2);
                BluetoothDevice device = getItem(pos);
                if (device != null) {
                    String bonded = device.getBondState() == BluetoothDevice.BOND_BONDED ? "*" : " ";
                    String caption = caption(device);
                    nameView.setText(getString(R.string.format_dev_name, caption, bonded));
                    addrView.setText(device.getAddress());
                }
                return view;
            }
        };

        devicesView = findViewById(R.id.devices);
        devicesView.setAdapter(devicesAdapter);
        devicesView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                BluetoothDevice device = (BluetoothDevice) parent.getItemAtPosition(pos);
                new AlertDialog.Builder(view.getContext())
                        .setTitle(caption(device))
                        .setMessage(device.getAddress())
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });

        // progress bar fo scanning
        scanProgress = findViewById(R.id.scan_progress);

        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "onReceive: " + action);
                if (action == null) {
                    return;
                }
                switch (action) {
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                        scanProgress.setIndeterminate(true);
                        invalidateOptionsMenu();
                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        scanProgress.setIndeterminate(false);
                        invalidateOptionsMenu();
                        break;
                    case BluetoothDevice.ACTION_FOUND:
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (devicesAdapter.getPosition(device) < 0) {
                            devicesAdapter.add(device);
                        }
                        devicesView.smoothScrollToPosition(devicesAdapter.getCount());
                        break;
                }
            }
        };
        scanFilter = new IntentFilter();
        scanFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        scanFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        scanFilter.addAction(BluetoothDevice.ACTION_FOUND);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            String text = getString(R.string.toast_bluetooth_is_not_available);
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQ_ENABLE_BLUETOOTH);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        registerReceiver(scanReceiver, scanFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        unregisterReceiver(scanReceiver);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState");
        outState.putParcelableArrayList(KEY_DEVLIST, devices);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.main, menu);
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_stop).setVisible(true);
        } else {
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_stop).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()) {
            case R.id.menu_scan:
                startScan();
                return true;
            case R.id.menu_stop:
                stopScan();
                return true;
            case R.id.menu_about:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.about_dialog_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        Log.d(TAG, "onActivityResult");
        switch (reqCode) {
            case REQ_ENABLE_BLUETOOTH:
                if (resCode != Activity.RESULT_OK) {
                    String text = getString(R.string.toast_bluetooth_must_be_enabled);
                    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
        super.onActivityResult(reqCode, resCode, data);
    }

    private void startScan() {
        Log.d(TAG, "startScan");
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, REQ_PERMISSIONS);
                return;
            }
        }
        startScan1();
    }

    @Override
    public void onRequestPermissionsResult(int reqCode, @NonNull String[] permissions, @NonNull int[] grants) {
        Log.d(TAG, "onRequestPermissionsResult");
        switch (reqCode) {
            case REQ_PERMISSIONS:
                for (int i = 0; i < grants.length; i++) {
                    if (grants[i] != PackageManager.PERMISSION_GRANTED) {
                        String text = getString(R.string.error_scanning_requires_permission, permissions[i]);
                        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                startScan1();
                break;
        }
    }

    private void startScan1() {
        Log.d(TAG, "startScan1");
        devicesAdapter.clear();
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();
    }

    private void stopScan() {
        Log.d(TAG, "stopScan");
        bluetoothAdapter.cancelDiscovery();
    }

    private static String caption(BluetoothDevice device) {
        String name = device.getName();
        return name == null ? "(no name)" : name;
    }
}