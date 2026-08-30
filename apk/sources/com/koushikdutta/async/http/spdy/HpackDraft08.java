package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.spdy.BitArray;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.java_websocket.drafts.Draft_75;

/* JADX INFO: loaded from: classes.dex */
final class HpackDraft08 {
    private static final int PREFIX_4_BITS = 15;
    private static final int PREFIX_6_BITS = 63;
    private static final int PREFIX_7_BITS = 127;
    private static final Header[] STATIC_HEADER_TABLE = {new Header(Header.TARGET_AUTHORITY, ""), new Header(Header.TARGET_METHOD, AsyncHttpGet.METHOD), new Header(Header.TARGET_METHOD, AsyncHttpPost.METHOD), new Header(Header.TARGET_PATH, "/"), new Header(Header.TARGET_PATH, "/index.html"), new Header(Header.TARGET_SCHEME, "http"), new Header(Header.TARGET_SCHEME, "https"), new Header(Header.RESPONSE_STATUS, "200"), new Header(Header.RESPONSE_STATUS, "204"), new Header(Header.RESPONSE_STATUS, "206"), new Header(Header.RESPONSE_STATUS, "304"), new Header(Header.RESPONSE_STATUS, "400"), new Header(Header.RESPONSE_STATUS, "404"), new Header(Header.RESPONSE_STATUS, "500"), new Header("accept-charset", ""), new Header("accept-encoding", "gzip, deflate"), new Header("accept-language", ""), new Header("accept-ranges", ""), new Header("accept", ""), new Header("access-control-allow-origin", ""), new Header("age", ""), new Header("allow", ""), new Header("authorization", ""), new Header("cache-control", ""), new Header("content-disposition", ""), new Header("content-encoding", ""), new Header("content-language", ""), new Header("content-length", ""), new Header("content-location", ""), new Header("content-range", ""), new Header("content-type", ""), new Header("cookie", ""), new Header("date", ""), new Header("etag", ""), new Header("expect", ""), new Header("expires", ""), new Header("from", ""), new Header("host", ""), new Header("if-match", ""), new Header("if-modified-since", ""), new Header("if-none-match", ""), new Header("if-range", ""), new Header("if-unmodified-since", ""), new Header("last-modified", ""), new Header("link", ""), new Header("location", ""), new Header("max-forwards", ""), new Header("proxy-authenticate", ""), new Header("proxy-authorization", ""), new Header("range", ""), new Header("referer", ""), new Header("refresh", ""), new Header("retry-after", ""), new Header("server", ""), new Header("set-cookie", ""), new Header("strict-transport-security", ""), new Header("transfer-encoding", ""), new Header("user-agent", ""), new Header("vary", ""), new Header("via", ""), new Header("www-authenticate", "")};
    private static final Map<ByteString, Integer> NAME_TO_FIRST_INDEX = nameToFirstIndex();

    private HpackDraft08() {
    }

    static final class Reader {
        private int maxHeaderTableByteCount;
        private int maxHeaderTableByteCountSetting;
        private final List<Header> emittedHeaders = new ArrayList();
        private final ByteBufferList source = new ByteBufferList();
        Header[] headerTable = new Header[8];
        int nextHeaderIndex = this.headerTable.length - 1;
        int headerCount = 0;
        BitArray referencedHeaders = new BitArray.FixedCapacity();
        BitArray emittedReferencedHeaders = new BitArray.FixedCapacity();
        int headerTableByteCount = 0;

        Reader(int maxHeaderTableByteCountSetting) {
            this.maxHeaderTableByteCountSetting = maxHeaderTableByteCountSetting;
            this.maxHeaderTableByteCount = maxHeaderTableByteCountSetting;
        }

        public void refill(ByteBufferList bb) {
            bb.get(this.source);
        }

        int maxHeaderTableByteCount() {
            return this.maxHeaderTableByteCount;
        }

        void maxHeaderTableByteCountSetting(int newMaxHeaderTableByteCountSetting) {
            this.maxHeaderTableByteCountSetting = newMaxHeaderTableByteCountSetting;
            this.maxHeaderTableByteCount = this.maxHeaderTableByteCountSetting;
            adjustHeaderTableByteCount();
        }

        private void adjustHeaderTableByteCount() {
            if (this.maxHeaderTableByteCount < this.headerTableByteCount) {
                if (this.maxHeaderTableByteCount == 0) {
                    clearHeaderTable();
                } else {
                    evictToRecoverBytes(this.headerTableByteCount - this.maxHeaderTableByteCount);
                }
            }
        }

        private void clearHeaderTable() {
            clearReferenceSet();
            Arrays.fill(this.headerTable, (Object) null);
            this.nextHeaderIndex = this.headerTable.length - 1;
            this.headerCount = 0;
            this.headerTableByteCount = 0;
        }

