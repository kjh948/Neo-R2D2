package android.support.v4.view;

import android.annotation.TargetApi;
import android.support.annotation.RequiresApi;
import android.view.ViewGroup;

/* JADX INFO: loaded from: classes.dex */
@RequiresApi(18)
@TargetApi(18)
class ViewGroupCompatJellybeanMR2 {
    ViewGroupCompatJellybeanMR2() {
    }

    public static int getLayoutMode(ViewGroup group) {
        return group.getLayoutMode();
    }

    public static void setLayoutMode(ViewGroup group, int mode) {
        group.setLayoutMode(mode);
    }
}
