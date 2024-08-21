package com.ringo;

import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RGInputFilterAlphabet implements InputFilter {
    Pattern mPattern;

    public RGInputFilterAlphabet(int max) {
        String p = "^[|a-z|A-Z|"+ // English
        //"ㄱ-ㅎ|ㅏ-ㅣ|가-힣|" + // Korean
        "0-9|" + // Number
        "\\x20|"+ // Space
        "!|" +
        "\\x22|" + // "
        "#|$|%|&|'|(|)|*|+|" + // Special
        ",|" + // ,
        "\\x2d|" + // -
        ".|/|" +
        ":|;|<|=|>|?|@|" +
        "\\x5b|" + // [
        "\\x5c|" + // \
        "\\x5d|" + // ]
        "\\x5e|" + // ^
        "_|`|" +
        "\\x7b|" + // {
        "\\x7c|" + // |
        "\\x7d|" + // }
        "\\x7e" + // ~
        "]{1," + Integer.toString(max) + "}$";

        mPattern = Pattern.compile(p);
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
        Matcher m = mPattern.matcher(source);
        if (m.matches())
            return source;
        return "";
    }
}