package android.support.v7.widget;

import android.R;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Shader;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.graphics.drawable.shapes.Shape;
import android.support.v4.graphics.drawable.DrawableWrapper;
import android.util.AttributeSet;
import android.widget.ProgressBar;

/* JADX INFO: loaded from: classes.dex */
class AppCompatProgressBarHelper {
    private static final int[] TINT_ATTRS = {R.attr.indeterminateDrawable, R.attr.progressDrawable};
    private Bitmap mSampleTile;
    private final ProgressBar mView;

    AppCompatProgressBarHelper(ProgressBar view) {
        this.mView = view;
    }

    void loadFromAttributes(AttributeSet attrs, int defStyleAttr) {
        TintTypedArray a = TintTypedArray.obtainStyledAttributes(this.mView.getContext(), attrs, TINT_ATTRS, defStyleAttr, 0);
        Drawable drawable = a.getDrawableIfKnown(0);
        if (drawable != null) {
            this.mView.setIndeterminateDrawable(tileifyIndeterminate(drawable));
        }
        Drawable drawable2 = a.getDrawableIfKnown(1);
        if (drawable2 != null) {
            this.mView.setProgressDrawable(tileify(drawable2, false));
        }
        a.recycle();
    }

    /* JADX WARN: Multi-variable type inference failed */
    private Drawable tileify(Drawable drawable, boolean z) {
        if (drawable instanceof DrawableWrapper) {
            Drawable wrappedDrawable = ((DrawableWrapper) drawable).getWrappedDrawable();
            if (wrappedDrawable != null) {
                ((DrawableWrapper) drawable).setWrappedDrawable(tileify(wrappedDrawable, z));
            }
        } else {
            if (drawable instanceof LayerDrawable) {
                LayerDrawable layerDrawable = (LayerDrawable) drawable;
                int numberOfLayers = layerDrawable.getNumberOfLayers();
                Drawable[] drawableArr = new Drawable[numberOfLayers];
                for (int i = 0; i < numberOfLayers; i++) {
                    int id = layerDrawable.getId(i);
                    drawableArr[i] = tileify(layerDrawable.getDrawable(i), id == 16908301 || id == 16908303);
                }
                LayerDrawable layerDrawable2 = new LayerDrawable(drawableArr);
                for (int i2 = 0; i2 < numberOfLayers; i2++) {
                    layerDrawable2.setId(i2, layerDrawable.getId(i2));
                }
                return layerDrawable2;
            }
            if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (this.mSampleTile == null) {
                    this.mSampleTile = bitmap;
                }
                ShapeDrawable shapeDrawable = new ShapeDrawable(getDrawableShape());
                shapeDrawable.getPaint().setShader(new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP));
                shapeDrawable.getPaint().setColorFilter(bitmapDrawable.getPaint().getColorFilter());
                Drawable clipDrawable = shapeDrawable;
                if (z) {
                    clipDrawable = new ClipDrawable(shapeDrawable, 3, 1);
                }
                return clipDrawable;
            }
        }
        return drawable;
    }

    private Drawable tileifyIndeterminate(Drawable drawable) {
        if (drawable instanceof AnimationDrawable) {
            AnimationDrawable background = (AnimationDrawable) drawable;
            int N = background.getNumberOfFrames();
            AnimationDrawable newBg = new AnimationDrawable();
            newBg.setOneShot(background.isOneShot());
            for (int i = 0; i < N; i++) {
                Drawable frame = tileify(background.getFrame(i), true);
                frame.setLevel(10000);
                newBg.addFrame(frame, background.getDuration(i));
            }
            newBg.setLevel(10000);
            return newBg;
        }
        return drawable;
    }

    private Shape getDrawableShape() {
        float[] roundedCorners = {5.0f, 5.0f, 5.0f, 5.0f, 5.0f, 5.0f, 5.0f, 5.0f};
        return new RoundRectShape(roundedCorners, null, null);
    }

    Bitmap getSampleTime() {
        return this.mSampleTile;
    }
}
