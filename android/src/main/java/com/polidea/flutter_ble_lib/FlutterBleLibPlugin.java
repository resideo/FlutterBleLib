package com.polidea.flutter_ble_lib;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.polidea.flutter_ble_lib.constant.ArgumentKey;
import com.polidea.flutter_ble_lib.constant.ChannelName;
import com.polidea.flutter_ble_lib.constant.MethodName;
import com.polidea.flutter_ble_lib.delegate.BluetoothStateDelegate;
import com.polidea.flutter_ble_lib.delegate.CallDelegate;
import com.polidea.flutter_ble_lib.delegate.CharacteristicsDelegate;
import com.polidea.flutter_ble_lib.delegate.DescriptorsDelegate;
import com.polidea.flutter_ble_lib.delegate.DeviceConnectionDelegate;
import com.polidea.flutter_ble_lib.delegate.DevicesDelegate;
import com.polidea.flutter_ble_lib.delegate.LogLevelDelegate;
import com.polidea.flutter_ble_lib.delegate.DiscoveryDelegate;
import com.polidea.flutter_ble_lib.delegate.MtuDelegate;
import com.polidea.flutter_ble_lib.delegate.RssiDelegate;
import com.polidea.flutter_ble_lib.event.AdapterStateStreamHandler;
import com.polidea.flutter_ble_lib.event.CharacteristicsMonitorStreamHandler;
import com.polidea.flutter_ble_lib.event.ConnectionStateStreamHandler;
import com.polidea.flutter_ble_lib.event.RestoreStateStreamHandler;
import com.polidea.flutter_ble_lib.event.ScanningStreamHandler;
import com.polidea.multiplatformbleadapter.BleAdapter;
import com.polidea.multiplatformbleadapter.BleAdapterFactory;
import com.polidea.multiplatformbleadapter.OnErrorCallback;
import com.polidea.multiplatformbleadapter.OnEventCallback;
import com.polidea.multiplatformbleadapter.ScanResult;
import com.polidea.multiplatformbleadapter.errors.BleError;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding;

public class FlutterBleLibPlugin implements FlutterPlugin, MethodCallHandler {

    static final String TAG = FlutterBleLibPlugin.class.getName();

    private BleAdapter bleAdapter;
    private Context context;
    
    private MethodChannel methodChannel;
    EventChannel bluetoothStateChannel;
    EventChannel restoreStateChannel;
    EventChannel scanningChannel;
    EventChannel connectionStateChannel;
    EventChannel characteristicMonitorChannel;

    private AdapterStateStreamHandler adapterStateStreamHandler = new AdapterStateStreamHandler();
    private RestoreStateStreamHandler restoreStateStreamHandler = new RestoreStateStreamHandler();
    private ScanningStreamHandler scanningStreamHandler = new ScanningStreamHandler();
    private ConnectionStateStreamHandler connectionStateStreamHandler = new ConnectionStateStreamHandler();
    private CharacteristicsMonitorStreamHandler characteristicsMonitorStreamHandler = new CharacteristicsMonitorStreamHandler();

    private List<CallDelegate> delegates = new LinkedList<>();

    public static void registerWith(Registrar registrar) {
        final FlutterBleLibPlugin plugin = new FlutterBleLibPlugin();

        plugin.onAttachedToEngine(registrar.context(), registrar.messenger());
    }

    public FlutterBleLibPlugin() {}

