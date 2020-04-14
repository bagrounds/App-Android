package edu.uw.covidsafe.ble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import com.example.covidsafe.R;

import edu.uw.covidsafe.seed_uuid.UUIDGeneratorTask;
import edu.uw.covidsafe.utils.ByteUtils;
import edu.uw.covidsafe.utils.Constants;
import edu.uw.covidsafe.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static android.content.Context.BLUETOOTH_SERVICE;

public class BluetoothUtils {

    // react appropriately if user turns of bluetooth off/on in middle of logging
    // this broadcast receiver is only registered when the logging is in process
    public static final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            SharedPreferences prefs = context.getSharedPreferences(Constants.SHARED_PREFENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = context.getSharedPreferences(Constants.SHARED_PREFENCE_NAME, Context.MODE_PRIVATE).edit();

            if (Constants.blueAdapter == null) {
                BluetoothManager bluetoothManager =
                        (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
                Constants.blueAdapter = bluetoothManager.getAdapter();
            }

            // It means the user has changed his bluetooth state.
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (Constants.blueAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                    if (Constants.LoggingServiceRunning) {
                        BluetoothUtils.haltBle(context);
                    }
                    editor.putBoolean(context.getString(R.string.ble_enabled_pkey), false);
                    editor.commit();
                    if (Constants.bleSwitch != null) {
                        Constants.bleSwitch.setChecked(false);
                    }
                    if (Constants.bleDesc != null) {
                        Constants.bleDesc.setText(context.getString(R.string.bluetooth_is_off));
                    }
                    return;
                }
                if (Constants.blueAdapter.getState() == BluetoothAdapter.STATE_ON) {
                    Log.e("ble","BLE TURNED ON");
                    if (Constants.LoggingServiceRunning) {
                        // the bluetooth sensor is turned on
                        BluetoothUtils.startBluetoothScan(context);
                        BluetoothServerHelper.createServer(context);
                        mkBeacon();
                    }

                    if (Utils.hasBlePermissions(context)){
                        editor.putBoolean(context.getString(R.string.ble_enabled_pkey), true);
                        editor.commit();
                        if (Constants.bleSwitch != null) {
                            Constants.bleSwitch.setChecked(true);
                        }
                        if (Constants.bleDesc != null) {
                            Constants.bleDesc.setText(context.getString(R.string.perm3desc));
                        }
                    }
                    return;
                }
            }
        }
    };

    public static void startBluetoothScan(Context context) {
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
        Log.e("ble","start bluetooth scan ");
        Constants.bluetoothScanTask = exec.scheduleWithFixedDelay(new BluetoothScanHelper(context), 0, Constants.BluetoothScanIntervalInMinutes, TimeUnit.MINUTES);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                BluetoothUtils.finishScan(context);
                Log.e("ble", "STOPPED SCANNING");
            }
        }, Constants.BluetoothScanPeriodInSeconds*1000);
    }

    public static void finishScan(Context cxt) {
        if (Constants.blueAdapter != null && Constants.blueAdapter.getBluetoothLeScanner() != null) {
            Constants.blueAdapter.getBluetoothLeScanner().stopScan(BluetoothScanHelper.mLeScanCallback);
        }

        if (Constants.scannedUUIDs != null) {
            for (String uuids : Constants.scannedUUIDs) {
                String[] elts = uuids.split("-");
                int rssi = Constants.scannedUUIDsRSSIs.get(uuids);
                Utils.bleLogToDatabase(cxt, uuids, rssi);
            }
        }
    }

    public static void haltBle(Context av) {
        if (Constants.blueAdapter != null && Constants.blueAdapter.getBluetoothLeAdvertiser() != null) {
            Constants.blueAdapter.getBluetoothLeAdvertiser().stopAdvertising(BluetoothScanHelper.advertiseCallback);
        }
        BluetoothUtils.finishScan(av);
        BluetoothServerHelper.stopServer();

        if (Constants.uuidGeneartionTask != null) {
            Constants.uuidGeneartionTask.cancel(true);
        }
    }

    public static void startBle(Context cxt) {
        Log.e("ble","spin out task ");
        BluetoothUtils.startBluetoothScan(cxt);
        BluetoothServerHelper.createServer(cxt);
        Log.e("ble","make beacon");
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
        // run this once to get a seed and broadcast it
        // have the generator be triggered at synchronized fixed 15 minute intervals:
        // e.g. 10:15, 10:30, 10:45
        Constants.uuidGeneartionTask = exec.scheduleWithFixedDelay(
                new UUIDGeneratorTask(cxt), getDelayInSeconds(), Constants.UUIDGenerationIntervalInSeconds, TimeUnit.SECONDS);
    }

    public static int getDelayInSeconds() {
        SimpleDateFormat hourFormat = new SimpleDateFormat("hh");
        SimpleDateFormat minuteFormat = new SimpleDateFormat("mm");
        SimpleDateFormat secondFormat = new SimpleDateFormat("ss");

        Date dd = new Date();

        int hour = Integer.parseInt(hourFormat.format(dd));
        int minute = Integer.parseInt(minuteFormat.format(dd));
        int second = Integer.parseInt(secondFormat.format(dd));

        List<Integer> intervals = new LinkedList<>();
        for (int i = 0; i < 60/Constants.UUIDGenerationIntervalInMinutes; i++) {
            intervals.add(i*Constants.UUIDGenerationIntervalInMinutes);
        }

        int delay = 0;
        if (second == 0 && intervals.contains(minute)) {
            delay = 0;
        }
        else {
            int closestIntervalIndex = 0;
            if (minute < intervals.get(intervals.size()-1)) {
                for (int i = 0; i < intervals.size(); i++) {
                    if (minute >= intervals.get(i)) {
                        closestIntervalIndex = i+1;
                    }
                }
            }

            int targetMinute = intervals.get(closestIntervalIndex);
            String time = hour+":"+targetMinute;
            if (intervals.get(closestIntervalIndex) == 0) {
                time = (hour+1)+":"+targetMinute;
            }

            int secondDelay = 60-second;
            int minuteDelay = targetMinute-minute-1;
            if (targetMinute == 0) {
                secondDelay = 60-second;
                minuteDelay = 60-minute-1;
            }
            delay = minuteDelay*60+secondDelay;
//            System.out.println("closest interval "+hour+":"+minute+"."+second+","+time+"=>"+minuteDelay+","+secondDelay+","+delay);
        }
        return delay;
    }

    public static void mkBeacon() {
        if (Constants.BLUETOOTH_ENABLED) {
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(true)
                    .build();

            AdvertiseData advertiseData = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(new ParcelUuid(Constants.BEACON_SERVICE_UUID))
                    .addServiceData(new ParcelUuid(Constants.BEACON_SERVICE_UUID), ByteUtils.uuid2bytes(Constants.contactUUID))
                    .build();
            Log.e("ble","MKBEACON");
            BluetoothLeAdvertiser bluetoothLeAdvertiser = Constants.blueAdapter.getBluetoothLeAdvertiser();
            bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, BluetoothScanHelper.advertiseCallback);
        }
    }

    public static boolean checkBluetoothSupport(Activity av) {
        BluetoothManager mBluetoothManager = (BluetoothManager) av.getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            return false;
        }

        if (!av.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false;
        }

        return true;
    }

    public static boolean isBluetoothOn(Activity av) {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null) {
            if (!mBluetoothAdapter.isEnabled()) {
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    public static void turnOnBluetooth(Activity av) {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        av.startActivityForResult(enableBtIntent, 0);
    }
}
