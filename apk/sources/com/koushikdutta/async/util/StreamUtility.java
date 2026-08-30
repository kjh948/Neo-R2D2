package com.koushikdutta.async.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/* JADX INFO: loaded from: classes.dex */
public class StreamUtility {
    public static void fastChannelCopy(ReadableByteChannel src, WritableByteChannel dest) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16384);
        while (src.read(buffer) != -1) {
            buffer.flip();
            dest.write(buffer);
            buffer.compact();
        }
        buffer.flip();
        while (buffer.hasRemaining()) {
            dest.write(buffer);
        }
    }

    public static void copyStream(InputStream input, OutputStream output) throws IOException {
        ReadableByteChannel inputChannel = Channels.newChannel(input);
        WritableByteChannel outputChannel = Channels.newChannel(output);
        fastChannelCopy(inputChannel, outputChannel);
    }

    public static byte[] readToEndAsArray(InputStream input) throws IOException {
        DataInputStream dis = new DataInputStream(input);
        byte[] stuff = new byte[1024];
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        while (true) {
            int read = dis.read(stuff);
            if (read != -1) {
                buff.write(stuff, 0, read);
            } else {
                dis.close();
                return buff.toByteArray();
            }
        }
    }

    public static String readToEnd(InputStream input) throws IOException {
        return new String(readToEndAsArray(input));
    }

    public static String readFile(String filename) throws IOException {
        return readFile(new File(filename));
    }

    public static String readFileSilent(String filename) {
        try {
            return readFile(new File(filename));
        } catch (IOException e) {
            return null;
        }
    }

    public static String readFile(File file) throws Throwable {
        DataInputStream input;
        byte[] buffer = new byte[(int) file.length()];
        DataInputStream input2 = null;
        try {
            input = new DataInputStream(new FileInputStream(file));
        } catch (Throwable th) {
            th = th;
        }
        try {
            input.readFully(buffer);
            closeQuietly(input);
            return new String(buffer);
        } catch (Throwable th2) {
            th = th2;
            input2 = input;
            closeQuietly(input2);
            throw th;
        }
    }

    public static void writeFile(File file, String string) throws IOException {
        file.getParentFile().mkdirs();
        DataOutputStream dout = new DataOutputStream(new FileOutputStream(file));
        dout.write(string.getBytes());
        dout.close();
    }

    public static void writeFile(String file, String string) throws IOException {
        writeFile(new File(file), string);
    }

    public static void closeQuietly(Closeable... closeables) {
        if (closeables != null) {
            for (Closeable closeable : closeables) {
                if (closeable != null) {
                    try {
                        closeable.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    public static void eat(InputStream input) throws IOException {
        byte[] stuff = new byte[1024];
        while (input.read(stuff) != -1) {
        }
    }
}