        private int evictToRecoverBytes(int bytesToRecover) {
            int entriesToEvict = 0;
            if (bytesToRecover > 0) {
                for (int j = this.headerTable.length - 1; j >= this.nextHeaderIndex && bytesToRecover > 0; j--) {
                    bytesToRecover -= this.headerTable[j].hpackSize;
                    this.headerTableByteCount -= this.headerTable[j].hpackSize;
                    this.headerCount--;
                    entriesToEvict++;
                }
                this.referencedHeaders.shiftLeft(entriesToEvict);
                this.emittedReferencedHeaders.shiftLeft(entriesToEvict);
                System.arraycopy(this.headerTable, this.nextHeaderIndex + 1, this.headerTable, this.nextHeaderIndex + 1 + entriesToEvict, this.headerCount);
                this.nextHeaderIndex += entriesToEvict;
            }
            return entriesToEvict;
        }

        void readHeaders() throws IOException {
            while (this.source.hasRemaining()) {
                int b = this.source.get() & Draft_75.END_OF_FRAME;
                if (b == 128) {
                    throw new IOException("index == 0");
                }
                if ((b & 128) == 128) {
                    int index = readInt(b, 127);
                    readIndexedHeader(index - 1);
                } else if (b == 64) {
                    readLiteralHeaderWithIncrementalIndexingNewName();
                } else if ((b & 64) == 64) {
                    int index2 = readInt(b, 63);
                    readLiteralHeaderWithIncrementalIndexingIndexedName(index2 - 1);
                } else if ((b & 32) == 32) {
                    if ((b & 16) == 16) {
                        if ((b & 15) != 0) {
                            throw new IOException("Invalid header table state change " + b);
                        }
                        clearReferenceSet();
                    } else {
                        this.maxHeaderTableByteCount = readInt(b, 15);
                        if (this.maxHeaderTableByteCount < 0 || this.maxHeaderTableByteCount > this.maxHeaderTableByteCountSetting) {
                            throw new IOException("Invalid header table byte count " + this.maxHeaderTableByteCount);
                        }
                        adjustHeaderTableByteCount();
                    }
                } else if (b == 16 || b == 0) {
                    readLiteralHeaderWithoutIndexingNewName();
                } else {
                    int index3 = readInt(b, 15);
                    readLiteralHeaderWithoutIndexingIndexedName(index3 - 1);
                }
            }
        }

        private void clearReferenceSet() {
            this.referencedHeaders.clear();
            this.emittedReferencedHeaders.clear();
        }

        void emitReferenceSet() {
            for (int i = this.headerTable.length - 1; i != this.nextHeaderIndex; i--) {
                if (this.referencedHeaders.get(i) && !this.emittedReferencedHeaders.get(i)) {
                    this.emittedHeaders.add(this.headerTable[i]);
                }
            }
        }

        List<Header> getAndReset() {
            List<Header> result = new ArrayList<>(this.emittedHeaders);
            this.emittedHeaders.clear();
            this.emittedReferencedHeaders.clear();
            return result;
        }

        private void readIndexedHeader(int index) throws IOException {
            if (isStaticHeader(index)) {
                int index2 = index - this.headerCount;
                if (index2 <= HpackDraft08.STATIC_HEADER_TABLE.length - 1) {
                    Header staticEntry = HpackDraft08.STATIC_HEADER_TABLE[index2];
                    if (this.maxHeaderTableByteCount == 0) {
                        this.emittedHeaders.add(staticEntry);
                        return;
                    } else {
                        insertIntoHeaderTable(-1, staticEntry);
                        return;
                    }
                }
                throw new IOException("Header index too large " + (index2 + 1));
            }
            int headerTableIndex = headerTableIndex(index);
            if (!this.referencedHeaders.get(headerTableIndex)) {
                this.emittedHeaders.add(this.headerTable[headerTableIndex]);
                this.emittedReferencedHeaders.set(headerTableIndex);
            }
            this.referencedHeaders.toggle(headerTableIndex);
        }

        private int headerTableIndex(int index) {
            return this.nextHeaderIndex + 1 + index;
        }

        private void readLiteralHeaderWithoutIndexingIndexedName(int index) throws IOException {
            ByteString name = getName(index);
            ByteString value = readByteString();
            this.emittedHeaders.add(new Header(name, value));
        }

        private void readLiteralHeaderWithoutIndexingNewName() throws IOException {
            ByteString name = HpackDraft08.checkLowercase(readByteString());
            ByteString value = readByteString();
            this.emittedHeaders.add(new Header(name, value));
        }

        private void readLiteralHeaderWithIncrementalIndexingIndexedName(int nameIndex) throws IOException {
            ByteString name = getName(nameIndex);
            ByteString value = readByteString();
            insertIntoHeaderTable(-1, new Header(name, value));
        }

        private void readLiteralHeaderWithIncrementalIndexingNewName() throws IOException {
            ByteString name = HpackDraft08.checkLowercase(readByteString());
            ByteString value = readByteString();
            insertIntoHeaderTable(-1, new Header(name, value));
        }

        private ByteString getName(int index) {
            return isStaticHeader(index) ? HpackDraft08.STATIC_HEADER_TABLE[index - this.headerCount].name : this.headerTable[headerTableIndex(index)].name;
        }

        private boolean isStaticHeader(int index) {
            return index >= this.headerCount;
        }

