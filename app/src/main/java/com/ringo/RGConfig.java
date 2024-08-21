package com.ringo;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class RGConfig {
    RGConfig() {

    }

    protected RGConfig(RGConfigType type, String name, int min, int max, String summary) {
        this.type = type;
        this.name = name;
        this.min = min;
        this.max = max;
        this.summary = summary;
    }

    protected RGConfig(RGConfigType type, String name, int min, int max, String summary, String[] options) {
        this.type = type;
        this.name = name;
        this.min = min;
        this.max = max;
        this.summary = summary;

        alOptions = new ArrayList<>();
        for(String o : options) {
            alOptions.add(o);
        }
    }

    protected void Create(Context c) {
        int dp = ConvertDPtoPX(c, 200);
        ll = new LinearLayout(c);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvName = new TextView(c);
        tvName .setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvName.setId(View.generateViewId());
        tvName.setText(name);
        ll.addView(tvName);
        tvSummary = new TextView(c);
        tvSummary .setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvSummary.setId(View.generateViewId());
        tvSummary.setText(summary);
        ll.addView(tvSummary);
    }

    public void valueToView() {

    }

    public void valueFromView() {

    }

    protected int ConvertDPtoPX(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    public void checkType() {

    }

    public void checkArguments() {

    }
    /*
    public JSONObject toJson() {
        JSONObject jo = null;
        try {
            jo = new JSONObject();
            jo.put("name", name);
            jo.put("type", type);
            jo.put("min", min);
            jo.put("max", max);
            jo.put("summary", summary);

            JSONArray ja = new JSONArray();
            for(int i = 0; i < alOptions.size(); i++) {
                ja.put(alOptions.get(i));
            }
            jo.put("options", ja);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jo;
    }
    */
    public void toJson(JSONObject jo) {
        try {
            jo.put("name", name);
            jo.put("type", type);
            jo.put("min", min);
            jo.put("max", max);
            jo.put("summary", summary);

            JSONArray ja = new JSONArray();
            for(int i = 0; i < alOptions.size(); i++) {
                ja.put(alOptions.get(i));
            }
            jo.put("options", ja);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void toJsonArrayValue(JSONArray ja) {

    }

    /*
    public void fromJson(String s) {
        JSONObject jo;
        try {
            jo = new JSONObject(s);
            if (jo.has("name")) {
                this.name = jo.getString("name");
            }
            if (jo.has("type")) {
                this.type = RGConfigType.valueOf(jo.getString("type"));
            }
            if (jo.has("min")) {
                this.min = jo.getInt("min");
            }
            if (jo.has("max")) {
                this.max = jo.getInt("max");
            }
            if (jo.has("summary")) {
                this.summary = jo.getString("summary");
            }
            if (jo.has("options")) {
                String sOptions = jo.getString("options");

                JSONArray ja = new JSONArray(sOptions);
                this.alOptions = new ArrayList<String>();
                for(int i = 0; i < ja.length(); i++) {
                    this.alOptions.add(ja.getString(i));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }
    */
    public void fromJson(JSONObject jo) {
        try {
            if (jo.has("name")) {
                this.name = jo.getString("name");
            }
            if (jo.has("type")) {
                this.type = RGConfigType.valueOf(jo.getString("type"));
            }
            if (jo.has("min")) {
                this.min = jo.getInt("min");
            }
            if (jo.has("max")) {
                this.max = jo.getInt("max");
            }
            if (jo.has("summary")) {
                this.summary = jo.getString("summary");
            }
            if (jo.has("options")) {
                String sOptions = jo.getString("options");

                JSONArray ja = new JSONArray(sOptions);
                this.alOptions = new ArrayList<String>();
                for(int i = 0; i < ja.length(); i++) {
                    this.alOptions.add(ja.getString(i));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Animal* p = &dog; p->Cry() : C++은 Animal::Cry() 호출, java는 Dog::Cry()  호출
    public void fromJsonArrayValue(JSONArray ja) {

    }

    public View getView() {
        return ll;
    }

    public RGConfigType getType() {
        return type;
    }

    protected String name;
    protected int min;
    protected int max;
    protected String summary;
    protected ArrayList<String> alOptions;
    protected RGConfigType type;
    protected LinearLayout ll;
    private TextView tvName;
    private TextView tvSummary;

}
