package android.support.v4.content.res;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.annotation.RequiresApi;

/* JADX INFO: loaded from: classes.dex */
@RequiresApi(15)
@TargetApi(15)
class ResourcesCompatIcsMr1 {
    ResourcesCompatIcsMr1() {
    }

    public static Drawable getDrawableForDensity(Resources res, int id, int density) throws Resources.NotFoundException {
        return res.getDrawableForDensity(id, density);
    }
}
