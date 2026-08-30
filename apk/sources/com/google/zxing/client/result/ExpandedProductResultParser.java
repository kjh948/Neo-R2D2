package com.google.zxing.client.result;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import java.util.HashMap;
import java.util.Map;
import org.java_websocket.drafts.Draft_75;

/* JADX INFO: loaded from: classes.dex */
public final class ExpandedProductResultParser extends ResultParser {
    /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
    @Override // com.google.zxing.client.result.ResultParser
    public ExpandedProductParsedResult parse(Result result) {
        BarcodeFormat format = result.getBarcodeFormat();
        if (format != BarcodeFormat.RSS_EXPANDED) {
            return null;
        }
        String rawText = getMassagedText(result);
        String productID = null;
        String sscc = null;
        String lotNumber = null;
        String productionDate = null;
        String packagingDate = null;
        String bestBeforeDate = null;
        String expirationDate = null;
        String weight = null;
        String weightType = null;
        String weightIncrement = null;
        String price = null;
        String priceIncrement = null;
        String priceCurrency = null;
        Map<String, String> uncommonAIs = new HashMap<>();
        int i = 0;
        while (i < rawText.length()) {
            String ai = findAIvalue(i, rawText);
            if (ai == null) {
                return null;
            }
            int i2 = i + ai.length() + 2;
            String value = findValue(i2, rawText);
            i = i2 + value.length();
            byte b = -1;
            switch (ai.hashCode()) {
                case 1536:
                    if (ai.equals("00")) {
                        b = 0;
                    }
                    break;
                case 1537:
                    if (ai.equals("01")) {
                        b = 1;
                    }
                    break;
                case 1567:
                    if (ai.equals("10")) {
                        b = 2;
                    }
                    break;
                case 1568:
                    if (ai.equals("11")) {
                        b = 3;
                    }
                    break;
                case 1570:
                    if (ai.equals("13")) {
                        b = 4;
                    }
                    break;
                case 1572:
                    if (ai.equals("15")) {
                        b = 5;
                    }
                    break;
                case 1574:
                    if (ai.equals("17")) {
                        b = 6;
                    }
                    break;
                case 1567966:
                    if (ai.equals("3100")) {
                        b = 7;
                    }
                    break;
                case 1567967:
                    if (ai.equals("3101")) {
                        b = 8;
                    }
                    break;
                case 1567968:
                    if (ai.equals("3102")) {
                        b = 9;
                    }
                    break;
                case 1567969:
                    if (ai.equals("3103")) {
                        b = 10;
                    }
                    break;
                case 1567970:
                    if (ai.equals("3104")) {
                        b = 11;
                    }
                    break;
                case 1567971:
                    if (ai.equals("3105")) {
                        b = 12;
                    }
                    break;
                case 1567972:
                    if (ai.equals("3106")) {
                        b = Draft_75.CR;
                    }
                    break;
                case 1567973:
                    if (ai.equals("3107")) {
                        b = 14;
                    }
                    break;
                case 1567974:
                    if (ai.equals("3108")) {
                        b = 15;
                    }
                    break;
                case 1567975:
                    if (ai.equals("3109")) {
                        b = 16;
                    }
                    break;
                case 1568927:
                    if (ai.equals("3200")) {
                        b = 17;
                    }
                    break;
                case 1568928:
                    if (ai.equals("3201")) {
                        b = 18;
                    }
                    break;
                case 1568929:
                    if (ai.equals("3202")) {
                        b = 19;
                    }
                    break;
                case 1568930:
                    if (ai.equals("3203")) {
                        b = 20;
                    }
                    break;
                case 1568931:
                    if (ai.equals("3204")) {
                        b = 21;
                    }
                    break;
                case 1568932:
                    if (ai.equals("3205")) {
                        b = 22;
                    }
                    break;
                case 1568933:
                    if (ai.equals("3206")) {
                        b = 23;
                    }
                    break;
                case 1568934:
                    if (ai.equals("3207")) {
                        b = 24;
                    }
                    break;
                case 1568935:
                    if (ai.equals("3208")) {
                        b = 25;
                    }
                    break;
                case 1568936:
                    if (ai.equals("3209")) {
                        b = 26;
                    }
                    break;
                case 1575716:
                    if (ai.equals("3920")) {
                        b = 27;
                    }
                    break;
                case 1575717:
                    if (ai.equals("3921")) {
                        b = 28;
                    }
                    break;
                case 1575718:
                    if (ai.equals("3922")) {
                        b = 29;
                    }
                    break;
                case 1575719:
                    if (ai.equals("3923")) {
                        b = 30;
                    }
                    break;
                case 1575747:
                    if (ai.equals("3930")) {
                        b = 31;
                    }
                    break;
                case 1575748:
                    if (ai.equals("3931")) {
                        b = 32;
                    }
                    break;
                case 1575749:
                    if (ai.equals("3932")) {
                        b = 33;
                    }
                    break;
                case 1575750:
                    if (ai.equals("3933")) {
                        b = 34;
                    }
                    break;
            }
            switch (b) {
                case 0:
                    sscc = value;
                    break;
                case 1:
                    productID = value;
                    break;
                case 2:
                    lotNumber = value;
                    break;
                case 3:
                    productionDate = value;
                    break;
                case 4:
                    packagingDate = value;
                    break;
                case 5:
                    bestBeforeDate = value;
                    break;
                case 6:
                    expirationDate = value;
                    break;
                case 7:
                case 8:
                case 9:
                case 10:
                case 11:
                case 12:
                case 13:
                case 14:
                case 15:
                case 16:
                    weight = value;
                    weightType = ExpandedProductParsedResult.KILOGRAM;
                    weightIncrement = ai.substring(3);
                    break;
                case 17:
                case 18:
                case 19:
                case 20:
                case 21:
                case 22:
                case 23:
                case 24:
                case 25:
                case 26:
                    weight = value;
                    weightType = ExpandedProductParsedResult.POUND;
                    weightIncrement = ai.substring(3);
                    break;
                case 27:
                case 28:
                case 29:
                case 30:
                    price = value;
                    priceIncrement = ai.substring(3);
                    break;
                case 31:
                case 32:
                case 33:
                case 34:
                    if (value.length() < 4) {
                        return null;
                    }
                    price = value.substring(3);
                    priceCurrency = value.substring(0, 3);
                    priceIncrement = ai.substring(3);
                    break;
                    break;
                default:
                    uncommonAIs.put(ai, value);
                    break;
            }
        }
        return new ExpandedProductParsedResult(rawText, productID, sscc, lotNumber, productionDate, packagingDate, bestBeforeDate, expirationDate, weight, weightType, weightIncrement, price, priceIncrement, priceCurrency, uncommonAIs);
    }

    private static String findAIvalue(int i, String rawText) {
        char c = rawText.charAt(i);
        if (c != '(') {
            return null;
        }
        CharSequence rawTextAux = rawText.substring(i + 1);
        StringBuilder buf = new StringBuilder();
        for (int index = 0; index < rawTextAux.length(); index++) {
            char currentChar = rawTextAux.charAt(index);
            if (currentChar == ')') {
                return buf.toString();
            }
            if (currentChar < '0' || currentChar > '9') {
                return null;
            }
            buf.append(currentChar);
        }
        return buf.toString();
    }

    private static String findValue(int i, String rawText) {
        StringBuilder buf = new StringBuilder();
        String rawTextAux = rawText.substring(i);
        for (int index = 0; index < rawTextAux.length(); index++) {
            char c = rawTextAux.charAt(index);
            if (c == '(') {
                if (findAIvalue(index, rawTextAux) != null) {
                    break;
                }
                buf.append('(');
            } else {
                buf.append(c);
            }
        }
        return buf.toString();
    }
}
