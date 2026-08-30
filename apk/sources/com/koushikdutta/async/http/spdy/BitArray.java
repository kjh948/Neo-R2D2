package com.koushikdutta.async.http.spdy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
interface BitArray {
    void clear();

    boolean get(int i);

    void set(int i);

    void shiftLeft(int i);

    void toggle(int i);

    public static final class FixedCapacity implements BitArray {
        long data = 0;

        @Override // com.koushikdutta.async.http.spdy.BitArray
        public void clear() {
            this.data = 0L;
        }

        @Override // com.koushikdutta.async.http.spdy.BitArray
        public void set(int index) {
            this.data |= 1 << checkInput(index);
        }

        @Override // com.koushikdutta.async.http.spdy.BitArray
        public void toggle(int index) {
            this.data ^= 1 << checkInput(index);
        }

        @Override // com.koushikdutta.async.http.spdy.BitArray
        public boolean get(int index) {
            return ((this.data >> checkInput(index)) & 1) == 1;
        }

        @Override // com.koushikdutta.async.http.spdy.BitArray
        public void shiftLeft(int count) {
            this.data <<= checkInput(count);
        }

        public String toString() {
            return Long.toBinaryString(this.data);
        }

        public BitArray toVariableCapacity() {
            return new VariableCapacity(this);
        }

        private static int checkInput(int index) {
            if (index < 0 || index > 63) {
                throw new IllegalArgumentException(String.format(Locale.ENGLISH, "input must be between 0 and 63: %s", Integer.valueOf(index)));
            }
            return index;
        }
    }

    public static final class VariableCapacity implements BitArray {
        long[] data;
        private int start;

        public VariableCapacity() {
            this.data = new long[1];
        }

        private VariableCapacity(FixedCapacity small) {
            this.data = new long[]{small.data, 0};
        }

        private void growToSize(int size) {
            long[] newData = new long[size];
            if (this.data != null) {
                System.arraycopy(this.data, 0, newData, 0, this.data.length);
            }
            this.data = newData;
        }

        private int offsetOf(int index) {
            int offset = (index + this.start) / 64;
            if (offset > this.data.length - 1) {
                growToSize(offset + 1);
            }
            return offset;
        }

        private int shiftOf(int index) {
            return (this.start + index) % 64;
        }

        @Override // com.koushikdutta.async.http.spdy.BitArray
        public void clear() {
            Arrays.fill(this.data, 0L);
        }

        @Override // com.koushikdutta.async.http.spdy.BitArray
        public void set(int index) {
            checkInput(index);
            int offset = offsetOf(index);
            long[] jArr = this.data;
            jArr[offset] = jArr[offset] | (1 << shiftOf(index));
        }

        @Override // com.koushikdutta.async.http.spdy.BitArray
        public void toggle(int index) {
            checkInput(index);
            int offset = offsetOf(index);
            long[] jArr = this.data;
            jArr[offset] = jArr[offset] ^ (1 << shiftOf(index));
        }

        @Override // com.koushikdutta.async.http.spdy.BitArray
        public boolean get(int index) {
            checkInput(index);
            int offset = offsetOf(index);
            return (this.data[offset] & (1 << shiftOf(index))) != 0;
        }

        @Override // com.koushikdutta.async.http.spdy.BitArray
        public void shiftLeft(int count) {
            this.start -= checkInput(count);
            if (this.start < 0) {
                int arrayShift = (this.start / (-64)) + 1;
                long[] newData = new long[this.data.length + arrayShift];
                System.arraycopy(this.data, 0, newData, arrayShift, this.data.length);
                this.data = newData;
                this.start = (this.start % 64) + 64;
            }
        }

        public String toString() {
            StringBuilder builder = new StringBuilder("{");
            List<Integer> ints = toIntegerList();
            int count = ints.size();
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(ints.get(i));
            }
            return builder.append('}').toString();
        }

        List<Integer> toIntegerList() {
            List<Integer> ints = new ArrayList<>();
            int count = (this.data.length * 64) - this.start;
            for (int i = 0; i < count; i++) {
                if (get(i)) {
                    ints.add(Integer.valueOf(i));
                }
            }
            return ints;
        }

        private static int checkInput(int index) {
            if (index < 0) {
                throw new IllegalArgumentException(String.format(Locale.ENGLISH, "input must be a positive number: %s", Integer.valueOf(index)));
            }
            return index;
        }
    }
}
