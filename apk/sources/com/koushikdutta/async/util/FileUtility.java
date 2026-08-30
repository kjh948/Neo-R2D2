package com.koushikdutta.async.util;

import java.io.File;

/* JADX INFO: loaded from: classes.dex */
public class FileUtility {
    public static boolean deleteDirectory(File path) {
        File[] files;
        if (path.exists() && (files = path.listFiles()) != null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return path.delete();
    }
}
