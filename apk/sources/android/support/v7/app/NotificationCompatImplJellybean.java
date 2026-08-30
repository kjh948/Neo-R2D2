package android.support.v7.app;

import android.annotation.TargetApi;
import android.app.Notification;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationBuilderWithBuilderAccessor;

/* JADX INFO: loaded from: classes.dex */
@RequiresApi(16)
@TargetApi(16)
class NotificationCompatImplJellybean {
    NotificationCompatImplJellybean() {
    }

    public static void addBigTextStyle(NotificationBuilderWithBuilderAccessor b, CharSequence bigText) {
        Notification.BigTextStyle bigTextStyle = new Notification.BigTextStyle(b.getBuilder());
        bigTextStyle.bigText(bigText);
    }
}
