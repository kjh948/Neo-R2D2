package android.support.v4.view;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.support.annotation.RequiresApi;
import android.view.View;

/* JADX INFO: loaded from: classes.dex */
@RequiresApi(19)
@TargetApi(19)
class ViewPropertyAnimatorCompatKK {
    ViewPropertyAnimatorCompatKK() {
    }

    public static void setUpdateListener(final View view, final ViewPropertyAnimatorUpdateListener listener) {
        ValueAnimator.AnimatorUpdateListener wrapped = null;
        if (listener != null) {
            wrapped = new ValueAnimator.AnimatorUpdateListener() { // from class: android.support.v4.view.ViewPropertyAnimatorCompatKK.1
                @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    listener.onAnimationUpdate(view);
                }
            };
        }
        view.animate().setUpdateListener(wrapped);
    }
}
