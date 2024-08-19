package tk.giesecke.esp32wifible;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.ringo.RGConfig;
import com.ringo.RGConfigType;
import com.ringo.RGConfigInteger;
import com.ringo.RGConfigString;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import static tk.giesecke.esp32wifible.DeviceScanActivity.EXTRAS_DEVICE;
import static tk.giesecke.esp32wifible.DeviceScanActivity.EXTRAS_DEVICE_ADDRESS;
import static tk.giesecke.esp32wifible.DeviceScanActivity.EXTRAS_DEVICE_NAME;
import static tk.giesecke.esp32wifible.XorCoding.xorCode;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlBLESerialActivity extends Activity {
	private final static String TAG = "ESP32WIFI_BLE_CTRL";

	private TextView mDataField;
	private String mDeviceAddress;
	private BluetoothLeService mBluetoothLeService;
	private boolean mConnected = false;
	private BluetoothDevice mmDevice;

	private Menu thisMenu;

	private Boolean firstView = true;

	private ArrayList<RGConfig> alConfig = new ArrayList<>();

	private LinearLayout llConfig;

	private final Handler mainLooper = new Handler(Looper.getMainLooper());

	private DeviceControlBLESerialActivity thisActivity;
	private int ble_state;
	private int ble_state_timer100ms = 0;
	private String ble_write_string;
	private String ble_read_string;
	private int ble_config_count;
	private int ble_config_index;

	// Code to manage Service lifecycle.
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
			if (mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
// Automatically connects to the device upon successful start-up initialization.
			mBluetoothLeService.connect(mDeviceAddress);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
            byte[] data;
			if (action != null) {
				switch (action) {
					case BluetoothLeService.ACTION_GATT_CONNECTED:
						mConnected = true;
						invalidateOptionsMenu();
						break;
					case BluetoothLeService.ACTION_GATT_DISCONNECTED:
						Bundle extras = intent.getExtras();
						int result = 0;
						if (extras != null) {
							result = extras.getInt("status");
						}
						if (result == 133) { // connection failed!!!!
							Log.e(TAG, "Server connection failed");
							Toast.makeText(getApplicationContext()
											, "Server connection failed\nRetry to connect again\nOr try to reset the ESP32"
											, Toast.LENGTH_LONG).show();
						}
						mConnected = false;
						invalidateOptionsMenu();
						clearUI();
						break;
					case BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED:
						Log.d(TAG, "Discovery finished");
                        if (mBluetoothLeService.changeMtu(64) == false) {
                            Log.e(TAG, "changeMtu Failed");
                        }
						break;

                    case BluetoothLeService.ACTION_CHANGED_MTU:
                        Log.d(TAG, "Changed Mtu");
                        int mtu = intent.getIntExtra("status", BluetoothLeService.DEFAULT_MTU);
                        mBluetoothLeService.setPayloadSize(mtu - 3);

                        if (mBluetoothLeService.changeReadDescriptor() == false) {
                            Log.e(TAG, "changeReadDescriptor Failed");
                        }
                        break;

                    case BluetoothLeService.ACTION_CHANGED_READ_DESCRIPTOR:
                        Log.d(TAG, "Changed ReadDescriptor");

						ble_state = 100; // read config_count, read config_index
                        break;

                    case BluetoothLeService.ACTION_READ_NRF_CHARACTERISTIC:
                        data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
						if (data != null) {
							ble_read_string = new String(BleSerial_decode(data));
							Log.d(TAG, "rs " + ble_read_string);
							displayData("Received:\n--\n" + data + "\n--\n" + ble_read_string);
						}
                        break;

                    case BluetoothLeService.ACTION_WRITE_NRF_CHARACTERISTIC:
                        // write -> writeNext -> writeNext ... -> writeNext ( data will be empty. )
                        if (mBluetoothLeService != null) {
                            mBluetoothLeService.writeNext();
                        }
                        break;
                }
			}
		}
	};

	public RGConfig RGConfigFromJson(JSONObject jo) {
		RGConfig rgc;
		rgc = RGConfigIntegerFromJson(jo);
		if (rgc != null)
			return rgc;
		rgc = RGConfigStringFromJson(jo);
		if (rgc != null)
			return rgc;

		return null;
	}

	public RGConfigInteger RGConfigIntegerFromJson(JSONObject jo) {
		RGConfigInteger rgc = null;
		try {
			rgc = new RGConfigInteger();
			rgc.fromJson(jo);
			if (rgc.getType() == RGConfigType.Switch) {
				rgc.Create(this);
			}
			if (rgc.getType() == RGConfigType.SeekBar) {
				rgc.Create(this);
			}
			if (rgc.getType() == RGConfigType.Spinner) {
				rgc.Create(this);
			}
			if (rgc.getType() == RGConfigType.Number) {
				rgc.Create(this);
			}
		} catch(Exception ex) {
			ex.printStackTrace();
			rgc = null;
		}
		return rgc;
	}

	public RGConfigString RGConfigStringFromJson(JSONObject jo) {
		RGConfigString rgc = null;
		try {
			rgc = new RGConfigString();
			rgc.fromJson(jo);
			if (rgc.getType() == RGConfigType.Text) {
				rgc.Create(this);
			}
		} catch(Exception ex) {
			ex.printStackTrace();
			rgc = null;
		}
		return rgc;
	}

	private void clearUI() {
		mDataField.setText(R.string.no_data);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.device_control);

		final Intent intent = getIntent();
		String mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
		mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
		mmDevice = intent.getParcelableExtra(EXTRAS_DEVICE);

		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

		android.app.ActionBar thisActionBar = getActionBar();
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			getWindow().setStatusBarColor(getResources().getColor(R.color.colorSecondaryDark));
		}
		if (android.os.Build.VERSION.SDK_INT >= 18) {
			Drawable actionBarDrawable  = new ColorDrawable(getResources().getColor(R.color.colorSecondary));
			if (thisActionBar != null) {
				thisActionBar.setBackgroundDrawable(actionBarDrawable);
			}
		}

		// Sets up UI references.
		mDataField = findViewById(R.id.data_value);

		//noinspection ConstantConditions
		getActionBar().setTitle(mDeviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

		llConfig = findViewById(R.id.llConfig);

		thisActivity = this;

		Timer t = new Timer();
		TimerTask tt = new TimerTask() {
			@Override
			public void run() {
				mainLooper.post(new Runnable() {
					@Override
					public void run() {
						thisActivity.timer1_Tick();
					}
				});
			}
		};
		t.scheduleAtFixedRate(tt, 0, 100);
	}

	public void timer1_Tick() {
		if (ble_state_timer100ms > 0)
			ble_state_timer100ms--;
		if (ble_state_timer100ms == 0) {
			ble_state_timer100ms = 10;
		}
		JSONObject jo;
		JSONArray ja;

		switch(ble_state) {
		case 0:
			break;

		case 100:
			ble_state = 110;
			break;

		case 110:
			alConfig.clear();
			llConfig.removeAllViews();
			jo = new JSONObject();
			try {
				jo.put("read", "config_count");
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json stringify failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			ble_read_string = "";
			ble_write_string = jo.toString();
			Log.d(TAG, "ws " + ble_write_string);
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_decode(ble_write_string.getBytes()));
			ble_state_timer100ms = 10;
			ble_state++;
			break;

		case 111:
			if (ble_state_timer100ms == 0) {
				Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			if (ble_read_string.equals(""))
				break;

			try {
				ble_config_count = 0;
				jo = new JSONObject(ble_read_string);
				jo.getString("read");
				ble_config_count = jo.getInt("config_count");
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json parse failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			ble_config_index = 0;
			ble_state = 120;
			break;

		case 120:
			jo = new JSONObject();
			try {
				jo.put("read", "config_index");
				jo.put("config_index", ble_config_index);
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json stringify failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			ble_read_string = "";
			ble_write_string = jo.toString();
			Log.d(TAG, "ws " + ble_write_string);
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_decode(ble_write_string.getBytes()));
			ble_state_timer100ms = 10;
			ble_state++;
			break;

		case 121:
			if (ble_state_timer100ms == 0) {
				Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			if (ble_read_string.equals(""))
				break;

			try {
				jo = new JSONObject(ble_read_string);
				RGConfig rgc = RGConfigFromJson(jo);
				alConfig.add(rgc);
				View v = rgc.getView();
				llConfig.addView(v);
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json parse failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			} catch (Exception e) {
				e.printStackTrace();
				Toast.makeText(this, "exception failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			ble_state++;
			break;

		case 122:
			ble_config_index++;
			if (ble_config_index >= ble_config_count) {
				ble_state = 0;
				break;
			}
			ble_state = 120;
			break;

		case 130:
			jo = new JSONObject();
			try {
				jo.put("read", "value");
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json stringify failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			ble_read_string = "";
			ble_write_string = jo.toString();
			Log.d(TAG, "ws " + ble_write_string);
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_decode(ble_write_string.getBytes()));
			ble_state_timer100ms = 10;
			ble_state++;
			break;

		case 131:
			if (ble_state_timer100ms == 0) {
				Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			if (ble_read_string.equals(""))
				break;

			try {
				ble_config_count = 0;
				jo = new JSONObject(ble_read_string);
				if (jo.getString("read").equals("value")) {
					ja = jo.getJSONArray("value");
					for(int i = 0; i < alConfig.size(); i++) {
						RGConfig rgc = alConfig.get(i);
						rgc.fromJsonArrayValue(ja);
						rgc.valueToView();
					}
				}
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json parse failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			ble_config_index = 0;
			ble_state++;
			break;

		case 230:
			jo = new JSONObject();
			try {
				jo.put("write", "value");
				ja = new JSONArray();
				for(int i = 0; i < alConfig.size(); i++) {
					RGConfig rgc = alConfig.get(i);
					rgc.valueFromView();
					rgc.toJsonArrayValue(ja);
				}
				jo.put("value", ja);
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json stringify failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			ble_read_string = "";
			ble_write_string = jo.toString();
			Log.d(TAG, "ws " + ble_write_string);
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_decode(ble_write_string.getBytes()));
			ble_state_timer100ms = 10;
			ble_state++;
			break;

		case 231:
			if (ble_state_timer100ms == 0) {
				Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			if (ble_read_string.equals(""))
				break;

			try {
				jo = new JSONObject(ble_read_string);
				if (jo.getString("write").equals("value") == false) {
					throw new JSONException("");
				}
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json parse failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			ble_state = 0;
			break;

		case 300:
			jo = new JSONObject();
			try {
				jo.put("erase", "");
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json stringify failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			ble_read_string = "";
			ble_write_string = jo.toString();
			Log.d(TAG, "ws " + ble_write_string);
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_decode(ble_write_string.getBytes()));
			ble_state_timer100ms = 10;
			ble_state++;
			break;

		case 301:
			if (ble_state_timer100ms == 0) {
				Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			if (ble_read_string.equals(""))
				break;

			try {
				jo = new JSONObject(ble_read_string);
				if (jo.has("erase") == false) {
					throw new JSONException("");
				}
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json parse failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			ble_state = 0;
			break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		if (mBluetoothLeService != null) {
			final boolean result = mBluetoothLeService.connect(mDeviceAddress);
			Log.d(TAG, "Connect request result=" + result);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.control, menu);
		if (mConnected) {
			menu.findItem(R.id.menu_connect).setVisible(false);
			menu.findItem(R.id.menu_disconnect).setVisible(true);
		} else {
			menu.findItem(R.id.menu_connect).setVisible(true);
			if (firstView) {
				menu.findItem(R.id.menu_connect).setActionView(R.layout.progress_bar);
				firstView = false;
			} else {
				menu.findItem(R.id.menu_connect).setActionView(null);
			}
			menu.findItem(R.id.menu_disconnect).setVisible(false);
		}
		thisMenu = menu;
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case R.id.menu_connect:
				thisMenu.findItem(R.id.menu_connect).setActionView(R.layout.progress_bar);
				mBluetoothLeService.connect(mDeviceAddress);
				return true;
			case R.id.menu_disconnect:
				mBluetoothLeService.disconnect();
				thisMenu.findItem(R.id.menu_connect).setActionView(null);
				return true;
			case android.R.id.home:
				thisMenu.findItem(R.id.menu_connect).setActionView(null);
				firstView = false;
				onBackPressed();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void displayData(String data) {
		if (data != null) {
			mDataField.setText(data);
		}
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_CHANGED_MTU);
		intentFilter.addAction(BluetoothLeService.ACTION_CHANGED_READ_DESCRIPTOR);
		intentFilter.addAction(BluetoothLeService.ACTION_READ_NRF_CHARACTERISTIC);
		intentFilter.addAction(BluetoothLeService.ACTION_WRITE_NRF_CHARACTERISTIC);
		return intentFilter;
	}

	@SuppressWarnings("unused")
	public void onClickWrite(View v){
		ble_state = 230; // write value
	}

	@SuppressWarnings("unused")
	public void onClickRead(View v){
		ble_state = 130; // read value
	}

	@SuppressWarnings("unused")
	public void onClickErase(View v){
		ble_state = 300; // erase
	}

	@SuppressWarnings("unused")
	public void onClickReset(View v){
		ble_state = 230; // write value
	}

	private byte[] BleSerial_decode(byte[] value)
	{
		byte[] decodedData = xorCode(mmDevice.getName(), value, value.length);
		return decodedData;
	}

	private byte[] BleSerial_encode(byte[] value)
	{
		byte[] encodedData = xorCode(mmDevice.getName(), value, value.length);
		return encodedData;
	}
}
