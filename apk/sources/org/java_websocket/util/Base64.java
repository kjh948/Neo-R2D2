package org.java_websocket.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.zip.GZIPOutputStream;
import org.java_websocket.drafts.Draft_75;

/* JADX INFO: loaded from: classes.dex */
public class Base64 {
    static final /* synthetic */ boolean $assertionsDisabled;
    public static final int DECODE = 0;
    public static final int DONT_GUNZIP = 4;
    public static final int DO_BREAK_LINES = 8;
    public static final int ENCODE = 1;
    private static final byte EQUALS_SIGN = 61;
    private static final byte EQUALS_SIGN_ENC = -1;
    public static final int GZIP = 2;
    private static final int MAX_LINE_LENGTH = 76;
    private static final byte NEW_LINE = 10;
    public static final int NO_OPTIONS = 0;
    public static final int ORDERED = 32;
    private static final String PREFERRED_ENCODING = "US-ASCII";
    public static final int URL_SAFE = 16;
    private static final byte WHITE_SPACE_ENC = -5;
    private static final byte[] _ORDERED_ALPHABET;
    private static final byte[] _ORDERED_DECODABET;
    private static final byte[] _STANDARD_ALPHABET;
    private static final byte[] _STANDARD_DECODABET;
    private static final byte[] _URL_SAFE_ALPHABET;
    private static final byte[] _URL_SAFE_DECODABET;

    static {
        $assertionsDisabled = !Base64.class.desiredAssertionStatus();
        _STANDARD_ALPHABET = new byte[]{65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 43, 47};
        _STANDARD_DECODABET = new byte[]{-9, -9, -9, -9, -9, -9, -9, -9, -9, WHITE_SPACE_ENC, WHITE_SPACE_ENC, -9, -9, WHITE_SPACE_ENC, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, WHITE_SPACE_ENC, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, 62, -9, -9, -9, 63, 52, 53, 54, 55, 56, 57, 58, 59, 60, EQUALS_SIGN, -9, -9, -9, -1, -9, -9, -9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, Draft_75.CR, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -9, -9, -9, -9, -9, -9, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9};
        _URL_SAFE_ALPHABET = new byte[]{65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 45, 95};
        _URL_SAFE_DECODABET = new byte[]{-9, -9, -9, -9, -9, -9, -9, -9, -9, WHITE_SPACE_ENC, WHITE_SPACE_ENC, -9, -9, WHITE_SPACE_ENC, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, WHITE_SPACE_ENC, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, 62, -9, -9, 52, 53, 54, 55, 56, 57, 58, 59, 60, EQUALS_SIGN, -9, -9, -9, -1, -9, -9, -9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, Draft_75.CR, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -9, -9, -9, -9, 63, -9, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9};
        _ORDERED_ALPHABET = new byte[]{45, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 95, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122};
        _ORDERED_DECODABET = new byte[]{-9, -9, -9, -9, -9, -9, -9, -9, -9, WHITE_SPACE_ENC, WHITE_SPACE_ENC, -9, -9, WHITE_SPACE_ENC, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, WHITE_SPACE_ENC, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, 0, -9, -9, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, -9, -9, -9, -1, -9, -9, -9, 11, 12, Draft_75.CR, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, -9, -9, -9, -9, 37, -9, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, EQUALS_SIGN, 62, 63, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9};
    }

