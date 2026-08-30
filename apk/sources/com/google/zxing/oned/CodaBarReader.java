package com.google.zxing.oned;

import android.support.v7.widget.ActivityChooserView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.BitArray;
import java.util.Arrays;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public final class CodaBarReader extends OneDReader {
    private static final float MAX_ACCEPTABLE = 2.0f;
    private static final int MIN_CHARACTER_LENGTH = 3;
    private static final float PADDING = 1.5f;
    private static final String ALPHABET_STRING = "0123456789-$:/.+ABCD";
    static final char[] ALPHABET = ALPHABET_STRING.toCharArray();
    static final int[] CHARACTER_ENCODINGS = {3, 6, 9, 96, 18, 66, 33, 36, 48, 72, 12, 24, 69, 81, 84, 21, 26, 41, 11, 14};
    private static final char[] STARTEND_ENCODING = {'A', 'B', 'C', 'D'};
    private final StringBuilder decodeRowResult = new StringBuilder(20);
    private int[] counters = new int[80];
    private int counterLength = 0;

    @Override // com.google.zxing.oned.OneDReader
    public Result decodeRow(int rowNumber, BitArray row, Map<DecodeHintType, ?> hints) throws NotFoundException {
        Arrays.fill(this.counters, 0);
        setCounters(row);
        int startOffset = findStartPattern();
        int nextStart = startOffset;
        this.decodeRowResult.setLength(0);
        do {
            int charOffset = toNarrowWidePattern(nextStart);
            if (charOffset == -1) {
                throw NotFoundException.getNotFoundInstance();
            }
            this.decodeRowResult.append((char) charOffset);
            nextStart += 8;
            if (this.decodeRowResult.length() > 1 && arrayContains(STARTEND_ENCODING, ALPHABET[charOffset])) {
                break;
            }
        } while (nextStart < this.counterLength);
        int trailingWhitespace = this.counters[nextStart - 1];
        int lastPatternSize = 0;
        for (int i = -8; i < -1; i++) {
            lastPatternSize += this.counters[nextStart + i];
        }
        if (nextStart < this.counterLength && trailingWhitespace < lastPatternSize / 2) {
            throw NotFoundException.getNotFoundInstance();
        }
        validatePattern(startOffset);
        for (int i2 = 0; i2 < this.decodeRowResult.length(); i2++) {
            this.decodeRowResult.setCharAt(i2, ALPHABET[this.decodeRowResult.charAt(i2)]);
        }
        char startchar = this.decodeRowResult.charAt(0);
        if (!arrayContains(STARTEND_ENCODING, startchar)) {
            throw NotFoundException.getNotFoundInstance();
        }
        char endchar = this.decodeRowResult.charAt(this.decodeRowResult.length() - 1);
        if (!arrayContains(STARTEND_ENCODING, endchar)) {
            throw NotFoundException.getNotFoundInstance();
        }
        if (this.decodeRowResult.length() <= 3) {
            throw NotFoundException.getNotFoundInstance();
        }
        if (hints == null || !hints.containsKey(DecodeHintType.RETURN_CODABAR_START_END)) {
            this.decodeRowResult.deleteCharAt(this.decodeRowResult.length() - 1);
            this.decodeRowResult.deleteCharAt(0);
        }
        int runningCount = 0;
        for (int i3 = 0; i3 < startOffset; i3++) {
            runningCount += this.counters[i3];
        }
        float left = runningCount;
        for (int i4 = startOffset; i4 < nextStart - 1; i4++) {
            runningCount += this.counters[i4];
        }
        float right = runningCount;
        return new Result(this.decodeRowResult.toString(), null, new ResultPoint[]{new ResultPoint(left, rowNumber), new ResultPoint(right, rowNumber)}, BarcodeFormat.CODABAR);
    }

    /* JADX WARN: Code restructure failed: missing block: B:20:0x00be, code lost:
    
        throw com.google.zxing.NotFoundException.getNotFoundInstance();
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    void validatePattern(int r16) throws com.google.zxing.NotFoundException {
        /*
            Method dump skipped, instruction units count: 228
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.zxing.oned.CodaBarReader.validatePattern(int):void");
    }

    private void setCounters(BitArray row) throws NotFoundException {
        this.counterLength = 0;
        int i = row.getNextUnset(0);
        int end = row.getSize();
        if (i >= end) {
            throw NotFoundException.getNotFoundInstance();
        }
        boolean isWhite = true;
        int count = 0;
        while (i < end) {
            if (row.get(i) ^ isWhite) {
                count++;
            } else {
                counterAppend(count);
                count = 1;
                isWhite = !isWhite;
            }
            i++;
        }
        counterAppend(count);
    }

    private void counterAppend(int e) {
        this.counters[this.counterLength] = e;
        this.counterLength++;
        if (this.counterLength >= this.counters.length) {
            int[] temp = new int[this.counterLength * 2];
            System.arraycopy(this.counters, 0, temp, 0, this.counterLength);
            this.counters = temp;
        }
    }

    private int findStartPattern() throws NotFoundException {
        for (int i = 1; i < this.counterLength; i += 2) {
            int charOffset = toNarrowWidePattern(i);
            if (charOffset != -1 && arrayContains(STARTEND_ENCODING, ALPHABET[charOffset])) {
                int patternSize = 0;
                for (int j = i; j < i + 7; j++) {
                    patternSize += this.counters[j];
                }
                if (i == 1 || this.counters[i - 1] >= patternSize / 2) {
                    return i;
                }
            }
        }
        throw NotFoundException.getNotFoundInstance();
    }

    static boolean arrayContains(char[] array, char key) {
        if (array == null) {
            return false;
        }
        for (char c : array) {
            if (c == key) {
                return true;
            }
        }
        return false;
    }

    private int toNarrowWidePattern(int position) {
        int end = position + 7;
        if (end >= this.counterLength) {
            return -1;
        }
        int[] theCounters = this.counters;
        int maxBar = 0;
        int minBar = ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
        for (int j = position; j < end; j += 2) {
            int currentCounter = theCounters[j];
            if (currentCounter < minBar) {
                minBar = currentCounter;
            }
            if (currentCounter > maxBar) {
                maxBar = currentCounter;
            }
        }
        int thresholdBar = (minBar + maxBar) / 2;
        int maxSpace = 0;
        int minSpace = ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
        for (int j2 = position + 1; j2 < end; j2 += 2) {
            int currentCounter2 = theCounters[j2];
            if (currentCounter2 < minSpace) {
                minSpace = currentCounter2;
            }
            if (currentCounter2 > maxSpace) {
                maxSpace = currentCounter2;
            }
        }
        int thresholdSpace = (minSpace + maxSpace) / 2;
        int bitmask = 128;
        int pattern = 0;
        for (int i = 0; i < 7; i++) {
            int threshold = (i & 1) == 0 ? thresholdBar : thresholdSpace;
            bitmask >>= 1;
            if (theCounters[position + i] > threshold) {
                pattern |= bitmask;
            }
        }
        for (int i2 = 0; i2 < CHARACTER_ENCODINGS.length; i2++) {
            if (CHARACTER_ENCODINGS[i2] == pattern) {
                return i2;
            }
        }
        return -1;
    }
}
