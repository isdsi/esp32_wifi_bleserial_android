package tk.giesecke.esp32wifible;

import android.app.Service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.polidea.rxandroidble2.internal.util.BleConnectionCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
	private final static String TAG = "ESP32WIFI_BLE";

	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private String mBluetoothDeviceAddress;
	private BluetoothGatt mBluetoothGatt;

	public final static String BLUETOOTH_LE_CCCD           = "00002902-0000-1000-8000-00805f9b34fb";
	public final static String BLUETOOTH_LE_NRF_SERVICE    = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
	public final static String BLUETOOTH_LE_NRF_CHAR_WRITE = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
	public final static String BLUETOOTH_LE_NRF_CHAR_READ  = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

	public final static String ACTION_GATT_CONNECTED =
					"ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED =
					"ACTION_GATT_DISCONNECTED";
	public final static String ACTION_GATT_SERVICES_DISCOVERED =
					"ACTION_GATT_SERVICES_DISCOVERED";
	public final static String ACTION_CHANGED_MTU =
					"ACTION_CHANGED_MTU";
	public final static String ACTION_CHANGED_READ_DESCRIPTOR =
					"ACTION_CHANGED_READ_DESCRIPTOR";
	public final static String ACTION_READ_NRF_CHARACTERISTIC =
					"ACTION_READ_NRF_CHARACTERISTIC";
	public final static String ACTION_WRITE_NRF_CHARACTERISTIC =
					"ACTION_WRITE_NRF_CHARACTERISTIC";
	public final static String EXTRA_DATA =
					"EXTRA_DATA";

	public static final int MAX_MTU = 512; // BLE standard does not limit, some BLE 4.2 devices support 251, various source say that Android has max 512
	public static final int DEFAULT_MTU = 23;

	private final ArrayList<byte[]> writeBuffer = new ArrayList<>();

	private boolean writePending;
	private int payloadSize = DEFAULT_MTU-3;


	// Implements callback methods for GATT events that the app cares about.For example,
	// connection change and services discovered.
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			String intentAction;
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				intentAction = ACTION_GATT_CONNECTED;
				broadcastUpdate2(intentAction, null, status);
				Log.i(TAG, "Connected to GATT server.");
				// Attempts to discover services after successful connection.
				Log.i(TAG, "Attempting to start service discovery:" +
								mBluetoothGatt.discoverServices());

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = ACTION_GATT_DISCONNECTED;
				Log.i(TAG, "Disconnected from GATT server. with status " + Integer.toString(status));
				mBluetoothGatt.close();
				mBluetoothGatt = null;
				broadcastUpdate2(intentAction, null, status);
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate2(ACTION_GATT_SERVICES_DISCOVERED, null, status);

				StringBuilder serviceDiscovery;

				List<BluetoothGattService> gattServices = mBluetoothGatt.getServices();
				Log.e("onServicesDiscovered", "Services count: "+gattServices.size());
				serviceDiscovery = new StringBuilder("Found " + gattServices.size() + " services\n");
				for (BluetoothGattService gattService : gattServices) {
					String uuid = gattService.getUuid().toString();
					Log.e("onServicesDiscovered", "Service uuid "+uuid);
					serviceDiscovery.append("Service uuid ").append(uuid).append("\n");
				}
				// to show displaydata?
				//broadcastUpdate(serviceDiscovery.toString());
			} else {
				Log.w(TAG, "onServicesDiscovered received: " + status);
			}
		}

		@Override
		public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
			Log.d(TAG, "mtu size " + mtu + ", status=" + status);
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.d(TAG, "mtu " + mtu);
				broadcastUpdate2(ACTION_CHANGED_MTU, null, mtu);
			}
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (descriptor.getUuid().equals(UUID.fromString(BLUETOOTH_LE_CCCD))) {
					broadcastUpdate2(ACTION_CHANGED_READ_DESCRIPTOR, descriptor.getValue(), 0);
				}
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
																		 BluetoothGattCharacteristic characteristic,
																		 int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (characteristic.getUuid().equals(UUID.fromString(BLUETOOTH_LE_NRF_CHAR_READ))) {
					broadcastUpdate2(ACTION_READ_NRF_CHARACTERISTIC, characteristic.getValue(), 0);
				}
			}
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (characteristic.getUuid().equals(UUID.fromString(BLUETOOTH_LE_NRF_CHAR_WRITE))) {
					broadcastUpdate2(ACTION_WRITE_NRF_CHARACTERISTIC, characteristic.getValue(), status);
				}
			}
		}
		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			if (characteristic.getUuid().equals(UUID.fromString(BLUETOOTH_LE_NRF_CHAR_READ))) {
				broadcastUpdate2(ACTION_READ_NRF_CHARACTERISTIC, characteristic.getValue(), 0);
			}
		}
	};

	public void broadcastUpdate2(String action, byte[] data, int status) {
		final Intent intent = new Intent(action);
		intent.putExtra("status", status);
		intent.putExtra(EXTRA_DATA, data);
		sendBroadcast(intent);
	}

	public class LocalBinder extends Binder {
		BluetoothLeService getService() {
			return BluetoothLeService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
// After using a given device, you should make sure that BluetoothGatt.close() is called
// such that resources are cleaned up properly.In this particular example, close() is
// invoked when the UI is disconnected from the Service.
		close();
		return super.onUnbind(intent);
	}

	private final IBinder mBinder = new LocalBinder();

	/**
	 * Initializes a reference to the local Bluetooth adapter.
	 *
	 * @return Return true if the initialization is successful.
	 */
	public boolean initialize() {
// For API level 18 and above, get a reference to BluetoothAdapter through
// BluetoothManager.
		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				Log.e(TAG, "Unable to initialize BluetoothManager.");
				return true;
			}
		}

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
			return true;
		}

		return false;
	}

	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 *
	 * @param address The device address of the destination device.
	 *
	 * @return Return true if the connection is initiated successfully. The connection result
	 * is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public boolean connect(final String address) {
		if (mBluetoothAdapter == null || address == null) {
			Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
			return false;
		}

// Previously connected device.Try to reconnect.
		if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
						&& mBluetoothGatt != null) {
			Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
			return mBluetoothGatt.connect();
		}

		final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			Log.w(TAG, "Device not found.Unable to connect.");
			return false;
		}