    private static final byte[] getAlphabet(int options) {
        if ((options & 16) == 16) {
            return _URL_SAFE_ALPHABET;
        }
        if ((options & 32) == 32) {
            return _ORDERED_ALPHABET;
        }
        return _STANDARD_ALPHABET;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final byte[] getDecodabet(int options) {
        if ((options & 16) == 16) {
            return _URL_SAFE_DECODABET;
        }
        if ((options & 32) == 32) {
            return _ORDERED_DECODABET;
        }
        return _STANDARD_DECODABET;
    }

    private Base64() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static byte[] encode3to4(byte[] b4, byte[] threeBytes, int numSigBytes, int options) {
        encode3to4(threeBytes, 0, numSigBytes, b4, 0, options);
        return b4;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    public static byte[] encode3to4(byte[] source, int srcOffset, int numSigBytes, byte[] destination, int destOffset, int options) {
        byte[] ALPHABET = getAlphabet(options);
        int inBuff = (numSigBytes > 1 ? (source[srcOffset + 1] << 24) >>> 16 : 0) | (numSigBytes > 0 ? (source[srcOffset] << 24) >>> 8 : 0) | (numSigBytes > 2 ? (source[srcOffset + 2] << 24) >>> 24 : 0);
        switch (numSigBytes) {
            case 1:
                destination[destOffset] = ALPHABET[inBuff >>> 18];
                destination[destOffset + 1] = ALPHABET[(inBuff >>> 12) & 63];
                destination[destOffset + 2] = EQUALS_SIGN;
                destination[destOffset + 3] = EQUALS_SIGN;
                return destination;
            case 2:
                destination[destOffset] = ALPHABET[inBuff >>> 18];
                destination[destOffset + 1] = ALPHABET[(inBuff >>> 12) & 63];
                destination[destOffset + 2] = ALPHABET[(inBuff >>> 6) & 63];
                destination[destOffset + 3] = EQUALS_SIGN;
                return destination;
            case 3:
                destination[destOffset] = ALPHABET[inBuff >>> 18];
                destination[destOffset + 1] = ALPHABET[(inBuff >>> 12) & 63];
                destination[destOffset + 2] = ALPHABET[(inBuff >>> 6) & 63];
                destination[destOffset + 3] = ALPHABET[inBuff & 63];
                return destination;
            default:
                return destination;
        }
    }

    public static void encode(ByteBuffer raw, ByteBuffer encoded) {
        byte[] raw3 = new byte[3];
        byte[] enc4 = new byte[4];
        while (raw.hasRemaining()) {
            int rem = Math.min(3, raw.remaining());
            raw.get(raw3, 0, rem);
            encode3to4(enc4, raw3, rem, 0);
            encoded.put(enc4);
        }
    }

    public static void encode(ByteBuffer raw, CharBuffer encoded) {
        byte[] raw3 = new byte[3];
        byte[] enc4 = new byte[4];
        while (raw.hasRemaining()) {
            int rem = Math.min(3, raw.remaining());
            raw.get(raw3, 0, rem);
            encode3to4(enc4, raw3, rem, 0);
            for (int i = 0; i < 4; i++) {
                encoded.put((char) (enc4[i] & 255));
            }
        }
    }

    public static String encodeObject(Serializable serializableObject) throws IOException {
        return encodeObject(serializableObject, 0);
    }

    public static String encodeObject(Serializable serializableObject, int options) throws Throwable {
        if (serializableObject == null) {
            throw new NullPointerException("Cannot serialize a null object.");
        }
        ByteArrayOutputStream baos = null;
        java.io.OutputStream b64os = null;
        GZIPOutputStream gzos = null;
        ObjectOutputStream oos = null;
        try {
            try {
                ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                try {
                    java.io.OutputStream b64os2 = new OutputStream(baos2, options | 1);
                    try {
                        if ((options & 2) != 0) {
                            GZIPOutputStream gzos2 = new GZIPOutputStream(b64os2);
                            try {
                                oos = new ObjectOutputStream(gzos2);
                                gzos = gzos2;
                            } catch (IOException e) {
                                e = e;
                                gzos = gzos2;
                                b64os = b64os2;
                                baos = baos2;
                                throw e;
                            } catch (Throwable th) {
                                th = th;
                                gzos = gzos2;
                                b64os = b64os2;
                                baos = baos2;
                                try {
                                    oos.close();
                                } catch (Exception e2) {
                                }
                                try {
                                    gzos.close();
                                } catch (Exception e3) {
                                }
                                try {
                                    b64os.close();
                                } catch (Exception e4) {
                                }
                                try {
                                    baos.close();
                                    throw th;
                                } catch (Exception e5) {
                                    throw th;
                                }
                            }
                        } else {
                            oos = new ObjectOutputStream(b64os2);
                        }
                        oos.writeObject(serializableObject);
                        try {
                            oos.close();
                        } catch (Exception e6) {
                        }
                        try {
                            gzos.close();
                        } catch (Exception e7) {
                        }
                        try {
                            b64os2.close();
                        } catch (Exception e8) {
                        }
                        try {
                            baos2.close();
                        } catch (Exception e9) {
                        }
                        try {
                            return new String(baos2.toByteArray(), PREFERRED_ENCODING);
                        } catch (UnsupportedEncodingException e10) {
                            return new String(baos2.toByteArray());
                        }
                    } catch (IOException e11) {
                        e = e11;
                        b64os = b64os2;
                        baos = baos2;
                    } catch (Throwable th2) {
                        th = th2;
                        b64os = b64os2;
                        baos = baos2;
                    }
                } catch (IOException e12) {
                    e = e12;
                    baos = baos2;
                } catch (Throwable th3) {
                    th = th3;
                    baos = baos2;
                }
            } catch (Throwable th4) {
                th = th4;
            }
        } catch (IOException e13) {
            throw e13;
        }
    }

    public static String encodeBytes(byte[] source) throws Throwable {
        String encoded = null;
        try {
            encoded = encodeBytes(source, 0, source.length, 0);
        } catch (IOException ex) {
            if (!$assertionsDisabled) {
                throw new AssertionError(ex.getMessage());
            }
        }
        if ($assertionsDisabled || encoded != null) {
            return encoded;
        }
        throw new AssertionError();
    }

    public static String encodeBytes(byte[] source, int options) throws IOException {
        return encodeBytes(source, 0, source.length, options);
    }

    public static String encodeBytes(byte[] source, int off, int len) throws Throwable {
        String encoded = null;
        try {
            encoded = encodeBytes(source, off, len, 0);
        } catch (IOException ex) {
            if (!$assertionsDisabled) {
                throw new AssertionError(ex.getMessage());
            }
        }
        if ($assertionsDisabled || encoded != null) {
            return encoded;
        }
        throw new AssertionError();
    }

    public static String encodeBytes(byte[] source, int off, int len, int options) throws Throwable {
        byte[] encoded = encodeBytesToBytes(source, off, len, options);
        try {
            return new String(encoded, PREFERRED_ENCODING);
        } catch (UnsupportedEncodingException e) {
            return new String(encoded);
        }
    }

    public static byte[] encodeBytesToBytes(byte[] source) throws Throwable {
        try {
            byte[] encoded = encodeBytesToBytes(source, 0, source.length, 0);
            return encoded;
        } catch (IOException ex) {
            if ($assertionsDisabled) {
                return null;
            }
            throw new AssertionError("IOExceptions only come from GZipping, which is turned off: " + ex.getMessage());
        }
    }

    public static byte[] encodeBytesToBytes(byte[] source, int off, int len, int options) throws Throwable {
        if (source == null) {
            throw new NullPointerException("Cannot serialize a null array.");
        }
        if (off < 0) {
            throw new IllegalArgumentException("Cannot have negative offset: " + off);
        }
        if (len < 0) {
            throw new IllegalArgumentException("Cannot have length offset: " + len);
        }
        if (off + len > source.length) {
            throw new IllegalArgumentException(String.format("Cannot have offset of %d and length of %d with array of length %d", Integer.valueOf(off), Integer.valueOf(len), Integer.valueOf(source.length)));
        }
        if ((options & 2) == 0) {
            boolean breakLines = (options & 8) != 0;
            int encLen = ((len / 3) * 4) + (len % 3 > 0 ? 4 : 0);
            if (breakLines) {
                encLen += encLen / 76;
            }
            byte[] outBuff = new byte[encLen];
            int d = 0;
            int e = 0;
            int len2 = len - 2;
            int lineLength = 0;
            while (d < len2) {
                encode3to4(source, d + off, 3, outBuff, e, options);
                lineLength += 4;
                if (breakLines && lineLength >= 76) {
                    outBuff[e + 4] = 10;
                    e++;
                    lineLength = 0;
                }
                d += 3;
                e += 4;
            }
            if (d < len) {
                encode3to4(source, d + off, len - d, outBuff, e, options);
                e += 4;
            }
            if (e > outBuff.length - 1) {
                return outBuff;
            }
            byte[] finalOut = new byte[e];
            System.arraycopy(outBuff, 0, finalOut, 0, e);
            return finalOut;
        }
        ByteArrayOutputStream baos = null;
        GZIPOutputStream gzos = null;
        OutputStream b64os = null;
        try {
            try {
                ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                try {
                    OutputStream b64os2 = new OutputStream(baos2, options | 1);
                    try {
                        GZIPOutputStream gzos2 = new GZIPOutputStream(b64os2);
                        try {
                            gzos2.write(source, off, len);
                            gzos2.close();
                            try {
                                gzos2.close();
                            } catch (Exception e2) {
                            }
                            try {
                                b64os2.close();
                            } catch (Exception e3) {
                            }
                            try {
                                baos2.close();
                            } catch (Exception e4) {
                            }
                            return baos2.toByteArray();
                        } catch (IOException e5) {
                            e = e5;
                            b64os = b64os2;
                            gzos = gzos2;
                            baos = baos2;
                            throw e;
                        } catch (Throwable th) {
                            th = th;
                            b64os = b64os2;
                            gzos = gzos2;
                            baos = baos2;
                            try {
                                gzos.close();
                            } catch (Exception e6) {
                            }
                            try {
                                b64os.close();
                            } catch (Exception e7) {
                            }
                            try {
                                baos.close();
                                throw th;
                            } catch (Exception e8) {
                                throw th;
                            }
                        }
                    } catch (IOException e9) {
                        e = e9;
                        b64os = b64os2;
                        baos = baos2;
                    } catch (Throwable th2) {
                        th = th2;
                        b64os = b64os2;
                        baos = baos2;
                    }
                } catch (IOException e10) {
                    e = e10;
                    baos = baos2;
                } catch (Throwable th3) {
                    th = th3;
                    baos = baos2;
                }
            } catch (Throwable th4) {
                th = th4;
            }
        } catch (IOException e11) {
            throw e11;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int decode4to3(byte[] source, int srcOffset, byte[] destination, int destOffset, int options) {
        if (source == null) {
            throw new NullPointerException("Source array was null.");
        }
        if (destination == null) {
            throw new NullPointerException("Destination array was null.");
        }
        if (srcOffset < 0 || srcOffset + 3 >= source.length) {
            throw new IllegalArgumentException(String.format("Source array with length %d cannot have offset of %d and still process four bytes.", Integer.valueOf(source.length), Integer.valueOf(srcOffset)));
        }
        if (destOffset < 0 || destOffset + 2 >= destination.length) {
            throw new IllegalArgumentException(String.format("Destination array with length %d cannot have offset of %d and still store three bytes.", Integer.valueOf(destination.length), Integer.valueOf(destOffset)));
        }
        byte[] DECODABET = getDecodabet(options);
        if (source[srcOffset + 2] == 61) {
            destination[destOffset] = (byte) ((((DECODABET[source[srcOffset]] & 255) << 18) | ((DECODABET[source[srcOffset + 1]] & 255) << 12)) >>> 16);
            return 1;
        }
        if (source[srcOffset + 3] == 61) {
            int outBuff = ((DECODABET[source[srcOffset]] & 255) << 18) | ((DECODABET[source[srcOffset + 1]] & 255) << 12) | ((DECODABET[source[srcOffset + 2]] & 255) << 6);
            destination[destOffset] = (byte) (outBuff >>> 16);
            destination[destOffset + 1] = (byte) (outBuff >>> 8);
            return 2;
        }
        int outBuff2 = ((DECODABET[source[srcOffset]] & 255) << 18) | ((DECODABET[source[srcOffset + 1]] & 255) << 12) | ((DECODABET[source[srcOffset + 2]] & 255) << 6) | (DECODABET[source[srcOffset + 3]] & 255);
        destination[destOffset] = (byte) (outBuff2 >> 16);
        destination[destOffset + 1] = (byte) (outBuff2 >> 8);
        destination[destOffset + 2] = (byte) outBuff2;
        return 3;
    }

    public static byte[] decode(byte[] source) throws IOException {
        return decode(source, 0, source.length, 0);
    }

    public static byte[] decode(byte[] source, int off, int len, int options) throws IOException {
        if (source == null) {
            throw new NullPointerException("Cannot decode null source array.");
        }
        if (off < 0 || off + len > source.length) {
            throw new IllegalArgumentException(String.format("Source array with length %d cannot have offset of %d and process %d bytes.", Integer.valueOf(source.length), Integer.valueOf(off), Integer.valueOf(len)));
        }
        if (len == 0) {
            return new byte[0];
        }
        if (len < 4) {
            throw new IllegalArgumentException("Base64-encoded string must have at least four characters, but length specified was " + len);
        }
        byte[] DECODABET = getDecodabet(options);
        int len34 = (len * 3) / 4;
        byte[] outBuff = new byte[len34];
        int outBuffPosn = 0;
        byte[] b4 = new byte[4];
        int b4Posn = 0;
        int i = off;
        while (true) {
            int b4Posn2 = b4Posn;
            if (i >= off + len) {
                break;
            }
            byte sbiDecode = DECODABET[source[i] & 255];
            if (sbiDecode >= -5) {
                if (sbiDecode >= -1) {
                    b4Posn = b4Posn2 + 1;
                    b4[b4Posn2] = source[i];
                    if (b4Posn > 3) {
                        outBuffPosn += decode4to3(b4, 0, outBuff, outBuffPosn, options);
                        b4Posn = 0;
                        if (source[i] == 61) {
                            break;
                        }
                    } else {
                        continue;
                    }
                } else {
                    b4Posn = b4Posn2;
                }
                i++;
            } else {
                throw new IOException(String.format("Bad Base64 input character decimal %d in array position %d", Integer.valueOf(source[i] & 255), Integer.valueOf(i)));
            }
        }
        byte[] out = new byte[outBuffPosn];
        System.arraycopy(outBuff, 0, out, 0, outBuffPosn);
        return out;
    }

    public static byte[] decode(String s) throws IOException {
        return decode(s, 0);
    }

    /*  JADX ERROR: JadxRuntimeException in pass: RegionMakerVisitor
        jadx.core.utils.exceptions.JadxRuntimeException: Can't find top splitter block for handler:B:38:0x0085
        	at jadx.core.utils.BlockUtils.getTopSplitterForHandler(BlockUtils.java:1182)
        	at jadx.core.dex.visitors.regions.maker.ExcHandlersRegionMaker.collectHandlerRegions(ExcHandlersRegionMaker.java:53)
        	at jadx.core.dex.visitors.regions.maker.ExcHandlersRegionMaker.process(ExcHandlersRegionMaker.java:38)
        	at jadx.core.dex.visitors.regions.RegionMakerVisitor.visit(RegionMakerVisitor.java:27)
        */
    public static byte[] decode(java.lang.String r17, int r18) throws java.lang.Throwable {
        /*
            if (r17 != 0) goto La
            java.lang.NullPointerException r14 = new java.lang.NullPointerException
            java.lang.String r15 = "Input string was null."
            r14.<init>(r15)
            throw r14
        La:
            java.lang.String r14 = "US-ASCII"
            r0 = r17
            byte[] r6 = r0.getBytes(r14)     // Catch: java.io.UnsupportedEncodingException -> L6f
        L12:
            r14 = 0
            int r15 = r6.length
            r0 = r18
            byte[] r6 = decode(r6, r14, r15, r0)
            r14 = r18 & 4
            if (r14 == 0) goto L75
            r7 = 1
        L1f:
            if (r6 == 0) goto L6e
            int r14 = r6.length
            r15 = 4
            if (r14 < r15) goto L6e
            if (r7 != 0) goto L6e
            r14 = 0
            r14 = r6[r14]
            r14 = r14 & 255(0xff, float:3.57E-43)
            r15 = 1
            r15 = r6[r15]
            int r15 = r15 << 8
            r16 = 65280(0xff00, float:9.1477E-41)
            r15 = r15 & r16
            r11 = r14 | r15
            r14 = 35615(0x8b1f, float:4.9907E-41)
            if (r14 != r11) goto L6e
            r1 = 0
            r9 = 0
            r3 = 0
            r14 = 2048(0x800, float:2.87E-42)
            byte[] r5 = new byte[r14]
            java.io.ByteArrayOutputStream r4 = new java.io.ByteArrayOutputStream     // Catch: java.lang.Throwable -> L87 java.io.IOException -> Lac
            r4.<init>()     // Catch: java.lang.Throwable -> L87 java.io.IOException -> Lac
            java.io.ByteArrayInputStream r2 = new java.io.ByteArrayInputStream     // Catch: java.lang.Throwable -> La0 java.io.IOException -> Lae
            r2.<init>(r6)     // Catch: java.lang.Throwable -> La0 java.io.IOException -> Lae
            java.util.zip.GZIPInputStream r10 = new java.util.zip.GZIPInputStream     // Catch: java.lang.Throwable -> La3 java.io.IOException -> Lb1
            r10.<init>(r2)     // Catch: java.lang.Throwable -> La3 java.io.IOException -> Lb1
        L53:
            int r12 = r10.read(r5)     // Catch: java.io.IOException -> L5e java.lang.Throwable -> La7
            if (r12 < 0) goto L77
            r14 = 0
            r4.write(r5, r14, r12)     // Catch: java.io.IOException -> L5e java.lang.Throwable -> La7
            goto L53
        L5e:
            r8 = move-exception
            r3 = r4
            r9 = r10
            r1 = r2
        L62:
            r8.printStackTrace()     // Catch: java.lang.Throwable -> L87
            r3.close()     // Catch: java.lang.Exception -> L98
        L68:
            r9.close()     // Catch: java.lang.Exception -> L9a
        L6b:
            r1.close()     // Catch: java.lang.Exception -> L85
        L6e:
            return r6
        L6f:
            r13 = move-exception
            byte[] r6 = r17.getBytes()
            goto L12
        L75:
            r7 = 0
            goto L1f
        L77:
            byte[] r6 = r4.toByteArray()     // Catch: java.io.IOException -> L5e java.lang.Throwable -> La7
            r4.close()     // Catch: java.lang.Exception -> L9c
        L7e:
            r10.close()     // Catch: java.lang.Exception -> L9e
        L81:
            r2.close()     // Catch: java.lang.Exception -> L85
            goto L6e
        L85:
            r14 = move-exception
            goto L6e
        L87:
            r14 = move-exception
        L88:
            r3.close()     // Catch: java.lang.Exception -> L92
        L8b:
            r9.close()     // Catch: java.lang.Exception -> L94
        L8e:
            r1.close()     // Catch: java.lang.Exception -> L96
        L91:
            throw r14
        L92:
            r15 = move-exception
            goto L8b
        L94:
            r15 = move-exception
            goto L8e
        L96:
            r15 = move-exception
            goto L91
        L98:
            r14 = move-exception
            goto L68
        L9a:
            r14 = move-exception
            goto L6b
        L9c:
            r14 = move-exception
            goto L7e
        L9e:
            r14 = move-exception
            goto L81
        La0:
            r14 = move-exception
            r3 = r4
            goto L88
        La3:
            r14 = move-exception
            r3 = r4
            r1 = r2
            goto L88
        La7:
            r14 = move-exception
            r3 = r4
            r9 = r10
            r1 = r2
            goto L88
        Lac:
            r8 = move-exception
            goto L62
        Lae:
            r8 = move-exception
            r3 = r4
            goto L62
        Lb1:
            r8 = move-exception
            r3 = r4
            r1 = r2
            goto L62
        */
        throw new UnsupportedOperationException("Method not decompiled: org.java_websocket.util.Base64.decode(java.lang.String, int):byte[]");
    }

    public static Object decodeToObject(String encodedObject) throws IOException, ClassNotFoundException {
        return decodeToObject(encodedObject, 0, null);
    }

    public static Object decodeToObject(String encodedObject, int options, final ClassLoader loader) throws Throwable {
        ByteArrayInputStream bais;
        byte[] objBytes = decode(encodedObject, options);
        ByteArrayInputStream bais2 = null;
        ObjectInputStream ois = null;
        try {
            try {
                bais = new ByteArrayInputStream(objBytes);
            } catch (Throwable th) {
                th = th;
            }
            try {
                ois = loader == null ? new ObjectInputStream(bais) : new ObjectInputStream(bais) { // from class: org.java_websocket.util.Base64.1
                    @Override // java.io.ObjectInputStream
                    public Class<?> resolveClass(ObjectStreamClass streamClass) throws ClassNotFoundException, IOException {
                        Class<?> c = Class.forName(streamClass.getName(), false, loader);
                        if (c == null) {
                            return super.resolveClass(streamClass);
                        }
                        return c;
                    }
                };
                Object obj = ois.readObject();
                try {
                    bais.close();
                } catch (Exception e) {
                }
                try {
                    ois.close();
                } catch (Exception e2) {
                }
                return obj;
            } catch (IOException e3) {
                throw e3;
            } catch (ClassNotFoundException e4) {
                throw e4;
            } catch (Throwable th2) {
                th = th2;
                bais2 = bais;
                try {
                    bais2.close();
                } catch (Exception e5) {
                }
                try {
                    ois.close();
                    throw th;
                } catch (Exception e6) {
                    throw th;
                }
            }
        } catch (IOException e7) {
            throw e7;
        } catch (ClassNotFoundException e8) {
            throw e8;
        }
    }

    public static void encodeToFile(byte[] dataToEncode, String filename) throws Throwable {
        OutputStream bos;
        if (dataToEncode == null) {
            throw new NullPointerException("Data to encode was null.");
        }
        OutputStream bos2 = null;
        try {
            try {
                bos = new OutputStream(new FileOutputStream(filename), 1);
            } catch (IOException e) {
                throw e;
            }
        } catch (Throwable th) {
            th = th;
        }
        try {
            bos.write(dataToEncode);
            try {
                bos.close();
            } catch (Exception e2) {
            }
        } catch (IOException e3) {
        } catch (Throwable th2) {
            th = th2;
            bos2 = bos;
            try {
                bos2.close();
            } catch (Exception e4) {
            }
            throw th;
        }
    }

    public static void decodeToFile(String dataToDecode, String filename) throws Throwable {
        OutputStream bos;
        OutputStream bos2 = null;
        try {
            try {
                bos = new OutputStream(new FileOutputStream(filename), 0);
            } catch (IOException e) {
                throw e;
            }
        } catch (Throwable th) {
            th = th;
        }
        try {
            bos.write(dataToDecode.getBytes(PREFERRED_ENCODING));
            try {
                bos.close();
            } catch (Exception e2) {
            }
        } catch (IOException e3) {
        } catch (Throwable th2) {
            th = th2;
            bos2 = bos;
            try {
                bos2.close();
            } catch (Exception e4) {
            }
            throw th;
        }
    }

    public static byte[] decodeFromFile(String filename) throws Throwable {
        InputStream bis = null;
        try {
            try {
                File file = new File(filename);
                int length = 0;
                if (file.length() > 2147483647L) {
                    throw new IOException("File is too big for this convenience method (" + file.length() + " bytes).");
                }
                byte[] buffer = new byte[(int) file.length()];
                InputStream bis2 = new InputStream(new BufferedInputStream(new FileInputStream(file)), 0);
                while (true) {
                    try {
                        int numBytes = bis2.read(buffer, length, 4096);
                        if (numBytes < 0) {
                            break;
                        }
                        length += numBytes;
                    } catch (IOException e) {
                        throw e;
                    } catch (Throwable th) {
                        th = th;
                        bis = bis2;
                        try {
                            bis.close();
                        } catch (Exception e2) {
                        }
                        throw th;
                    }
                }
                byte[] decodedData = new byte[length];
                System.arraycopy(buffer, 0, decodedData, 0, length);
                try {
                    bis2.close();
                } catch (Exception e3) {
                }
                return decodedData;
            } catch (IOException e4) {
                throw e4;
            }
        } catch (Throwable th2) {
            th = th2;
        }
    }

    public static String encodeFromFile(String filename) throws Throwable {
        InputStream bis = null;
        try {
            try {
                File file = new File(filename);
                byte[] buffer = new byte[Math.max((int) ((file.length() * 1.4d) + 1.0d), 40)];
                int length = 0;
                InputStream bis2 = new InputStream(new BufferedInputStream(new FileInputStream(file)), 1);
                while (true) {
                    try {
                        int numBytes = bis2.read(buffer, length, 4096);
                        if (numBytes < 0) {
                            break;
                        }
                        length += numBytes;
                    } catch (IOException e) {
                        bis = bis2;
                        throw e;
                    } catch (Throwable th) {
                        th = th;
                        bis = bis2;
                        try {
                            bis.close();
                        } catch (Exception e2) {
                        }
                        throw th;
                    }
                }
                String encodedData = new String(buffer, 0, length, PREFERRED_ENCODING);
                try {
                    bis2.close();
                } catch (Exception e3) {
                }
                return encodedData;
            } catch (Throwable th2) {
                th = th2;
            }
        } catch (IOException e4) {
            throw e4;
        }
    }

    public static void encodeFileToFile(String infile, String outfile) throws Throwable {
        java.io.OutputStream out;
        String encoded = encodeFromFile(infile);
        java.io.OutputStream out2 = null;
        try {
            try {
                out = new BufferedOutputStream(new FileOutputStream(outfile));
            } catch (Throwable th) {
                th = th;
            }
            try {
                out.write(encoded.getBytes(PREFERRED_ENCODING));
                try {
                    out.close();
                } catch (Exception e) {
                }
            } catch (IOException e2) {
                out2 = out;
                throw e2;
            } catch (Throwable th2) {
                th = th2;
                out2 = out;
                try {
                    out2.close();
                } catch (Exception e3) {
                }
                throw th;
            }
        } catch (IOException e4) {
        }
    }

    public static void decodeFileToFile(String infile, String outfile) throws Throwable {
        java.io.OutputStream out;
        byte[] decoded = decodeFromFile(infile);
        java.io.OutputStream out2 = null;
        try {
            try {
                out = new BufferedOutputStream(new FileOutputStream(outfile));
            } catch (IOException e) {
                throw e;
            }
        } catch (Throwable th) {
            th = th;
        }
        try {
            out.write(decoded);
            try {
                out.close();
            } catch (Exception e2) {
            }
        } catch (IOException e3) {
        } catch (Throwable th2) {
            th = th2;
            out2 = out;
            try {
                out2.close();
            } catch (Exception e4) {
            }
            throw th;
        }
    }

    public static class InputStream extends FilterInputStream {
        private boolean breakLines;
        private byte[] buffer;
        private int bufferLength;
        private byte[] decodabet;
        private boolean encode;
        private int lineLength;
        private int numSigBytes;
        private int options;
        private int position;

        public InputStream(java.io.InputStream in) {
            this(in, 0);
        }

        public InputStream(java.io.InputStream in, int options) {
            super(in);
            this.options = options;
            this.breakLines = (options & 8) > 0;
            this.encode = (options & 1) > 0;
            this.bufferLength = this.encode ? 4 : 3;
            this.buffer = new byte[this.bufferLength];
            this.position = -1;
            this.lineLength = 0;
            this.decodabet = Base64.getDecodabet(options);
        }

        @Override // java.io.FilterInputStream, java.io.InputStream
        public int read() throws IOException {
            int b;
            if (this.position < 0) {
                if (this.encode) {
                    byte[] b3 = new byte[3];
                    int numBinaryBytes = 0;
                    for (int i = 0; i < 3; i++) {
                        int b2 = this.in.read();
                        if (b2 < 0) {
                            break;
                        }
                        b3[i] = (byte) b2;
                        numBinaryBytes++;
                    }
                    if (numBinaryBytes <= 0) {
                        return -1;
                    }
                    Base64.encode3to4(b3, 0, numBinaryBytes, this.buffer, 0, this.options);
                    this.position = 0;
                    this.numSigBytes = 4;
                } else {
                    byte[] b4 = new byte[4];
                    int i2 = 0;
                    while (i2 < 4) {
                        do {
                            b = this.in.read();
                            if (b < 0) {
                                break;
                            }
                        } while (this.decodabet[b & 127] <= -5);
                        if (b < 0) {
                            break;
                        }
                        b4[i2] = (byte) b;
                        i2++;
                    }
                    if (i2 == 4) {
                        this.numSigBytes = Base64.decode4to3(b4, 0, this.buffer, 0, this.options);
                        this.position = 0;
                    } else {
                        if (i2 == 0) {
                            return -1;
                        }
                        throw new IOException("Improperly padded Base64 input.");
                    }
                }
            }
            if (this.position >= 0) {
                if (this.position >= this.numSigBytes) {
                    return -1;
                }
                if (this.encode && this.breakLines && this.lineLength >= 76) {
                    this.lineLength = 0;
                    return 10;
                }
                this.lineLength++;
                byte[] bArr = this.buffer;
                int i3 = this.position;
                this.position = i3 + 1;
                int b5 = bArr[i3];
                if (this.position >= this.bufferLength) {
                    this.position = -1;
                }
                return b5 & 255;
            }
            throw new IOException("Error in Base64 code reading stream.");
        }

        @Override // java.io.FilterInputStream, java.io.InputStream
        public int read(byte[] dest, int off, int len) throws IOException {
            int i = 0;
            while (i < len) {
                int b = read();
                if (b >= 0) {
                    dest[off + i] = (byte) b;
                    i++;
                } else {
                    if (i == 0) {
                        return -1;
                    }
                    return i;
                }
            }
            return i;
        }
    }

    public static class OutputStream extends FilterOutputStream {
        private byte[] b4;
        private boolean breakLines;
        private byte[] buffer;
        private int bufferLength;
        private byte[] decodabet;
        private boolean encode;
        private int lineLength;
        private int options;
        private int position;
        private boolean suspendEncoding;

        public OutputStream(java.io.OutputStream out) {
            this(out, 1);
        }

        public OutputStream(java.io.OutputStream out, int options) {
            super(out);
            this.breakLines = (options & 8) != 0;
            this.encode = (options & 1) != 0;
            this.bufferLength = this.encode ? 3 : 4;
            this.buffer = new byte[this.bufferLength];
            this.position = 0;
            this.lineLength = 0;
            this.suspendEncoding = false;
            this.b4 = new byte[4];
            this.options = options;
            this.decodabet = Base64.getDecodabet(options);
        }

        @Override // java.io.FilterOutputStream, java.io.OutputStream
        public void write(int theByte) throws IOException {
            if (this.suspendEncoding) {
                this.out.write(theByte);
                return;
            }
            if (this.encode) {
                byte[] bArr = this.buffer;
                int i = this.position;
                this.position = i + 1;
                bArr[i] = (byte) theByte;
                if (this.position >= this.bufferLength) {
                    this.out.write(Base64.encode3to4(this.b4, this.buffer, this.bufferLength, this.options));
                    this.lineLength += 4;
                    if (this.breakLines && this.lineLength >= 76) {
                        this.out.write(10);
                        this.lineLength = 0;
                    }
                    this.position = 0;
                    return;
                }
                return;
            }
            if (this.decodabet[theByte & 127] > -5) {
                byte[] bArr2 = this.buffer;
                int i2 = this.position;
                this.position = i2 + 1;
                bArr2[i2] = (byte) theByte;
                if (this.position >= this.bufferLength) {
                    int len = Base64.decode4to3(this.buffer, 0, this.b4, 0, this.options);
                    this.out.write(this.b4, 0, len);
                    this.position = 0;
                    return;
                }
                return;
            }
            if (this.decodabet[theByte & 127] != -5) {
                throw new IOException("Invalid character in Base64 data.");
            }
        }

        @Override // java.io.FilterOutputStream, java.io.OutputStream
        public void write(byte[] theBytes, int off, int len) throws IOException {
            if (this.suspendEncoding) {
                this.out.write(theBytes, off, len);
                return;
            }
            for (int i = 0; i < len; i++) {
                write(theBytes[off + i]);
            }
        }

        public void flushBase64() throws IOException {
            if (this.position > 0) {
                if (this.encode) {
                    this.out.write(Base64.encode3to4(this.b4, this.buffer, this.position, this.options));
                    this.position = 0;
                    return;
                }
                throw new IOException("Base64 input not properly padded.");
            }
        }

        @Override // java.io.FilterOutputStream, java.io.OutputStream, java.io.Closeable, java.lang.AutoCloseable
        public void close() throws IOException {
            flushBase64();
            super.close();
            this.buffer = null;
            this.out = null;
        }

        public void suspendEncoding() throws IOException {
            flushBase64();
            this.suspendEncoding = true;
        }

        public void resumeEncoding() {
            this.suspendEncoding = false;
        }
    }
}
