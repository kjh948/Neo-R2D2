package com.google.zxing.client.result;

/* JADX INFO: loaded from: classes.dex */
abstract class AbstractDoCoMoResultParser extends ResultParser {
    AbstractDoCoMoResultParser() {
    }

    static String[] matchDoCoMoPrefixedField(String prefix, String rawText, boolean trim) {
        return matchPrefixedField(prefix, rawText, ';', trim);
    }

    static String matchSingleDoCoMoPrefixedField(String prefix, String rawText, boolean trim) {
        return matchSinglePrefixedField(prefix, rawText, ';', trim);
    }
}
