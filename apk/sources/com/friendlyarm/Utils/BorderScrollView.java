package com.friendlyarm.Utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ScrollView;

/* JADX INFO: loaded from: classes.dex */
public class BorderScrollView extends ScrollView {
    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint();
        paint.setColor(-7829368);
        canvas.drawLine(0.0f, 0.0f, getWidth() - 1, 0.0f, paint);
        canvas.drawLine(0.0f, 0.0f, 0.0f, getHeight() - 1, paint);
        canvas.drawLine(getWidth() - 1, 0.0f, getWidth() - 1, getHeight() - 1, paint);
        canvas.drawLine(0.0f, getHeight() - 1, getWidth() - 1, getHeight() - 1, paint);
    }

    public BorderScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
