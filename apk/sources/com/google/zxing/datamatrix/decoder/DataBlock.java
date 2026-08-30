package com.google.zxing.datamatrix.decoder;

import com.google.zxing.datamatrix.decoder.Version;

/* JADX INFO: loaded from: classes.dex */
final class DataBlock {
    private final byte[] codewords;
    private final int numDataCodewords;

    private DataBlock(int numDataCodewords, byte[] codewords) {
        this.numDataCodewords = numDataCodewords;
        this.codewords = codewords;
    }

    static DataBlock[] getDataBlocks(byte[] rawCodewords, Version version) {
        Version.ECBlocks ecBlocks = version.getECBlocks();
        int totalBlocks = 0;
        Version.ECB[] ecBlockArray = ecBlocks.getECBlocks();
        for (Version.ECB ecb : ecBlockArray) {
            totalBlocks += ecb.getCount();
        }
        DataBlock[] result = new DataBlock[totalBlocks];
        int numResultBlocks = 0;
        for (Version.ECB ecBlock : ecBlockArray) {
            int i = 0;
            while (i < ecBlock.getCount()) {
                int numDataCodewords = ecBlock.getDataCodewords();
                int numBlockCodewords = ecBlocks.getECCodewords() + numDataCodewords;
                result[numResultBlocks] = new DataBlock(numDataCodewords, new byte[numBlockCodewords]);
                i++;
                numResultBlocks++;
            }
        }
        int longerBlocksTotalCodewords = result[0].codewords.length;
        int longerBlocksNumDataCodewords = longerBlocksTotalCodewords - ecBlocks.getECCodewords();
        int shorterBlocksNumDataCodewords = longerBlocksNumDataCodewords - 1;
        int rawCodewordsOffset = 0;
        int i2 = 0;
        while (i2 < shorterBlocksNumDataCodewords) {
            int j = 0;
            int rawCodewordsOffset2 = rawCodewordsOffset;
            while (j < numResultBlocks) {
                result[j].codewords[i2] = rawCodewords[rawCodewordsOffset2];
                j++;
                rawCodewordsOffset2++;
            }
            i2++;
            rawCodewordsOffset = rawCodewordsOffset2;
        }
        boolean specialVersion = version.getVersionNumber() == 24;
        int numLongerBlocks = specialVersion ? 8 : numResultBlocks;
        int j2 = 0;
        int rawCodewordsOffset3 = rawCodewordsOffset;
        while (j2 < numLongerBlocks) {
            result[j2].codewords[longerBlocksNumDataCodewords - 1] = rawCodewords[rawCodewordsOffset3];
            j2++;
            rawCodewordsOffset3++;
        }
        int max = result[0].codewords.length;
        int i3 = longerBlocksNumDataCodewords;
        int rawCodewordsOffset4 = rawCodewordsOffset3;
        while (i3 < max) {
            int j3 = 0;
            int rawCodewordsOffset5 = rawCodewordsOffset4;
            while (j3 < numResultBlocks) {
                int jOffset = specialVersion ? (j3 + 8) % numResultBlocks : j3;
                int iOffset = (!specialVersion || jOffset <= 7) ? i3 : i3 - 1;
                result[jOffset].codewords[iOffset] = rawCodewords[rawCodewordsOffset5];
                j3++;
                rawCodewordsOffset5++;
            }
            i3++;
            rawCodewordsOffset4 = rawCodewordsOffset5;
        }
        if (rawCodewordsOffset4 != rawCodewords.length) {
            throw new IllegalArgumentException();
        }
        return result;
    }

    int getNumDataCodewords() {
        return this.numDataCodewords;
    }

    byte[] getCodewords() {
        return this.codewords;
    }
}
