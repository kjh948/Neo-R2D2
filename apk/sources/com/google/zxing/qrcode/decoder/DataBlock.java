package com.google.zxing.qrcode.decoder;

import com.google.zxing.qrcode.decoder.Version;

/* JADX INFO: loaded from: classes.dex */
final class DataBlock {
    private final byte[] codewords;
    private final int numDataCodewords;

    private DataBlock(int numDataCodewords, byte[] codewords) {
        this.numDataCodewords = numDataCodewords;
        this.codewords = codewords;
    }

    static DataBlock[] getDataBlocks(byte[] rawCodewords, Version version, ErrorCorrectionLevel ecLevel) {
        if (rawCodewords.length != version.getTotalCodewords()) {
            throw new IllegalArgumentException();
        }
        Version.ECBlocks ecBlocks = version.getECBlocksForLevel(ecLevel);
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
                int numBlockCodewords = ecBlocks.getECCodewordsPerBlock() + numDataCodewords;
                result[numResultBlocks] = new DataBlock(numDataCodewords, new byte[numBlockCodewords]);
                i++;
                numResultBlocks++;
            }
        }
        int shorterBlocksTotalCodewords = result[0].codewords.length;
        int longerBlocksStartAt = result.length - 1;
        while (longerBlocksStartAt >= 0) {
            int numCodewords = result[longerBlocksStartAt].codewords.length;
            if (numCodewords == shorterBlocksTotalCodewords) {
                break;
            }
            longerBlocksStartAt--;
        }
        int longerBlocksStartAt2 = longerBlocksStartAt + 1;
        int shorterBlocksNumDataCodewords = shorterBlocksTotalCodewords - ecBlocks.getECCodewordsPerBlock();
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
        int j2 = longerBlocksStartAt2;
        int rawCodewordsOffset3 = rawCodewordsOffset;
        while (j2 < numResultBlocks) {
            result[j2].codewords[shorterBlocksNumDataCodewords] = rawCodewords[rawCodewordsOffset3];
            j2++;
            rawCodewordsOffset3++;
        }
        int max = result[0].codewords.length;
        int i3 = shorterBlocksNumDataCodewords;
        int rawCodewordsOffset4 = rawCodewordsOffset3;
        while (i3 < max) {
            int j3 = 0;
            int rawCodewordsOffset5 = rawCodewordsOffset4;
            while (j3 < numResultBlocks) {
                int iOffset = j3 < longerBlocksStartAt2 ? i3 : i3 + 1;
                result[j3].codewords[iOffset] = rawCodewords[rawCodewordsOffset5];
                j3++;
                rawCodewordsOffset5++;
            }
            i3++;
            rawCodewordsOffset4 = rawCodewordsOffset5;
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
