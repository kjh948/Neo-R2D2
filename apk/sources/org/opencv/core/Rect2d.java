package org.opencv.core;

/* JADX INFO: loaded from: classes.dex */
public class Rect2d {
    public double height;
    public double width;
    public double x;
    public double y;

    public Rect2d(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Rect2d() {
        this(0.0d, 0.0d, 0.0d, 0.0d);
    }

    public Rect2d(Point p1, Point p2) {
        this.x = p1.x < p2.x ? p1.x : p2.x;
        this.y = p1.y < p2.y ? p1.y : p2.y;
        this.width = (p1.x > p2.x ? p1.x : p2.x) - this.x;
        this.height = (p1.y > p2.y ? p1.y : p2.y) - this.y;
    }

    public Rect2d(Point p, Size s) {
        this(p.x, p.y, s.width, s.height);
    }

    public Rect2d(double[] vals) {
        set(vals);
    }

    public void set(double[] vals) {
        if (vals != null) {
            this.x = vals.length > 0 ? vals[0] : 0.0d;
            this.y = vals.length > 1 ? vals[1] : 0.0d;
            this.width = vals.length > 2 ? vals[2] : 0.0d;
            this.height = vals.length > 3 ? vals[3] : 0.0d;
            return;
        }
        this.x = 0.0d;
        this.y = 0.0d;
        this.width = 0.0d;
        this.height = 0.0d;
    }

    public Rect2d clone() {
        return new Rect2d(this.x, this.y, this.width, this.height);
    }

    public Point tl() {
        return new Point(this.x, this.y);
    }

    public Point br() {
        return new Point(this.x + this.width, this.y + this.height);
    }

    public Size size() {
        return new Size(this.width, this.height);
    }

    public double area() {
        return this.width * this.height;
    }

    public boolean empty() {
        return this.width <= 0.0d || this.height <= 0.0d;
    }

    public boolean contains(Point p) {
        return this.x <= p.x && p.x < this.x + this.width && this.y <= p.y && p.y < this.y + this.height;
    }

    public int hashCode() {
        long temp = Double.doubleToLongBits(this.height);
        int result = ((int) ((temp >>> 32) ^ temp)) + 31;
        long temp2 = Double.doubleToLongBits(this.width);
        int result2 = (result * 31) + ((int) ((temp2 >>> 32) ^ temp2));
        long temp3 = Double.doubleToLongBits(this.x);
        int result3 = (result2 * 31) + ((int) ((temp3 >>> 32) ^ temp3));
        long temp4 = Double.doubleToLongBits(this.y);
        return (result3 * 31) + ((int) ((temp4 >>> 32) ^ temp4));
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Rect2d)) {
            return false;
        }
        Rect2d it = (Rect2d) obj;
        return this.x == it.x && this.y == it.y && this.width == it.width && this.height == it.height;
    }

    public String toString() {
        return "{" + this.x + ", " + this.y + ", " + this.width + "x" + this.height + "}";
    }
}
