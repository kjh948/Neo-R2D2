package android.support.v4.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.support.annotation.RequiresApi;
import android.text.method.SingleLineTransformationMethod;
import android.view.View;
import android.widget.TextView;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
@RequiresApi(14)
@TargetApi(14)
class PagerTitleStripIcs {
    PagerTitleStripIcs() {
    }

    public static void setSingleLineAllCaps(TextView text) {
        text.setTransformationMethod(new SingleLineAllCapsTransform(text.getContext()));
    }

    private static class SingleLineAllCapsTransform extends SingleLineTransformationMethod {
        private static final String TAG = "SingleLineAllCapsTransform";
        private Locale mLocale;

        public SingleLineAllCapsTransform(Context context) {
            this.mLocale = context.getResources().getConfiguration().locale;
        }

        @Override // android.text.method.ReplacementTransformationMethod, android.text.method.TransformationMethod
        public CharSequence getTransformation(CharSequence source, View view) {
            CharSequence source2 = super.getTransformation(source, view);
            if (source2 != null) {
                return source2.toString().toUpperCase(this.mLocale);
            }
            return null;
        }
    }
}
