package tk.giesecke.esp32wifible;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ringo.RGConfig;
import com.ringo.RGConfigType;
import com.ringo.RGConfigInteger;
import com.ringo.RGConfigString;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.CRC32;

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
    private TextView tvFileSystem;
    private TextView tvFileName;
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

    private int ble_timer10ms = 0;

    private int ble_state;
	private int ble_state_timer100ms = 0;
	private int ble_connect_state;
	private int ble_connect_state_timer100ms = 0;
	private String ble_write_string;
	private String ble_read_string;
	private int ble_config_count;
	private int ble_config_index;
	private String ble_file_name;
	private long ble_file_size;
	private long ble_file_crc;
	private final int ble_file_timeout_100ms = 30;

	private FileInputStream fis = null;
	private FileOutputStream fos = null;

	private static final int REQUEST_GET_CONTENT = 1;
	private static final int REQUEST_CREATE_DOCUMENT = 2;

	private String arrFileName[] = null;
	private Long arrFileSize[] = null;

	private CRC32 crc = new CRC32();

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

						ble_connect_state = 100;
                        break;

                    case BluetoothLeService.ACTION_READ_NRF_CHARACTERISTIC:
                        data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                        if (ble_file_size > 0) {
                        	if (fos != null) {
                        		ble_state_timer100ms = ble_file_timeout_100ms; // prevent timeout
                        		long bytes_to_write;
                        		if (ble_file_size - data.length >= 0) {
									bytes_to_write = data.length;
								} else {
									bytes_to_write = ble_file_size;
								}
								try {
                        			Log.d(TAG, "w" + bytes_to_write);
									fos.write(data, 0, (int)bytes_to_write);
									crc.update(data, 0, (int)bytes_to_write); // if you want to delete this, you should know how to get a real path from uri onActivityResult
									ble_file_size -= bytes_to_write;
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						} else {
							if (data != null) {
								ble_read_string = new String(BleSerial_decode(data));
								Log.d(TAG, "rs " + ble_read_string);
								displayData("Received:\n--\n" + data + "\n--\n" + ble_read_string);
							}
						}
                        break;

                    case BluetoothLeService.ACTION_WRITE_NRF_CHARACTERISTIC:
						if (ble_file_size > 0) {
							if (fis != null) {
								ble_state_timer100ms = ble_file_timeout_100ms; // prevent timeout
								int payload = 256;
								long bytes_to_read;
								if (ble_file_size - payload >= 0) {
									bytes_to_read = payload;
								} else {
									bytes_to_read = ble_file_size;
								}
								data = new byte[(int)bytes_to_read];
								try {
									Log.d(TAG, "r" + bytes_to_read);
									fis.read(data, 0, (int)bytes_to_read); // don't need to care remain bytes.
									ble_file_size -= bytes_to_read;
								} catch (IOException e) {
									e.printStackTrace();
								}
								mBluetoothLeService.writeNrfCharacteristicNoResponse(data);
							}
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
        tvFileSystem = findViewById(R.id.tvFileSystem);
        tvFileName = findViewById(R.id.tvFileName);

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
		t.scheduleAtFixedRate(tt, 0, 10);
	}

	public void timer1_Tick() {
		if (ble_timer10ms > 0)
			ble_timer10ms--;
		if (ble_timer10ms == 0) {
			ble_timer10ms = 10;

			// 10ms tick
			if (ble_state_timer100ms > 0)
				ble_state_timer100ms--;
			if (ble_connect_state_timer100ms > 0)
				ble_connect_state_timer100ms--;
		}

        // 100ms tick
        /*
        if (ble_state_timer100ms > 0)
            ble_state_timer100ms--;
		if (ble_connect_state_timer100ms > 0)
			ble_connect_state_timer100ms--;
        */
		JSONObject jo;
		JSONArray ja;
		String s;
		byte[] data0 = new byte[1];

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
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_encode(ble_write_string.getBytes()));
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
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_encode(ble_write_string.getBytes()));
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
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_encode(ble_write_string.getBytes()));
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

			ble_state = 0;
			break;

        case 140:
            jo = new JSONObject();
            try {
                jo.put("read", "filesystem");
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "json stringify failed", Toast.LENGTH_LONG).show();
                ble_state = 0;
                break;
            }
            tvFileSystem.setText("");
            ble_read_string = "";
            ble_write_string = jo.toString();
            Log.d(TAG, "ws " + ble_write_string);
            mBluetoothLeService.writeNrfCharacteristic(BleSerial_encode(ble_write_string.getBytes()));
            ble_state_timer100ms = 10;
            ble_state++;
            break;

        case 141:
            if (ble_state_timer100ms == 0) {
                Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
                ble_state = 0;
                break;
            }
            if (ble_read_string.equals(""))
                break;

            try {

                jo = new JSONObject(ble_read_string);
                s = "";
                if (jo.getString("read").equals("filesystem")) {
                    if (jo.getString("result").equals("ok")) {
                        s = "totalBytes ";
                        s += jo.getString("totalBytes");
                        s += " usedBytes ";
                        s += jo.getString("usedBytes");
						Long freeBytes =  jo.getLong("totalBytes") - jo.getLong("usedBytes");
						s += " freeBytes ";
						s += freeBytes.toString();
                    } else {
                        s = jo.getString("result");
                    }
                } else {
                    s = "json invalid";
                }
                tvFileSystem.setText(s);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "json parse failed", Toast.LENGTH_LONG).show();
                ble_state = 0;
                break;
            }

            ble_state = 0;
            break;

        case 150:
            jo = new JSONObject();
            try {
                jo.put("read", "listDir");
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "json stringify failed", Toast.LENGTH_LONG).show();
                ble_state = 0;
                break;
            }

            ble_read_string = "";
            ble_write_string = jo.toString();
            Log.d(TAG, "ws " + ble_write_string);
            mBluetoothLeService.writeNrfCharacteristic(BleSerial_encode(ble_write_string.getBytes()));
            ble_state_timer100ms = 10;
            ble_state++;
            break;

        case 151:
            if (ble_state_timer100ms == 0) {
                Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
                ble_state = 0;
                break;
            }
            if (ble_read_string.equals(""))
                break;

            try {

                jo = new JSONObject(ble_read_string);
                s = "";
                if (jo.getString("read").equals("listDir")) {
                    if (jo.getString("result").equals("ok")) {
                        ja = jo.getJSONArray("listDirFileName");
						arrFileName = new String[ja.length()];
                        for(int i = 0; i < ja.length(); i++) {
                            s += ja.getString(i) + " ";
							arrFileName[i] = ja.getString(i);
                        }
                        ja = jo.getJSONArray("listDirFileSize");
						arrFileSize = new Long[ja.length()];
						for(int i = 0; i < ja.length(); i++) {
                            s += ja.get(i).toString() + " ";
							arrFileSize[i] = ja.getLong(i);
                        }
                    } else {
                        s = jo.getString("result");
                    }
                } else {
                    s = "json invalid";
                }

            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "json parse failed", Toast.LENGTH_LONG).show();
                ble_state = 0;
                break;
            }

            ble_state = 0;
            break;

        case 160:
            jo = new JSONObject();
            try {
                jo.put("read", "file");
                jo.put("fileName", "/" + ble_file_name);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "json stringify failed", Toast.LENGTH_LONG).show();
                ble_state = 170;
                break;
            }
			ble_file_size = 0;

            ble_read_string = "";
            ble_write_string = jo.toString().replaceAll("\\\\", ""); // "\/test.txt" problems
            Log.d(TAG, "ws " + ble_write_string);
            mBluetoothLeService.writeNrfCharacteristic(BleSerial_encode(ble_write_string.getBytes()));
            ble_state_timer100ms = ble_file_timeout_100ms;
            ble_state++;
            break;

        case 161:
            if (ble_state_timer100ms == 0) {
                Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
                ble_state = 170;
                break;
            }
            if (ble_read_string.equals(""))
                break;

			s = "";
            try {
                jo = new JSONObject(ble_read_string);
                if (jo.getString("read").equals("file")) {
                    if (jo.getString("result").equals("ok")) {
                        ble_file_size = jo.getLong("fileSize");
                        ble_file_crc = jo.getLong("fileCRC");
						crc.reset();
                    } else {
                        s = jo.getString("result");
                    }
                } else {
                    s = "json invalid";
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "json parse failed", Toast.LENGTH_LONG).show();
				ble_state = 170;
                break;
            }
			if (s.equals("") == false) {
				Toast.makeText(this, s, Toast.LENGTH_LONG).show();
				ble_state = 170;
				break;
			}

            ble_state++;
            break;

		case 162:
			if (ble_state_timer100ms == 0) {
				Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
				ble_state = 170;
				break;
			}
			if (ble_file_size > 0)
				break;

			if (crc.getValue() != ble_file_crc) {
				Toast.makeText(this, "crc failed", Toast.LENGTH_LONG).show();
				ble_state++;
				break;
			}

			Toast.makeText(this, "completed", Toast.LENGTH_LONG).show();
			ble_state = 170;
			break;

		case 170: // read file finalize
			try {
				fos.close();
				fos = null;
			} catch(IOException e) {
				e.printStackTrace();
			}
			ble_file_size = 0;
			ble_state = 0;
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
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_encode(ble_write_string.getBytes()));
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

		case 260:
			jo = new JSONObject();
			try {
				jo.put("write", "file");
				jo.put("fileName", "/" + ble_file_name);
				jo.put("fileSize", fis.getChannel().size());
				jo.put("fileCRC", ble_file_crc);
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json stringify failed", Toast.LENGTH_LONG).show();
				ble_state = 270;
				break;
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "file input stream failed", Toast.LENGTH_LONG).show();
				ble_state = 270;
				break;
			}
			ble_file_size = 0;

			ble_read_string = "";
			ble_write_string = jo.toString().replaceAll("\\\\", ""); // "\/test.txt" problems
			Log.d(TAG, "ws " + ble_write_string);
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_encode(ble_write_string.getBytes()));
			ble_state_timer100ms = ble_file_timeout_100ms;
			ble_state++;
			break;

		case 261:
			if (ble_state_timer100ms == 0) {
				Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
				ble_state = 270;
				break;
			}
			if (ble_read_string.equals(""))
				break;

			s = "";
			try {
				jo = new JSONObject(ble_read_string);
				if (jo.getString("write").equals("file")) {
					if (jo.getString("result").equals("ok")) {

					} else {
						s = jo.getString("result");
					}
				} else {
					s = "json invalid";
				}
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json parse failed", Toast.LENGTH_LONG).show();
				ble_state = 270;
				break;
			}
			if (s.equals("") == false) {
				Toast.makeText(this, s, Toast.LENGTH_LONG).show();
				ble_state = 270;
				break;
			}

			try {
				// stream is already opened
				//fis = new FileInputStream(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + ble_file_name);
				ble_file_size = fis.getChannel().size();
				fis.read(data0, 0, 1);
				Log.d(TAG, "r" + 1);
				ble_file_size--;
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				Toast.makeText(this, "file not found", Toast.LENGTH_LONG).show();
				ble_state = 270;
				break;
			} catch(IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "file input stream failed", Toast.LENGTH_LONG).show();
				ble_state = 270;
				break;
			}

			ble_read_string = "";
			ble_state_timer100ms = 1; // give time esp32 to get ready
			ble_state++;
			break;

		case 262:
			if (ble_state_timer100ms != 0)
				break;

			mBluetoothLeService.writeNrfCharacteristicNoResponse(data0); // for triggering ACTION_WRITE_NRF_CHARACTERISTIC
			ble_state_timer100ms = 10;
			ble_state++;
			break;

		case 263:
			if (ble_state_timer100ms == 0) {
				Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
				ble_state = 270;
				break;
			}
			if (ble_file_size > 0)
				break;

			ble_state_timer100ms = 10;
			ble_state++;
			break;

		case 264:
			// cannot know if ble_file_size == 0 because of error during file transferring
			/*
			if (ble_state_timer100ms == 0) {
				Toast.makeText(this, "timeout", Toast.LENGTH_LONG).show();
				ble_state = 270;
				break;
			}
			if (ble_read_string.equals(""))
				break;

			s = "";
			try {
				jo = new JSONObject(ble_read_string);
				if (jo.getString("write").equals("file")) {
					if (jo.getString("result").equals("ok")) {

					} else {
						s = jo.getString("result");
					}
				} else {
					s = "json invalid";
				}
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(this, "json parse failed", Toast.LENGTH_LONG).show();
				ble_state = 270;
				break;
			}
			if (s.equals("") == false) {
				Toast.makeText(this, s, Toast.LENGTH_LONG).show();
				ble_state = 270;
				break;
			}
			ble_read_string = "";
			*/
			Toast.makeText(this, "completed", Toast.LENGTH_LONG).show();
			ble_state = 270;
			break;

		case 270: // write file cancel
			try {
				fis.close();
				fis = null;
			} catch(IOException e) {
				e.printStackTrace();
			}
			ble_file_size = 0;
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
			mBluetoothLeService.writeNrfCharacteristic(BleSerial_encode(ble_write_string.getBytes()));
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

        case 310:
            jo = new JSONObject();
            try {
                jo.put("reset", "");
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "json stringify failed", Toast.LENGTH_LONG).show();
                ble_state = 0;
                break;
            }
            ble_read_string = "";
            ble_write_string = jo.toString();
            Log.d(TAG, "ws " + ble_write_string);
            mBluetoothLeService.writeNrfCharacteristic(BleSerial_encode(ble_write_string.getBytes()));
            ble_state_timer100ms = 10;
            ble_state++;
            break;

		}

		switch(ble_connect_state) {
		case 100:
			ble_state = 110;// read config_count, read config_index
			ble_connect_state_timer100ms = 50;
			ble_connect_state++;
			break;

		case 101:
			if (ble_state != 0)
				break;

			if (ble_connect_state_timer100ms == 0) {
				ble_connect_state = 0;
				break;
			}

			ble_state = 140; // read filesystem
			ble_connect_state_timer100ms = 50;
			ble_connect_state++;
			break;

		case 102:
			if (ble_state != 0)
				break;

			if (ble_connect_state_timer100ms == 0) {
				ble_connect_state = 0;
				break;
			}

			ble_state = 150; // read listDir
			ble_connect_state_timer100ms = 50;
			ble_connect_state++;
			break;

		case 103:
			if (ble_state != 0)
				break;

			if (ble_connect_state_timer100ms == 0) {
				ble_connect_state = 0;
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
		ble_state = 310; // reset
	}

    public void onClickFileList(View v){
		showFileDialog();
    }

    public void onClickFileUpload(View v){
		// ble_state = 260; // write file
		Intent i = new Intent(Intent.ACTION_GET_CONTENT);
		i.setType("*/*");
		startActivityForResult(i, REQUEST_GET_CONTENT);
    }

    public void onClickFileDownload(View v){
        //ble_state = 160; // read file
		Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		i.setType("*/*");
		startActivityForResult(i, REQUEST_CREATE_DOCUMENT);
    }

    private void showFileDialog() {
		AlertDialog.Builder adb = new AlertDialog.Builder(thisActivity);
		adb.setTitle("Select a file");
		adb.setItems(arrFileName, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Toast.makeText(getApplicationContext(), "Result : " + arrFileName[which], Toast.LENGTH_LONG).show();
				tvFileName.setText(arrFileName[which]);
			}
		});
		adb.setNeutralButton("Close", null);
		adb.setPositiveButton("Ok", null);
		adb.setCancelable(false);
		AlertDialog ad = adb.create();
		ad.show();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_GET_CONTENT) {
			// local->remote
			if (data == null)
				return;
			Uri uri = data.getData();
			if (uri == null)
				return;
			String uriInfo = uri.toString();
			Log.d(TAG, "uriInfo " + uriInfo);

			try
			{
				// first read crc value
				FileInputStream fisCRC = (FileInputStream)getApplicationContext().getContentResolver().openInputStream(uri);
				byte[] b = new byte[(int)fisCRC.getChannel().size()];
				fisCRC.read(b);
				crc.reset();
				crc.update(b);
				fisCRC.close();

				fis = (FileInputStream)getApplicationContext().getContentResolver().openInputStream(uri);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				Toast.makeText(getApplicationContext(), "file not found"  + uriInfo.toString(), Toast.LENGTH_LONG).show();
				return;
			} catch (Exception e) {
				e.printStackTrace();
				Toast.makeText(getApplicationContext(), "file open failed " + uriInfo.toString(), Toast.LENGTH_LONG).show();
				return;
			}

			if (tvFileName.getText().toString().equals("")) {
				Toast.makeText(getApplicationContext(), "select a file", Toast.LENGTH_LONG).show();
				return;
			}
			/*
			if (DeviceScanActivity.checkSelfPermission() == false) {
				Toast.makeText(getApplicationContext(), "require permission", Toast.LENGTH_LONG).show();
				return;
			}
			if (checkParamter() == false)
				return;
			*/
			ble_file_name = tvFileName.getText().toString();
			ble_file_crc = crc.getValue();
			ble_state = 260; // write file
		} else if (requestCode == REQUEST_CREATE_DOCUMENT) {
			// remote->local
			if (data == null)
				return;
			Uri uri = data.getData();
			if (uri == null)
				return;

			String uriInfo = uri.toString();
			Log.d(TAG, "uriInfo " + uriInfo);

			try
			{
				fos = (FileOutputStream)getApplicationContext().getContentResolver().openOutputStream(uri);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				Toast.makeText(getApplicationContext(), "file not found"  + uriInfo.toString(), Toast.LENGTH_LONG).show();
				return;
			} catch (Exception e) {
				e.printStackTrace();
				Toast.makeText(getApplicationContext(), "file open failed " + uriInfo.toString(), Toast.LENGTH_LONG).show();
				return;
			}

			if (tvFileName.getText().toString().equals("")) {
				Toast.makeText(getApplicationContext(), "select a file", Toast.LENGTH_LONG).show();
				return;
			}
			/*
			if (DeviceScanActivity.checkSelfPermission() == false) {
				Toast.makeText(getApplicationContext(), "require permission", Toast.LENGTH_LONG).show();
				return;
			}

			if (checkParamter() == false)
				return;
			*/
			ble_file_name = tvFileName.getText().toString();
			ble_state = 160; // read file
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