    private FlutterBleLibPlugin(Context context) {
        this.context = context;
    }

    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        onAttachedToEngine(binding.getApplicationContext(), binding.getBinaryMessenger());
    }

    private void onAttachedToEngine(Context context, BinaryMessenger messenger) {
        this.context = context;

        methodChannel = new MethodChannel(messenger, ChannelName.FLUTTER_BLE_LIB);

        scanningChannel = new EventChannel(messenger, ChannelName.SCANNING_EVENTS);
        bluetoothStateChannel = new EventChannel(messenger, ChannelName.ADAPTER_STATE_CHANGES);
        restoreStateChannel = new EventChannel(messenger, ChannelName.STATE_RESTORE_EVENTS);
        connectionStateChannel = new EventChannel(messenger, ChannelName.CONNECTION_STATE_CHANGE_EVENTS);
        characteristicMonitorChannel = new EventChannel(messenger, ChannelName.MONITOR_CHARACTERISTIC);

        methodChannel.setMethodCallHandler(this);

        scanningChannel.setStreamHandler(this.scanningStreamHandler);
        bluetoothStateChannel.setStreamHandler(this.adapterStateStreamHandler);
        restoreStateChannel.setStreamHandler(this.restoreStateStreamHandler);
        connectionStateChannel.setStreamHandler(this.connectionStateStreamHandler);
        characteristicMonitorChannel.setStreamHandler(this.characteristicsMonitorStreamHandler);
    }

    @Override
    public void onDetachedFromEngine(FlutterPluginBinding binding) {
        context = null;
        methodChannel.setMethodCallHandler(null);
        methodChannel = null;

        scanningChannel.setStreamHandler(null);
        bluetoothStateChannel.setStreamHandler(null);
        restoreStateChannel.setStreamHandler(null);
        connectionStateChannel.setStreamHandler(null);
        characteristicMonitorChannel.setStreamHandler(null);

        scanningChannel = null;
        bluetoothStateChannel = null;
        restoreStateChannel = null;
        connectionStateChannel = null;
        characteristicMonitorChannel = null;
    }

    private void setupAdapter(Context context) {
        bleAdapter = BleAdapterFactory.getNewAdapter(context);
        delegates.add(new DeviceConnectionDelegate(bleAdapter, connectionStateStreamHandler));
        delegates.add(new LogLevelDelegate(bleAdapter));
        delegates.add(new DiscoveryDelegate(bleAdapter));
        delegates.add(new BluetoothStateDelegate(bleAdapter));
        delegates.add(new RssiDelegate(bleAdapter));
        delegates.add(new MtuDelegate(bleAdapter));
        delegates.add(new CharacteristicsDelegate(bleAdapter, characteristicsMonitorStreamHandler));
        delegates.add(new DevicesDelegate(bleAdapter));
        delegates.add(new DescriptorsDelegate(bleAdapter));
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        Log.d(TAG, "on native side observed method: " + call.method);
        for (CallDelegate delegate : delegates) {
            if (delegate.canHandle(call)) {
                delegate.onMethodCall(call, result);
                return;
            }
        }

        switch (call.method) {
            case MethodName.CREATE_CLIENT:
                createClient(call, result);
                break;
            case MethodName.DESTROY_CLIENT:
                destroyClient(result);
                break;
            case MethodName.START_DEVICE_SCAN:
                startDeviceScan(call, result);
                break;
            case MethodName.STOP_DEVICE_SCAN:
                stopDeviceScan(result);
                break;
            case MethodName.CANCEL_TRANSACTION:
                cancelTransaction(call, result);
                break;
            case MethodName.IS_CLIENT_CREATED:
                isClientCreated(result);
                break;
            default:
                result.notImplemented();
        }
    }

    private void isClientCreated(final Result result) {
        runOnUIThread(new Runnable() {
            @Override
            public void run() {
                result.success(bleAdapter != null);
            }
        });

    }

    private void createClient(MethodCall call, final Result result) {
        if (bleAdapter != null) {
            Log.w(TAG,
                    "Overwriting existing native client. Use BleManager#isClientCreated to check whether a client already exists.");
        }
        setupAdapter(context);
        bleAdapter.createClient(call.<String>argument(ArgumentKey.RESTORE_STATE_IDENTIFIER),
                new OnEventCallback<String>() {
                    @Override
                    public void onEvent(String adapterState) {
                        adapterStateStreamHandler.onNewAdapterState(adapterState);
                    }
                }, new OnEventCallback<Integer>() {
                    @Override
                    public void onEvent(Integer restoreStateIdentifier) {
                        restoreStateStreamHandler.onRestoreEvent(restoreStateIdentifier);
                    }
                });
        runOnUIThread(new Runnable() {
            @Override
            public void run() {
                result.success(null);
            }
        });
    }

    private void destroyClient(final Result result) {
        if (bleAdapter != null) {
            bleAdapter.destroyClient();
        }
        scanningStreamHandler.onComplete();
        connectionStateStreamHandler.onComplete();
        bleAdapter = null;
        delegates.clear();

        runOnUIThread(new Runnable() {
            @Override
            public void run() {
                result.success(null);
            }
        });
    }

    private void startDeviceScan(@NonNull MethodCall call, final Result result) {
        List<String> uuids = call.<List<String>>argument(ArgumentKey.UUIDS);
        bleAdapter.startDeviceScan(uuids.toArray(new String[uuids.size()]),
                call.<Integer>argument(ArgumentKey.SCAN_MODE),
                call.<Integer>argument(ArgumentKey.CALLBACK_TYPE),
                new OnEventCallback<ScanResult>() {
                    @Override
                    public void onEvent(ScanResult data) {
                        scanningStreamHandler.onScanResult(data);
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(final BleError error) {
                        runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                scanningStreamHandler.onError(error);
                            }
                        });

                    }
                });
        runOnUIThread(new Runnable() {
            @Override
            public void run() {
                result.success(null);
            }
        });
    }

    private void stopDeviceScan(final Result result) {
        if (bleAdapter != null) {
            bleAdapter.stopDeviceScan();
        }
        runOnUIThread(new Runnable() {
            @Override
            public void run() {
                scanningStreamHandler.onComplete();
                result.success(null);
            }
        });
    }

    private void cancelTransaction(MethodCall call, final Result result) {
        if (bleAdapter != null) {
            bleAdapter.cancelTransaction(call.<String>argument(ArgumentKey.TRANSACTION_ID));
        }
        runOnUIThread(new Runnable() {
            @Override
            public void run() {
                result.success(null);
            }
        });

    }

    private void runOnUIThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

}
