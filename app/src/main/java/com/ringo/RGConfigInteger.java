package com.ringo;


import android.content.Context;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import static android.view.inputmethod.EditorInfo.IME_FLAG_FORCE_ASCII;

public class RGConfigInteger extends RGConfig {
    public RGConfigInteger() {
        super();
    }

    public RGConfigInteger(RGConfigType type, String name, int value, int min, int max, int defaultValue, String summary) {
        super(type, name, min, max, summary);
        this.value = value;
        this.defaultValue = defaultValue;
        checkArguments();
    }

    public RGConfigInteger(RGConfigType type, String name, int value, int min, int max, int defaultValue, String summary, String[] options) {
        super(type, name, min, max, summary, options);
        this.value = value;
        this.defaultValue = defaultValue;
        checkArguments();
    }

    @Override
    public void checkType() {
        try {
            if (type != RGConfigType.Switch &&
                    type != RGConfigType.SeekBar &&
                    type != RGConfigType.Spinner &&
                    type != RGConfigType.Number) {
                throw new IllegalArgumentException("type is invalid");
            }
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public void checkArguments() {
        // check arguments
        try {
            if (value < min) {
                throw new IllegalArgumentException("value is smaller than min");
            }
            if (value > max) {
                throw new IllegalArgumentException("value is larger than max");
            }
            if (defaultValue < min) {
                throw new IllegalArgumentException("defaultValue is smaller than min");
            }
            if (defaultValue > max) {
                throw new IllegalArgumentException("defaultValue is larger than max");
            }
            if (type == RGConfigType.Spinner) {
                if (alOptions.size() < max) {
                    throw new IllegalArgumentException("options is smaller than max");
                }
            }
        } catch (Exception e) {
            throw e;
        }
    }

    public void Create(Context c) {
        super.Create(c);
        int dp = ConvertDPtoPX(c, 200);
        if (type == RGConfigType.Switch) {
            sw = new Switch(c);
            sw.setLayoutParams(new ViewGroup.LayoutParams(dp, ViewGroup.LayoutParams.WRAP_CONTENT));
            sw.setId(View.generateViewId());
            // 값 변경 이벤트 처리
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (mChangeListener != null) {
                        int newValue = sw.isChecked() ? 1 : 0;
                        mChangeListener.onChangeInteger(value, newValue);
                        value = newValue;
                    }
                }
            });
            ll.addView(sw);
        } else if (type == RGConfigType.SeekBar) {
            sb = new SeekBar(c);
            sb.setLayoutParams(new ViewGroup.LayoutParams(dp, ViewGroup.LayoutParams.WRAP_CONTENT));
            sb.setId(View.generateViewId());
            sb.setMax(ValueToPosition(max));
            // 값 변경 이벤트 처리
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (mChangeListener != null) {
                        int newValue = PositionToValue(sb.getProgress());
                        mChangeListener.onChangeInteger(value, newValue);
                        value = newValue;
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
            ll.addView(sb);
        } else if (type == RGConfigType.Spinner) {
            sp = new Spinner(c);
            sp.setLayoutParams(new ViewGroup.LayoutParams(dp, ViewGroup.LayoutParams.WRAP_CONTENT));
            sp.setId(View.generateViewId());
            Spinner spinner = new Spinner(c);
            ArrayAdapter<String> aa = new ArrayAdapter<String>(c,  android.R.layout.simple_spinner_dropdown_item, alOptions);
            sp.setAdapter(aa);
            // 값 변경 이벤트 처리
            sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (mChangeListener != null) {
                        int newValue = PositionToValue(sp.getSelectedItemPosition());
                        mChangeListener.onChangeInteger(value, newValue);
                        value = newValue;
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
            ll.addView(sp);
        } else if (type == RGConfigType.Number) {
            et = new EditText(c);
            et.setLayoutParams(new ViewGroup.LayoutParams(dp, ViewGroup.LayoutParams.WRAP_CONTENT));
            et.setId(View.generateViewId());
            et.setInputType(EditorInfo.TYPE_CLASS_NUMBER);
            et.setFilters(new RGInputFilterMinMax[] {
                new RGInputFilterMinMax(Integer.toString(min), Integer.toString(max))
            });
            et.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (mChangeListener != null) {
                        int newValue = Integer.parseInt(et.getText().toString());
                        mChangeListener.onChangeInteger(value, newValue);
                        value = newValue;
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });
            ll.addView(et);
        }
        valueToView();
    }

    @Override
    public void valueToView() {
        if (type == RGConfigType.Switch) {
            sw.setChecked(value != 0 ? true : false);
        }
        else if (type == RGConfigType.SeekBar) {
            sb.setProgress(ValueToPosition(value));
        }
        else if (type == RGConfigType.Spinner) {
            sp.setSelection(ValueToPosition(value));
        }
        else if (type == RGConfigType.Number) {
            et.setText(Integer.toString(value));
        }
    }

    @Override
    public void valueFromView() {
        if (type == RGConfigType.Switch) {
            value = sw.isChecked() ? 1 : 0;
        }
        else if (type == RGConfigType.SeekBar) {
            value = PositionToValue(sb.getProgress());
        }
        else if (type == RGConfigType.Spinner) {
            value = PositionToValue(sp.getSelectedItemPosition());
        }
        else if (type == RGConfigType.Number) {
            value = Integer.parseInt(et.getText().toString());
        }
    }

    private int PositionToValue(int position) {
        return position + min;
    }

    private int ValueToPosition(int value) {
        return value - min;
    }

    /**
     * Interface definition for a callback to be invoked when a value is changed.
     */
    public interface OnChangeIntegerListener {
        /**
         * Called when a value has been changed.
         *
         *  The value that was changed.
         */
        void onChangeInteger(int value, int newValue);
    }

    public void setOnChangeIntegerListener(@Nullable OnChangeIntegerListener l) {
        View v = null;
        mChangeListener = l;
    }
    /*
    public JSONObject toJson() {
        JSONObject jo = super.toJson();
        try {
            jo.put("value", value);
            jo.put("defaultValue", defaultValue);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jo;
    }
    */

    @Override
    public void toJson(JSONObject jo) {
        super.toJson(jo);
        try {
            jo.put("value", value);
            jo.put("defaultValue", defaultValue);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void toJsonArrayValue(JSONArray ja) {
        ja.put(Integer.toString(value));
    }

    /*
    public void fromJson(String s) {
        super.fromJson(s);
        checkType();
        JSONObject jo;
        try {
            jo = new JSONObject(s);
            if (jo.has("value")) {
                this.value = jo.getInt("value");
            }
            if (jo.has("defaultValue")) {
                this.defaultValue = jo.getInt("defaultValue");
            }
            checkArguments();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    */

    @Override
    public void fromJson(JSONObject jo) {
        super.fromJson(jo);
        checkType();
        try {
            if (jo.has("value")) {
                this.value = jo.getInt("value");
            }
            if (jo.has("defaultValue")) {
                this.defaultValue = jo.getInt("defaultValue");
            }
            checkArguments();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void fromJsonArrayValue(JSONArray ja) {
        super.fromJsonArrayValue(ja);
        try {
            String s = ja.getString(0);
            ja.remove(0);
            this.value =  Integer.parseInt(s);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private int value;
    private int defaultValue;
    private String summary;
    private Switch sw;
    private SeekBar sb;
    private Spinner sp;
    private EditText et;
    private OnChangeIntegerListener mChangeListener;
}
