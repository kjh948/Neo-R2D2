package android.support.v7.app;

import android.annotation.TargetApi;
import android.app.Notification;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationBuilderWithBuilderAccessor;

/* JADX INFO: loaded from: classes.dex */
@RequiresApi(24)
@TargetApi(24)
class NotificationCompatImpl24 {
    NotificationCompatImpl24() {
    }

    public static void addDecoratedCustomViewStyle(NotificationBuilderWithBuilderAccessor b) {
        Notification.Builder builder = b.getBuilder();
        builder.setStyle(new Notification.DecoratedCustomViewStyle());
    }

    public static void addDecoratedMediaCustomViewStyle(NotificationBuilderWithBuilderAccessor b) {
        Notification.Builder builder = b.getBuilder();
        builder.setStyle(new Notification.DecoratedMediaCustomViewStyle());
    }
}