// We want to directly connect to the device, so we are setting the autoConnect
// parameter to false.
//		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		mBluetoothGatt = (new BleConnectionCompat(this)).connectGatt(device, false, mGattCallback);
		Log.d(TAG, "Trying to create a new connection.");
		mBluetoothDeviceAddress = address;
		return true;
	}

	/**
	 * Disconnects an existing connection or cancel a pending connection. The disconnection result
	 * is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public void disconnect() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		synchronized (writeBuffer) {
			writePending = false;
			writeBuffer.clear();
		}
		mBluetoothGatt.disconnect();
	}

	/**
	 * After using a given BLE device, the app must call this method to ensure resources are
	 * released properly.
	 */
	private void close() {
		if (mBluetoothGatt == null) {
			return;
		}
		mBluetoothGatt.close();
		mBluetoothGatt = null;
	}

	public void setPayloadSize(int s) {
		payloadSize = s;
	}

	public int getPayloadSize() { return payloadSize; }

	// write
	public void write(byte[] data) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		// check if the service is available on the device
		BluetoothGattService mCustomService = mBluetoothGatt
				.getService(UUID.fromString(BLUETOOTH_LE_NRF_SERVICE));
		if (mCustomService == null) {
			Log.w(TAG, "Custom BLE Service not found");
			return;
		}
		// get the read characteristic from the service
		BluetoothGattCharacteristic mWriteCharacteristic = mCustomService
				.getCharacteristic(UUID.fromString(BLUETOOTH_LE_NRF_CHAR_WRITE));

		byte[] data0;
		synchronized (writeBuffer) {
			if(data.length <= payloadSize) {
				data0 = data;
			} else {
				data0 = Arrays.copyOfRange(data, 0, payloadSize);
			}
			if(!writePending && writeBuffer.isEmpty()) {
				writePending = true;
			} else {
				writeBuffer.add(data0);
				Log.d(TAG,"write queued, len="+data0.length);
				data0 = null;
			}
			if(data.length > payloadSize) {
				for(int i=1; i<(data.length+payloadSize-1)/payloadSize; i++) {
					int from = i*payloadSize;
					int to = Math.min(from+payloadSize, data.length);
					writeBuffer.add(Arrays.copyOfRange(data, from, to));
					Log.d(TAG,"write queued, len="+(to-from));
				}
			}
		}
		if(data0 != null) {
			mWriteCharacteristic.setValue(data0);
			if (!mBluetoothGatt.writeCharacteristic(mWriteCharacteristic)) {
				Log.w(TAG, "Failed to write characteristic");
			} else {
				Log.d(TAG,"write started, len="+data0.length);
			}
		}
		// continues asynchronously in onCharacteristicWrite()
	}

	public void writeNext() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		// check if the service is available on the device
		BluetoothGattService mCustomService = mBluetoothGatt
				.getService(UUID.fromString(BLUETOOTH_LE_NRF_SERVICE));
		if (mCustomService == null) {
			Log.w(TAG, "Custom BLE Service not found");
			return;
		}
		// get the read characteristic from the service
		BluetoothGattCharacteristic mWriteCharacteristic = mCustomService
				.getCharacteristic(UUID.fromString(BLUETOOTH_LE_NRF_CHAR_WRITE));

		final byte[] data;
		synchronized (writeBuffer) {
			if (!writeBuffer.isEmpty()) {
				writePending = true;
				data = writeBuffer.remove(0);
			} else {
				writePending = false;
				data = null;
			}
		}
		if(data != null) {
			mWriteCharacteristic.setValue(data);
			if (!mBluetoothGatt.writeCharacteristic(mWriteCharacteristic)) {
				Log.w(TAG, "Failed to write characteristic");
			} else {
				Log.d(TAG,"write started, len="+data.length);
			}
		}
	}

	public boolean changeMtu(int mtu) {
		if (mBluetoothGatt.requestMtu(mtu) == false) {
			Log.e(TAG, "request MTU failed");
			return false;
		}
		return true;
	}

	public boolean changeReadDescriptor()
	{
		BluetoothGattService mCustomService = mBluetoothGatt.
				getService(UUID.fromString(BLUETOOTH_LE_NRF_SERVICE));
		if (mCustomService == null) {
			Log.w(TAG, "Custom BLE Service not found");
			return false;
		}

		if (mCustomService == null)
		{
			Log.w(TAG, "Custom BLE Service not found");
			return false;
		}
		// change readDescripter
		BluetoothGattCharacteristic writeCharacteristic = mCustomService.getCharacteristic(UUID.fromString(BLUETOOTH_LE_NRF_CHAR_WRITE));
		BluetoothGattCharacteristic readCharacteristic = mCustomService.getCharacteristic(UUID.fromString(BLUETOOTH_LE_NRF_CHAR_READ));

		// check writeDescriptor
		int writeProperties = writeCharacteristic.getProperties();
		if ((writeProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE +     // Microbit,HM10-clone have WRITE
				BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) { // HM10,TI uart,Telit have only WRITE_NO_RESPONSE
			Log.e(TAG, "write characteristic not writable");
			return false;
		}

		// set Notification
		if (!mBluetoothGatt.setCharacteristicNotification(readCharacteristic, true)) {
			Log.e(TAG, "no notification for read characteristic");
			return false;
		}

		// check readDescriptor
		BluetoothGattDescriptor readDescriptor = readCharacteristic.getDescriptor(UUID.fromString(BLUETOOTH_LE_CCCD));
		if (readDescriptor == null) {
			Log.e(TAG, "no CCCD descriptor for read characteristic");
			return false;
		}

		// check readDescriptor and enable notification
		int readProperties = readCharacteristic.getProperties();
		if ((readProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
			Log.d(TAG, "enable read indication");
			readDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
		} else if ((readProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
			Log.d(TAG, "enable read notification");
			readDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
		} else {
			Log.e(TAG, "no indication/notification for read characteristic (" + readProperties + ")");
			return false;
		}
		// write read descriptor
		Log.d(TAG, "writing read characteristic descriptor");
		if (!mBluetoothGatt.writeDescriptor(readDescriptor)) {
			Log.e(TAG, "read characteristic CCCD descriptor not writable");
			return false;
		}
		return true;
	}

	public void readNrfCharacteristic() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		// check if the service is available on the device
		BluetoothGattService mCustomService = mBluetoothGatt.
				getService(UUID.fromString(BLUETOOTH_LE_NRF_SERVICE));
		if (mCustomService == null) {
			Log.w(TAG, "Custom BLE Service not found");
			return;
		}
		// get the read characteristic from the service
		BluetoothGattCharacteristic mReadCharacteristic = mCustomService.
				getCharacteristic(UUID.fromString(BLUETOOTH_LE_NRF_CHAR_READ));
		if (!mBluetoothGatt.readCharacteristic(mReadCharacteristic)) {
			Log.w(TAG, "Failed to read characteristic");
			return;
		}
		//Log.i(TAG, mReadCharacteristic.getStringValue(0));
		Log.i(TAG, "Trying to log received value");
	}

	public void writeNrfCharacteristic(byte[] data) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		// check if the service is available on the device
		BluetoothGattService mCustomService = mBluetoothGatt
				.getService(UUID.fromString(BLUETOOTH_LE_NRF_SERVICE));
		if (mCustomService == null) {
			Log.w(TAG, "Custom BLE Service not found");
			return;
		}
		// get the read characteristic from the service
		BluetoothGattCharacteristic mWriteCharacteristic = mCustomService
				.getCharacteristic(UUID.fromString(BLUETOOTH_LE_NRF_CHAR_WRITE));
		mWriteCharacteristic.setValue(data);
		if (!mBluetoothGatt.writeCharacteristic(mWriteCharacteristic)) {
			Log.w(TAG, "Failed to write characteristic");
		}
	}
}
