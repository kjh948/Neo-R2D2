package com.koushikdutta.async.util;

import android.support.v4.media.session.PlaybackStateCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
public class FileCache {
    private static String hashAlgorithm = "MD5";
    static MessageDigest messageDigest;
    File directory;
    boolean loadAsync;
    boolean loading;
    long size;
    Random random = new Random();
    long blockSize = PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
    Comparator<File> dateCompare = new Comparator<File>() { // from class: com.koushikdutta.async.util.FileCache.1
        @Override // java.util.Comparator
        public int compare(File lhs, File rhs) {
            long l = lhs.lastModified();
            long r = rhs.lastModified();
            if (l < r) {
                return -1;
            }
            if (r > l) {
                return 1;
            }
            return 0;
        }
    };
    InternalCache cache = new InternalCache();

    class CacheEntry {
        final long size;

        public CacheEntry(File file) {
            this.size = file.length();
        }
    }

    public static class Snapshot {
        FileInputStream[] fins;
        long[] lens;

        Snapshot(FileInputStream[] fins, long[] lens) {
            this.fins = fins;
            this.lens = lens;
        }

        public long getLength(int index) {
            return this.lens[index];
        }

        public void close() {
            StreamUtility.closeQuietly(this.fins);
        }
    }

    static {
        try {
            messageDigest = MessageDigest.getInstance(hashAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            messageDigest = findAlternativeMessageDigest();
            if (messageDigest == null) {
                throw new RuntimeException(e);
            }
        }
        try {
            messageDigest = (MessageDigest) messageDigest.clone();
        } catch (CloneNotSupportedException e2) {
        }
    }

    private static MessageDigest findAlternativeMessageDigest() {
        MessageDigest messageDigest2;
        if ("MD5".equals(hashAlgorithm)) {
            for (Provider provider : Security.getProviders()) {
                for (Provider.Service service : provider.getServices()) {
                    hashAlgorithm = service.getAlgorithm();
                    try {
                        messageDigest2 = MessageDigest.getInstance(hashAlgorithm);
                    } catch (NoSuchAlgorithmException e) {
                    }
                    if (messageDigest2 != null) {
                        return messageDigest2;
                    }
                }
            }
        }
        return null;
    }

    public static synchronized String toKeyString(Object... parts) {
        byte[] md5bytes;
        messageDigest.reset();
        for (Object part : parts) {
            messageDigest.update(part.toString().getBytes());
        }
        md5bytes = messageDigest.digest();
        return new BigInteger(1, md5bytes).toString(16);
    }

    public File getTempFile() {
        File f;
        do {
            f = new File(this.directory, new BigInteger(128, this.random).toString(16));
        } while (f.exists());
        return f;
    }

    public File[] getTempFiles(int count) {
        File[] ret = new File[count];
        for (int i = 0; i < count; i++) {
            ret[i] = getTempFile();
        }
        return ret;
    }

    public static void removeFiles(File... files) {
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    public void remove(String key) {
        for (int i = 0; this.cache.remove(getPartName(key, i)) != null; i++) {
        }
        removePartFiles(key);
    }

    public boolean exists(String key, int part) {
        return getPartFile(key, part).exists();
    }

    public boolean exists(String key) {
        return getPartFile(key, 0).exists();
    }

    public File touch(File file) {
        this.cache.get(file.getName());
        file.setLastModified(System.currentTimeMillis());
        return file;
    }

    public FileInputStream get(String key) throws IOException {
        return new FileInputStream(touch(getPartFile(key, 0)));
    }

    public File getFile(String key) {
        return touch(getPartFile(key, 0));
    }

    public FileInputStream[] get(String key, int count) throws IOException {
        FileInputStream[] ret = new FileInputStream[count];
        for (int i = 0; i < count; i++) {
            try {
                ret[i] = new FileInputStream(touch(getPartFile(key, i)));
            } catch (IOException e) {
                for (FileInputStream fin : ret) {
                    StreamUtility.closeQuietly(fin);
                }
                remove(key);
                throw e;
            }
        }
        return ret;
    }

    String getPartName(String key, int part) {
        return key + "." + part;
    }

    public void commitTempFiles(String key, File... tempFiles) {
        removePartFiles(key);
        for (int i = 0; i < tempFiles.length; i++) {
            File tmp = tempFiles[i];
            File partFile = getPartFile(key, i);
            if (!tmp.renameTo(partFile)) {
                removeFiles(tempFiles);
                remove(key);
                return;
            } else {
                remove(tmp.getName());
                this.cache.put(getPartName(key, i), new CacheEntry(partFile));
            }
        }
    }

    void removePartFiles(String key) {
        int i = 0;
        while (true) {
            File f = getPartFile(key, i);
            if (f.exists()) {
                f.delete();
                i++;
            } else {
                return;
            }
        }
    }

    File getPartFile(String key, int part) {
        return new File(this.directory, getPartName(key, part));
    }

    public void setBlockSize(long blockSize) {
        this.blockSize = blockSize;
    }

    class InternalCache extends LruCache<String, CacheEntry> {
        public InternalCache() {
            super(FileCache.this.size);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // com.koushikdutta.async.util.LruCache
        public long sizeOf(String key, CacheEntry value) {
            return Math.max(FileCache.this.blockSize, value.size);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // com.koushikdutta.async.util.LruCache
        public void entryRemoved(boolean evicted, String key, CacheEntry oldValue, CacheEntry newValue) {
            super.entryRemoved(evicted, key, oldValue, newValue);
            if (newValue == null && !FileCache.this.loading) {
                new File(FileCache.this.directory, key).delete();
            }
        }
    }

    void load() {
        this.loading = true;
        try {
            File[] files = this.directory.listFiles();
            if (files != null) {
                ArrayList<File> list = new ArrayList<>();
                Collections.addAll(list, files);
                Collections.sort(list, this.dateCompare);
                for (File file : list) {
                    String name = file.getName();
                    CacheEntry entry = new CacheEntry(file);
                    this.cache.put(name, entry);
                    this.cache.get(name);
                }
            }
        } finally {
            this.loading = false;
        }
    }

    /* JADX WARN: Type inference failed for: r0v1, types: [com.koushikdutta.async.util.FileCache$2] */
    private void doLoad() {
        if (this.loadAsync) {
            new Thread() { // from class: com.koushikdutta.async.util.FileCache.2
                @Override // java.lang.Thread, java.lang.Runnable
                public void run() {
                    FileCache.this.load();
                }
            }.start();
        } else {
            load();
        }
    }

    public FileCache(File directory, long size, boolean loadAsync) {
        this.directory = directory;
        this.size = size;
        this.loadAsync = loadAsync;
        directory.mkdirs();
        doLoad();
    }

    public long size() {
        return this.cache.size();
    }

    public void clear() {
        removeFiles(this.directory.listFiles());
        this.cache.evictAll();
    }

    public Set<String> keySet() {
        HashSet<String> ret = new HashSet<>();
        File[] files = this.directory.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                int last = name.lastIndexOf(46);
                if (last != -1) {
                    ret.add(name.substring(0, last));
                }
            }
        }
        return ret;
    }

    public void setMaxSize(long maxSize) {
        this.cache.setMaxSize(maxSize);
        doLoad();
    }
}
