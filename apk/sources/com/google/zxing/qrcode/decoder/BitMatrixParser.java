package com.google.zxing.qrcode.decoder;

import com.google.zxing.FormatException;
import com.google.zxing.common.BitMatrix;

/* JADX INFO: loaded from: classes.dex */
final class BitMatrixParser {
    private final BitMatrix bitMatrix;
    private boolean mirror;
    private FormatInformation parsedFormatInfo;
    private Version parsedVersion;

    BitMatrixParser(BitMatrix bitMatrix) throws FormatException {
        int dimension = bitMatrix.getHeight();
        if (dimension < 21 || (dimension & 3) != 1) {
            throw FormatException.getFormatInstance();
        }
        this.bitMatrix = bitMatrix;
    }

    FormatInformation readFormatInformation() throws FormatException {
        if (this.parsedFormatInfo != null) {
            return this.parsedFormatInfo;
        }
        int formatInfoBits1 = 0;
        for (int i = 0; i < 6; i++) {
            formatInfoBits1 = copyBit(i, 8, formatInfoBits1);
        }
        int formatInfoBits12 = copyBit(8, 7, copyBit(8, 8, copyBit(7, 8, formatInfoBits1)));
        for (int j = 5; j >= 0; j--) {
            formatInfoBits12 = copyBit(8, j, formatInfoBits12);
        }
        int dimension = this.bitMatrix.getHeight();
        int formatInfoBits2 = 0;
        int jMin = dimension - 7;
        for (int j2 = dimension - 1; j2 >= jMin; j2--) {
            formatInfoBits2 = copyBit(8, j2, formatInfoBits2);
        }
        for (int i2 = dimension - 8; i2 < dimension; i2++) {
            formatInfoBits2 = copyBit(i2, 8, formatInfoBits2);
        }
        this.parsedFormatInfo = FormatInformation.decodeFormatInformation(formatInfoBits12, formatInfoBits2);
        if (this.parsedFormatInfo != null) {
            return this.parsedFormatInfo;
        }
        throw FormatException.getFormatInstance();
    }

    Version readVersion() throws FormatException {
        if (this.parsedVersion != null) {
            return this.parsedVersion;
        }
        int dimension = this.bitMatrix.getHeight();
        int provisionalVersion = (dimension - 17) / 4;
        if (provisionalVersion <= 6) {
            return Version.getVersionForNumber(provisionalVersion);
        }
        int versionBits = 0;
        int ijMin = dimension - 11;
        for (int j = 5; j >= 0; j--) {
            for (int i = dimension - 9; i >= ijMin; i--) {
                versionBits = copyBit(i, j, versionBits);
            }
        }
        Version theParsedVersion = Version.decodeVersionInformation(versionBits);
        if (theParsedVersion != null && theParsedVersion.getDimensionForVersion() == dimension) {
            this.parsedVersion = theParsedVersion;
            return theParsedVersion;
        }
        int versionBits2 = 0;
        for (int i2 = 5; i2 >= 0; i2--) {
            for (int j2 = dimension - 9; j2 >= ijMin; j2--) {
                versionBits2 = copyBit(i2, j2, versionBits2);
            }
        }
        Version theParsedVersion2 = Version.decodeVersionInformation(versionBits2);
        if (theParsedVersion2 != null && theParsedVersion2.getDimensionForVersion() == dimension) {
            this.parsedVersion = theParsedVersion2;
            return theParsedVersion2;
        }
        throw FormatException.getFormatInstance();
    }

    private int copyBit(int i, int j, int versionBits) {
        boolean bit = this.mirror ? this.bitMatrix.get(j, i) : this.bitMatrix.get(i, j);
        return bit ? (versionBits << 1) | 1 : versionBits << 1;
    }

