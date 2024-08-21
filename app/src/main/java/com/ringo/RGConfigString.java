package com.ringo;


import android.content.Context;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.InputFilter;
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

public class RGConfigString extends RGConfig {
    public RGConfigString() {
        super();
    }

    public RGConfigString(RGConfigType type, String name, String value, int min, int max, String defaultValue, String summary) {
        super(type, name, min, max, summary);
        this.value = value;
        this.defaultValue = defaultValue;
        checkArguments();
    }

    public RGConfigString(RGConfigType type, String name, String value, int min, int max, String defaultValue, String summary, String[] options) {
        super(type, name, min, max, summary, options);
        this.value = value;
        this.defaultValue = defaultValue;
        checkArguments();
    }

    @Override
    public void checkType() {
        // check arguments
        try {
            if (type != RGConfigType.Text) {
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
            if (value.length() < min) {
                throw new IllegalArgumentException("value is smaller than min");
            }
            if (value.length() > max) {
                throw new IllegalArgumentException("value is larger than max");
            }
            if (defaultValue.length() < min) {
                throw new IllegalArgumentException("defaultValue is smaller than min");
            }
            if (defaultValue.length() > max) {
                throw new IllegalArgumentException("defaultValue is larger than max");
            }
        } catch (Exception e) {
            throw e;
        }
    }

    public void Create(Context c) {
        super.Create(c);
        int dp = ConvertDPtoPX(c, 200);
        if (type == RGConfigType.Text) {
            et = new EditText(c);
            et.setLayoutParams(new ViewGroup.LayoutParams(dp, ViewGroup.LayoutParams.WRAP_CONTENT));
            et.setId(View.generateViewId());
            et.setInputType(EditorInfo.TYPE_CLASS_TEXT);

            et.setFilters(new InputFilter[] {
                    new RGInputFilterAlphabet(max),
            });
            et.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (mChangeListener != null) {
                        String newValue = et.getText().toString();
                        mChangeListener.onChangeString(value, newValue);
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
        if (type == RGConfigType.Text) {
            et.setText(value);
        }
    }

    @Override
    public void valueFromView() {
        if (type == RGConfigType.Text) {
            value = et.getText().toString();
        }
    }

    /**
     * Interface definition for a callback to be invoked when a value is changed.
     */
    public interface OnChangeStringListener {
        /**
         * Called when a value has been changed.
         *
         *  The value that was changed.
         */
        void onChangeString(String value, String newValue);
    }

    public void setOnChangeStringListener(@Nullable OnChangeStringListener l) {
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
        ja.put(value);
    }

    /*
    public void fromJson(String s) {
        super.fromJson(s);
        JSONObject jo;
        try {
            jo = new JSONObject(s);
            if (jo.has("value")) {
                this.value = jo.getString("value");
            }
            if (jo.has("defaultValue")) {
                this.defaultValue = jo.getString("defaultValue");
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
        try {
            if (jo.has("value")) {
                this.value = jo.getString("value");
            }
            if (jo.has("defaultValue")) {
                this.defaultValue = jo.getString("defaultValue");
            }
            checkArguments();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void fromJsonArrayValue(JSONArray ja) {
        try {
            String s = ja.getString(0);
            ja.remove(0);
            this.value =  s;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String value;
    private String defaultValue;
    private EditText et;
    private OnChangeStringListener mChangeListener;
}
