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

	private String ssidPrimString = "";
	private String pwPrimString = "";
	private String ssidSecString = "";
	private String pwSecString = "";

	private Boolean doubleApEnabled = false;

	private EditText ssidPrimET;
	private EditText pwPrimET;
	private EditText ssidSecET;
	private EditText pwSecET;

	private Menu thisMenu;

	private Boolean firstView = true;

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

						if (mBluetoothLeService != null) {
							mBluetoothLeService.readNrfCharacteristic();
						}
                        break;

                    case BluetoothLeService.ACTION_READ_NRF_CHARACTERISTIC:
                        data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                        if (data != null) {
                        }
						if (data != null) {
							// Decode the data
							//byte[] decodedData = xorCode(mmDevice.getName(),data,data.length);
							//String finalData = new String(decodedData);

							//displayData("Received:\n--\n" + data + "\n--\n" + finalData);

							ble_read_string = new String(BleSerial_decode(data));
							Log.d(TAG, "rs " + ble_read_string);
							displayData("Received:\n--\n" + data + "\n--\n" + ble_read_string);
							// Get stored WiFi credentials from the received data
							/*
							JSONObject receivedConfigJSON;
							try {
								receivedConfigJSON = new JSONObject(finalData);
								boolean write = false;
								boolean enumerate = false;
								if (receivedConfigJSON.has("write")) {
									write = receivedConfigJSON.getBoolean("write");
								}
								if (receivedConfigJSON.has("enumerate")) {
									enumerate = receivedConfigJSON.getBoolean("enumerate");
								}
								if (write == false) {
									if (enumerate) {
										llConfig.removeAllViews();
										JSONArray jaConfig = receivedConfigJSON.getJSONArray("configs");
										String s = jaConfig.getString(0);
										RGConfig rgc = RGConfigFromJson(s);
										View v = rgc.getView();
										llConfig.addView(v);
									} else {
										if (receivedConfigJSON.has("ssidPrim")) {
											ssidPrimString = receivedConfigJSON.getString("ssidPrim");
											ssidPrimET.setText(ssidPrimString);
										}
										if (receivedConfigJSON.has("pwPrim")) {
											pwPrimString = receivedConfigJSON.getString("pwPrim");
											pwPrimET.setText(pwPrimString);
										}
										if (receivedConfigJSON.has("ssidSec")) {
											ssidSecString = receivedConfigJSON.getString("ssidSec");
											ssidSecET.setText(ssidSecString);
										}
										if (receivedConfigJSON.has("pwSec")) {
											pwSecString = receivedConfigJSON.getString("pwSec");
											pwSecET.setText(pwSecString);
										}
									}
								}
							} catch (JSONException e) {
								e.printStackTrace();
							}
							*/
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

	public RGConfig RGConfigFromJson(String s) {
		RGConfig rgc;
		rgc = RGConfigIntegerFromJson(s);
		if (rgc != null)
			return rgc;
		rgc = RGConfigStringFromJson(s);
		if (rgc != null)
			return rgc;

		return null;
	}

	public RGConfigInteger RGConfigIntegerFromJson(String s) {
		RGConfigInteger rgc = null;
		try {
			rgc = new RGConfigInteger();
			rgc.fromJson(s);
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

	public RGConfigString RGConfigStringFromJson(String s) {
		RGConfigString rgc = null;
		try {
			rgc = new RGConfigString();
			rgc.fromJson(s);
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
		ssidPrimET = findViewById(R.id.ssidPrim);
		pwPrimET = findViewById(R.id.pwPrim);
		ssidSecET = findViewById(R.id.ssidSec);
		pwSecET = findViewById(R.id.pwSec);

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

		switch(ble_state) {
		case 0:
			break;

		case 100:
			llConfig.removeAllViews();
			jo = new JSONObject();
			try {
				jo.put("read", "config_count"); // phone <- esp32
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json parse failed", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			ble_read_string = "";
			ble_write_string = jo.toString();
			Log.d(TAG, "ws " + ble_write_string);
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_decode(ble_write_string.getBytes()));
			ble_state_timer100ms = 10;
			ble_state++;

		case 101:
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
			ble_state++;
			break;

		case 102:
			jo = new JSONObject();
			try {
				jo.put("read", "config_index");
				jo.put("config_index", ble_config_index);
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json create failed", Toast.LENGTH_LONG).show();
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

		case 103:
			if (ble_state_timer100ms == 0) {
				Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
				ble_state = 0;
				break;
			}
			if (ble_read_string.equals(""))
				break;

			try {
				jo = new JSONObject(ble_read_string);
				RGConfig rgc = RGConfigFromJson(ble_read_string);
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

		case 104:
			ble_config_index++;
			if (ble_config_index >= ble_config_count) {
				ble_state = 0;
				break;
			}
			ble_state = 102;
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
		ble_state = 200;
		/*
		if(mBluetoothLeService != null) {

			// Update credentials with last edit text values
			ssidPrimString = ssidPrimET.getText().toString();
			pwPrimString = pwPrimET.getText().toString();
			ssidSecString = ssidSecET.getText().toString();
			pwSecString = pwSecET.getText().toString();

			// Create JSON object
			JSONObject wifiCreds = new JSONObject();
			try {
				wifiCreds.put("write", true); // phone -> esp32
				if (ssidPrimString.equals("")) {
					Toast.makeText(getApplicationContext()
									, "Missing primary SSID entry"
									, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else {
					wifiCreds.put("ssidPrim", ssidPrimString);
				}
				if (pwPrimString.equals("")) {
					Toast.makeText(getApplicationContext()
									, "Missing primary password entry"
									, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else {
					wifiCreds.put("pwPrim", pwPrimString);
				}
				if (ssidSecString.equals("") && doubleApEnabled) {
					Toast.makeText(getApplicationContext()
									, "Missing secondary SSID entry"
									, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else if (ssidSecString.equals("") && !doubleApEnabled) {
					wifiCreds.put("ssidSec", ssidPrimString);
				} else {
					wifiCreds.put("ssidSec", ssidSecString);
				}
				if (pwSecString.equals("") && doubleApEnabled) {
					Toast.makeText(getApplicationContext()
									, "Missing secondary password entry"
									, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else if (pwSecString.equals("") && !doubleApEnabled) {
					wifiCreds.put("pwSec", pwPrimString);
				} else {
					wifiCreds.put("pwSec", pwSecString);
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
			byte[] decodedData = xorCode(mmDevice.getName()
							,wifiCreds.toString().getBytes()
							,wifiCreds.toString().length());
			mBluetoothLeService.writeNrfCharacteristic(decodedData);
			displayData(getResources().getString(R.string.update_config));
		}
		*/
	}

	@SuppressWarnings("unused")
	public void onClickRead(View v){
		ble_state = 100;

		/*
		if(mBluetoothLeService != null) {

			// Update credentials with last edit text values
			ssidPrimString = ssidPrimET.getText().toString();
			pwPrimString = pwPrimET.getText().toString();
			ssidSecString = ssidSecET.getText().toString();
			pwSecString = pwSecET.getText().toString();

			// Create JSON object
			JSONObject wifiCreds = new JSONObject();
			try {
				wifiCreds.put("write", false); // phone <- esp32
			} catch (JSONException e) {
				e.printStackTrace();
			}
			byte[] decodedData = xorCode(mmDevice.getName()
					, wifiCreds.toString().getBytes()
					, wifiCreds.toString().length());
			mBluetoothLeService.writeNrfCharacteristic(decodedData);
			displayData(getResources().getString(R.string.get_config));
		}
		*/
	}

	@SuppressWarnings("unused")
	public void onClickErase(View v){
		thisMenu.findItem(R.id.menu_connect).setActionView(R.layout.progress_bar);
		if(mBluetoothLeService != null) {
			// Create JSON object
			JSONObject wifiCreds = new JSONObject();
			try {
				wifiCreds.put("write", true); // phone -> esp32
				wifiCreds.put("erase", true);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			byte[] decodedData = xorCode(mmDevice.getName()
							,wifiCreds.toString().getBytes()
							,wifiCreds.toString().length());
			mBluetoothLeService.writeNrfCharacteristic(decodedData);
			displayData(getResources().getString(R.string.erase_config));
		}
	}

	@SuppressWarnings("unused")
	public void onClickReset(View v){
		thisMenu.findItem(R.id.menu_connect).setActionView(R.layout.progress_bar);
		if(mBluetoothLeService != null) {
			// Create JSON object
			JSONObject wifiCreds = new JSONObject();
			try {
				wifiCreds.put("write", true);
				wifiCreds.put("reset", true);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			byte[] decodedData = xorCode(mmDevice.getName()
							,wifiCreds.toString().getBytes()
							,wifiCreds.toString().length());
			mBluetoothLeService.writeNrfCharacteristic(decodedData);
			displayData(getResources().getString(R.string.reset_device));
		}
	}

	@SuppressWarnings("unused")
	public void onClickSwitch(View v){
		TextView chgHdr;
		EditText chgEt;
		Switch enaDoubleAP = findViewById(R.id.apNumSelector);
		if (enaDoubleAP.isChecked()) {
			doubleApEnabled = true;
			chgHdr = findViewById(R.id.ssidSecHdr);
			chgHdr.setVisibility(View.VISIBLE);
			chgEt = findViewById(R.id.ssidSec);
			chgEt.setVisibility(View.VISIBLE);
			chgHdr = findViewById(R.id.pwSecHdr);
			chgHdr.setVisibility(View.VISIBLE);
			chgEt = findViewById(R.id.pwSec);
			chgEt.setVisibility(View.VISIBLE);
		} else {
			doubleApEnabled = false;
			chgHdr = findViewById(R.id.ssidSecHdr);
			chgHdr.setVisibility(View.INVISIBLE);
			chgEt = findViewById(R.id.ssidSec);
			chgEt.setVisibility(View.INVISIBLE);
			chgHdr = findViewById(R.id.pwSecHdr);
			chgHdr.setVisibility(View.INVISIBLE);
			chgEt = findViewById(R.id.pwSec);
			chgEt.setVisibility(View.INVISIBLE);
		}
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
