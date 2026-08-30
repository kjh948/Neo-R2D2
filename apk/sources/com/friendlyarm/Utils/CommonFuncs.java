package com.friendlyarm.Utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

/* JADX INFO: loaded from: classes.dex */
public class CommonFuncs {
    public static void showAlertDialog(Context context, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message).setCancelable(false).setPositiveButton("Close", new DialogInterface.OnClickListener() { // from class: com.friendlyarm.Utils.CommonFuncs.1
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }
}
