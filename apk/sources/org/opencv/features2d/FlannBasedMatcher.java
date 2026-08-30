package org.opencv.features2d;

/* JADX INFO: loaded from: classes.dex */
public class FlannBasedMatcher extends DescriptorMatcher {
    private static native long FlannBasedMatcher_0();

    private static native long create_0();

    private static native void delete(long j);

    protected FlannBasedMatcher(long addr) {
        super(addr);
    }

    public FlannBasedMatcher() {
        super(FlannBasedMatcher_0());
    }

    public static FlannBasedMatcher create() {
        FlannBasedMatcher retVal = new FlannBasedMatcher(create_0());
        return retVal;
    }

    @Override // org.opencv.features2d.DescriptorMatcher, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
