package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.ByteBufferList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/* JADX INFO: loaded from: classes.dex */
class HeaderReader {
    Inflater inflater = new Inflater() { // from class: com.koushikdutta.async.http.spdy.HeaderReader.1
        @Override // java.util.zip.Inflater
        public int inflate(byte[] buffer, int offset, int count) throws DataFormatException {
            int result = super.inflate(buffer, offset, count);
            if (result == 0 && needsDictionary()) {
                setDictionary(Spdy3.DICTIONARY);
                return super.inflate(buffer, offset, count);
            }
            return result;
        }
    };

    public List<Header> readHeader(ByteBufferList bb, int length) throws IOException {
        byte[] bytes = new byte[length];
        bb.get(bytes);
        this.inflater.setInput(bytes);
        ByteBufferList source = new ByteBufferList().order(ByteOrder.BIG_ENDIAN);
        while (!this.inflater.needsInput()) {
            ByteBuffer b = ByteBufferList.obtain(8192);
            try {
                int read = this.inflater.inflate(b.array());
                b.limit(read);
                source.add(b);
            } catch (DataFormatException e) {
                throw new IOException(e);
            }
        }
        int numberOfPairs = source.getInt();
        List<Header> entries = new ArrayList<>(numberOfPairs);
        for (int i = 0; i < numberOfPairs; i++) {
            ByteString name = readByteString(source).toAsciiLowercase();
            ByteString values = readByteString(source);
            if (name.size() == 0) {
                throw new IOException("name.size == 0");
            }
            entries.add(new Header(name, values));
        }
        return entries;
    }

    private static ByteString readByteString(ByteBufferList source) {
        int length = source.getInt();
        return ByteString.of(source.getBytes(length));
    }
}