        private void insertIntoHeaderTable(int index, Header entry) {
            int delta = entry.hpackSize;
            if (index != -1) {
                delta -= this.headerTable[headerTableIndex(index)].hpackSize;
            }
            if (delta > this.maxHeaderTableByteCount) {
                clearHeaderTable();
                this.emittedHeaders.add(entry);
                return;
            }
            int bytesToRecover = (this.headerTableByteCount + delta) - this.maxHeaderTableByteCount;
            int entriesEvicted = evictToRecoverBytes(bytesToRecover);
            if (index == -1) {
                if (this.headerCount + 1 > this.headerTable.length) {
                    Header[] doubled = new Header[this.headerTable.length * 2];
                    System.arraycopy(this.headerTable, 0, doubled, this.headerTable.length, this.headerTable.length);
                    if (doubled.length == 64) {
                        this.referencedHeaders = ((BitArray.FixedCapacity) this.referencedHeaders).toVariableCapacity();
                        this.emittedReferencedHeaders = ((BitArray.FixedCapacity) this.emittedReferencedHeaders).toVariableCapacity();
                    }
                    this.referencedHeaders.shiftLeft(this.headerTable.length);
                    this.emittedReferencedHeaders.shiftLeft(this.headerTable.length);
                    this.nextHeaderIndex = this.headerTable.length - 1;
                    this.headerTable = doubled;
                }
                int index2 = this.nextHeaderIndex;
                this.nextHeaderIndex = index2 - 1;
                this.referencedHeaders.set(index2);
                this.headerTable[index2] = entry;
                this.headerCount++;
            } else {
                int index3 = index + headerTableIndex(index) + entriesEvicted;
                this.referencedHeaders.set(index3);
                this.headerTable[index3] = entry;
            }
            this.headerTableByteCount += delta;
        }

        private int readByte() throws IOException {
            return this.source.get() & Draft_75.END_OF_FRAME;
        }

        int readInt(int firstByte, int prefixMask) throws IOException {
            int prefix = firstByte & prefixMask;
            if (prefix >= prefixMask) {
                int result = prefixMask;
                int shift = 0;
                while (true) {
                    int b = readByte();
                    if ((b & 128) != 0) {
                        result += (b & 127) << shift;
                        shift += 7;
                    } else {
                        return result + (b << shift);
                    }
                }
            } else {
                return prefix;
            }
        }

        ByteString readByteString() throws IOException {
            int firstByte = readByte();
            boolean huffmanDecode = (firstByte & 128) == 128;
            int length = readInt(firstByte, 127);
            if (huffmanDecode) {
                return ByteString.of(Huffman.get().decode(this.source.getBytes(length)));
            }
            return ByteString.of(this.source.getBytes(length));
        }
    }

    private static Map<ByteString, Integer> nameToFirstIndex() {
        Map<ByteString, Integer> result = new LinkedHashMap<>(STATIC_HEADER_TABLE.length);
        for (int i = 0; i < STATIC_HEADER_TABLE.length; i++) {
            if (!result.containsKey(STATIC_HEADER_TABLE[i].name)) {
                result.put(STATIC_HEADER_TABLE[i].name, Integer.valueOf(i));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static final class Writer {
        Writer() {
        }

        ByteBufferList writeHeaders(List<Header> headerBlock) throws IOException {
            ByteBufferList out = new ByteBufferList();
            ByteBuffer current = ByteBufferList.obtain(8192);
            int size = headerBlock.size();
            for (int i = 0; i < size; i++) {
                if (current.remaining() < current.capacity() / 2) {
                    current.flip();
                    out.add(current);
                    current = ByteBufferList.obtain(current.capacity() * 2);
                }
                ByteString name = headerBlock.get(i).name.toAsciiLowercase();
                Integer staticIndex = (Integer) HpackDraft08.NAME_TO_FIRST_INDEX.get(name);
                if (staticIndex != null) {
                    writeInt(current, staticIndex.intValue() + 1, 15, 0);
                    writeByteString(current, headerBlock.get(i).value);
                } else {
                    current.put((byte) 0);
                    writeByteString(current, name);
                    writeByteString(current, headerBlock.get(i).value);
                }
            }
            out.add(current);
            return out;
        }

        void writeInt(ByteBuffer out, int value, int prefixMask, int bits) throws IOException {
            if (value < prefixMask) {
                out.put((byte) (bits | value));
                return;
            }
            out.put((byte) (bits | prefixMask));
            int value2 = value - prefixMask;
            while (value2 >= 128) {
                int b = value2 & 127;
                out.put((byte) (b | 128));
                value2 >>>= 7;
            }
            out.put((byte) value2);
        }

        void writeByteString(ByteBuffer out, ByteString data) throws IOException {
            writeInt(out, data.size(), 127, 0);
            out.put(data.toByteArray());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static ByteString checkLowercase(ByteString name) throws IOException {
        int length = name.size();
        for (int i = 0; i < length; i++) {
            byte c = name.getByte(i);
            if (c >= 65 && c <= 90) {
                throw new IOException("PROTOCOL_ERROR response malformed: mixed case name: " + name.utf8());
            }
        }
        return name;
    }
}