    /* JADX WARN: Removed duplicated region for block: B:31:0x00a0 A[PHI: r2 r5
      0x00a0: PHI (r2v4 'bitsRead' int) = (r2v3 'bitsRead' int), (r2v6 'bitsRead' int) binds: [B:15:0x005a, B:20:0x0078] A[DONT_GENERATE, DONT_INLINE]
      0x00a0: PHI (r5v4 'currentByte' int) = (r5v3 'currentByte' int), (r5v7 'currentByte' int) binds: [B:15:0x005a, B:20:0x0078] A[DONT_GENERATE, DONT_INLINE]] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    byte[] readCodewords() throws com.google.zxing.FormatException {
        /*
            r19 = this;
            com.google.zxing.qrcode.decoder.FormatInformation r8 = r19.readFormatInformation()
            com.google.zxing.qrcode.decoder.Version r16 = r19.readVersion()
            byte r17 = r8.getDataMask()
            com.google.zxing.qrcode.decoder.DataMask r6 = com.google.zxing.qrcode.decoder.DataMask.forReference(r17)
            r0 = r19
            com.google.zxing.common.BitMatrix r0 = r0.bitMatrix
            r17 = r0
            int r7 = r17.getHeight()
            r0 = r19
            com.google.zxing.common.BitMatrix r0 = r0.bitMatrix
            r17 = r0
            r0 = r17
            r6.unmaskBitMatrix(r0, r7)
            com.google.zxing.common.BitMatrix r9 = r16.buildFunctionPattern()
            r12 = 1
            int r17 = r16.getTotalCodewords()
            r0 = r17
            byte[] r13 = new byte[r0]
            r14 = 0
            r5 = 0
            r2 = 0
            int r11 = r7 + (-1)
        L37:
            if (r11 <= 0) goto L92
            r17 = 6
            r0 = r17
            if (r11 != r0) goto L41
            int r11 = r11 + (-1)
        L41:
            r4 = 0
        L42:
            if (r4 >= r7) goto L8d
            if (r12 == 0) goto L87
            int r17 = r7 + (-1)
            int r10 = r17 - r4
        L4a:
            r3 = 0
            r15 = r14
        L4c:
            r17 = 2
            r0 = r17
            if (r3 >= r0) goto L89
            int r17 = r11 - r3
            r0 = r17
            boolean r17 = r9.get(r0, r10)
            if (r17 != 0) goto La0
            int r2 = r2 + 1
            int r5 = r5 << 1
            r0 = r19
            com.google.zxing.common.BitMatrix r0 = r0.bitMatrix
            r17 = r0
            int r18 = r11 - r3
            r0 = r17
            r1 = r18
            boolean r17 = r0.get(r1, r10)
            if (r17 == 0) goto L74
            r5 = r5 | 1
        L74:
            r17 = 8
            r0 = r17
            if (r2 != r0) goto La0
            int r14 = r15 + 1
            byte r0 = (byte) r5
            r17 = r0
            r13[r15] = r17
            r2 = 0
            r5 = 0
        L83:
            int r3 = r3 + 1
            r15 = r14
            goto L4c
        L87:
            r10 = r4
            goto L4a
        L89:
            int r4 = r4 + 1
            r14 = r15
            goto L42
        L8d:
            r12 = r12 ^ 1
            int r11 = r11 + (-2)
            goto L37
        L92:
            int r17 = r16.getTotalCodewords()
            r0 = r17
            if (r14 == r0) goto L9f
            com.google.zxing.FormatException r17 = com.google.zxing.FormatException.getFormatInstance()
            throw r17
        L9f:
            return r13
        La0:
            r14 = r15
            goto L83
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.zxing.qrcode.decoder.BitMatrixParser.readCodewords():byte[]");
    }

    void remask() {
        if (this.parsedFormatInfo != null) {
            DataMask dataMask = DataMask.forReference(this.parsedFormatInfo.getDataMask());
            int dimension = this.bitMatrix.getHeight();
            dataMask.unmaskBitMatrix(this.bitMatrix, dimension);
        }
    }

    void setMirror(boolean mirror) {
        this.parsedVersion = null;
        this.parsedFormatInfo = null;
        this.mirror = mirror;
    }

    void mirror() {
        for (int x = 0; x < this.bitMatrix.getWidth(); x++) {
            for (int y = x + 1; y < this.bitMatrix.getHeight(); y++) {
                if (this.bitMatrix.get(x, y) != this.bitMatrix.get(y, x)) {
                    this.bitMatrix.flip(y, x);
                    this.bitMatrix.flip(x, y);
                }
            }
        }
    }
}
