package com.bullb.r2d2_nanopisystem.utils;

import android.R;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Patterns;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import com.bullb.r2d2_nanopisystem.Commander;
import com.bullb.r2d2_nanopisystem.Model.Robot;
import com.bullb.r2d2_nanopisystem.WebSocket.SocketServer;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public class SharedUtils {
    public static final String REGISTRATION_COMPLETE = "registrationComplete";
    public static final String SENT_TOKEN_TO_SERVER = "sentTokenToServer";
    public static int LOW_BATTERY_PERCENTAGE = 20;
    public static int UNAUTHORIZED = 401;
    public static boolean BLUETOOTH_ENABLE = false;
    public static String ROBOT_API_KEY = "01a2ecdcae3687b53631866647c13c28";
    public static String GCM_SENDER_ID = "476197366820";

    public static void changeLocale(Context context, Locale locale) {
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        context.getApplicationContext().getResources().updateConfiguration(config, null);
    }

    public static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    public static final boolean isValidEmail(CharSequence target) {
        if (target == null) {
            return false;
        }
        return Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

    public static final int randomNum() {
        Random r = new Random();
        int i1 = r.nextInt(600);
        return i1;
    }

    public static final int randomNum(int max) {
        Random r = new Random();
        int i1 = r.nextInt(max);
        return i1;
    }

    public static int getStatusBarHeight(Context context) {
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId <= 0) {
            return 0;
        }
        int statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        return statusBarHeight;
    }

    public static int getToolbarHeight(Context context) {
        TypedArray styledAttributes = context.getTheme().obtainStyledAttributes(new int[]{R.attr.actionBarSize});
        int actionBarHeight = (int) styledAttributes.getDimension(0, 0.0f);
        styledAttributes.recycle();
        return actionBarHeight;
    }

    public static Uri getLocalBitmapUri(ImageView imageView) {
        Uri outputFileUri;
        OutputStream fOut;
        imageView.buildDrawingCache();
        Bitmap bm = imageView.getDrawingCache();
        try {
            String root = Environment.getExternalStorageDirectory() + "//s_we_wedding//";
            File dir = new File(root);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File imageFile = new File(root, "invitation.jpg");
            outputFileUri = Uri.fromFile(imageFile);
            fOut = new FileOutputStream(imageFile);
        } catch (Exception e) {
            e = e;
        }
        try {
            bm.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut.flush();
            fOut.close();
            return outputFileUri;
        } catch (Exception e2) {
            e = e2;
            e.printStackTrace();
            return null;
        }
    }

    public static void setCursorDrawableColor(EditText editText, int color) {
        try {
            Field fCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
            fCursorDrawableRes.setAccessible(true);
            int mCursorDrawableRes = fCursorDrawableRes.getInt(editText);
            Field fEditor = TextView.class.getDeclaredField("mEditor");
            fEditor.setAccessible(true);
            Object editor = fEditor.get(editText);
            Class<?> clazz = editor.getClass();
            Field fCursorDrawable = clazz.getDeclaredField("mCursorDrawable");
            fCursorDrawable.setAccessible(true);
            Drawable[] drawables = {editText.getContext().getResources().getDrawable(mCursorDrawableRes), editText.getContext().getResources().getDrawable(mCursorDrawableRes)};
            drawables[0].setColorFilter(color, PorterDuff.Mode.SRC_IN);
            drawables[1].setColorFilter(color, PorterDuff.Mode.SRC_IN);
            fCursorDrawable.set(editor, drawables);
        } catch (Throwable th) {
        }
    }

    public static void hideKeyboard(Context context, View layout) {
        try {
            InputMethodManager imm = (InputMethodManager) context.getSystemService("input_method");
            imm.hideSoftInputFromWindow(layout.getWindowToken(), 0);
        } catch (Exception e) {
        }
    }

    public static int get16to9Height(Context context) {
        Display display = ((Activity) context).getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return (size.x * 9) / 16;
    }

    public static int getScreenHeight(Context context) {
        Display display = ((Activity) context).getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.y;
    }

    public static void disableEnableControls(boolean enable, ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            child.setEnabled(enable);
            if (child instanceof ViewGroup) {
                disableEnableControls(enable, (ViewGroup) child);
            }
        }
    }

    public static String convertMediaUriToPath(Uri uri, Context context) {
        boolean isKitKat = Build.VERSION.SDK_INT >= 19;
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                if ("primary".equalsIgnoreCase(split[0])) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
                return null;
            }
            if (isDownloadsDocument(uri)) {
                String id = DocumentsContract.getDocumentId(uri);
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id).longValue());
                return getDataColumn(context, contentUri, null, null);
            }
            if (!isMediaDocument(uri)) {
                return null;
            }
            String docId2 = DocumentsContract.getDocumentId(uri);
            String[] split2 = docId2.split(":");
            String type = split2[0];
            Uri contentUri2 = null;
            if ("image".equals(type)) {
                contentUri2 = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if ("video".equals(type)) {
                contentUri2 = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if ("audio".equals(type)) {
                contentUri2 = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }
            String[] selectionArgs = {split2[1]};
            return getDataColumn(context, contentUri2, "_id=?", selectionArgs);
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }
            return getDataColumn(context, uri, null, null);
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        String[] projection = {"_data"};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndexOrThrow("_data");
                String string = cursor.getString(index);
            }
            if (cursor != null) {
                cursor.close();
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    public static void clearAllPreferences(Context context) {
        RobotPreference.clearPreference(context);
        WIFIPreference.clearPreference(context);
    }

    public static void notifyRobotChanged(Context context) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("cmd", Commander.GIN);
            Robot robot = new Robot(context, true);
            jsonObject.put("robot", new JSONObject(new Gson().toJson(robot)));
            try {
                SocketServer socketServer = SocketServer.getInstance(context);
                socketServer.send(jsonObject.toString() + "\n");
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        } catch (JSONException e2) {
            e2.printStackTrace();
        }
    }
}
