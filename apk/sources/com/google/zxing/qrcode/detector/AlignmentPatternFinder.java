package com.google.zxing.qrcode.detector;

import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.BitMatrix;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
final class AlignmentPatternFinder {
    private final int height;
    private final BitMatrix image;
    private final float moduleSize;
    private final ResultPointCallback resultPointCallback;
    private final int startX;
    private final int startY;
    private final int width;
    private final List<AlignmentPattern> possibleCenters = new ArrayList(5);
    private final int[] crossCheckStateCount = new int[3];

    AlignmentPatternFinder(BitMatrix image, int startX, int startY, int width, int height, float moduleSize, ResultPointCallback resultPointCallback) {
        this.image = image;
        this.startX = startX;
        this.startY = startY;
        this.width = width;
        this.height = height;
        this.moduleSize = moduleSize;
        this.resultPointCallback = resultPointCallback;
    }

    AlignmentPattern find() throws NotFoundException {
        AlignmentPattern confirmed;
        AlignmentPattern confirmed2;
        int startX = this.startX;
        int height = this.height;
        int maxJ = startX + this.width;
        int middleI = this.startY + (height / 2);
        int[] stateCount = new int[3];
        for (int iGen = 0; iGen < height; iGen++) {
            int i = middleI + ((iGen & 1) == 0 ? (iGen + 1) / 2 : -((iGen + 1) / 2));
            stateCount[0] = 0;
            stateCount[1] = 0;
            stateCount[2] = 0;
            int j = startX;
            while (j < maxJ && !this.image.get(j, i)) {
                j++;
            }
            int currentState = 0;
            while (j < maxJ) {
                if (this.image.get(j, i)) {
                    if (currentState == 1) {
                        stateCount[currentState] = stateCount[currentState] + 1;
                    } else if (currentState == 2) {
                        if (!foundPatternCross(stateCount) || (confirmed2 = handlePossibleCenter(stateCount, i, j)) == null) {
                            stateCount[0] = stateCount[2];
                            stateCount[1] = 1;
                            stateCount[2] = 0;
                            currentState = 1;
                        } else {
                            return confirmed2;
                        }
                    } else {
                        currentState++;
                        stateCount[currentState] = stateCount[currentState] + 1;
                    }
                } else {
                    if (currentState == 1) {
                        currentState++;
                    }
                    stateCount[currentState] = stateCount[currentState] + 1;
                }
                j++;
            }
            if (foundPatternCross(stateCount) && (confirmed = handlePossibleCenter(stateCount, i, maxJ)) != null) {
                return confirmed;
            }
        }
        if (!this.possibleCenters.isEmpty()) {
            return this.possibleCenters.get(0);
        }
        throw NotFoundException.getNotFoundInstance();
    }

    private static float centerFromEnd(int[] stateCount, int end) {
        return (end - stateCount[2]) - (stateCount[1] / 2.0f);
    }

    private boolean foundPatternCross(int[] stateCount) {
        float moduleSize = this.moduleSize;
        float maxVariance = moduleSize / 2.0f;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(moduleSize - stateCount[i]) >= maxVariance) {
                return false;
            }
        }
        return true;
    }

    private float crossCheckVertical(int startI, int centerJ, int maxCount, int originalStateCountTotal) {
        BitMatrix image = this.image;
        int maxI = image.getHeight();
        int[] stateCount = this.crossCheckStateCount;
        stateCount[0] = 0;
        stateCount[1] = 0;
        stateCount[2] = 0;
        int i = startI;
        while (i >= 0 && image.get(centerJ, i) && stateCount[1] <= maxCount) {
            stateCount[1] = stateCount[1] + 1;
            i--;
        }
        if (i < 0 || stateCount[1] > maxCount) {
            return Float.NaN;
        }
        while (i >= 0 && !image.get(centerJ, i) && stateCount[0] <= maxCount) {
            stateCount[0] = stateCount[0] + 1;
            i--;
        }
        if (stateCount[0] > maxCount) {
            return Float.NaN;
        }
        int i2 = startI + 1;
        while (i2 < maxI && image.get(centerJ, i2) && stateCount[1] <= maxCount) {
            stateCount[1] = stateCount[1] + 1;
            i2++;
        }
        if (i2 == maxI || stateCount[1] > maxCount) {
            return Float.NaN;
        }
        while (i2 < maxI && !image.get(centerJ, i2) && stateCount[2] <= maxCount) {
            stateCount[2] = stateCount[2] + 1;
            i2++;
        }
        if (stateCount[2] > maxCount) {
            return Float.NaN;
        }
        int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2];
        if (Math.abs(stateCountTotal - originalStateCountTotal) * 5 >= originalStateCountTotal * 2 || !foundPatternCross(stateCount)) {
            return Float.NaN;
        }
        return centerFromEnd(stateCount, i2);
    }

    private AlignmentPattern handlePossibleCenter(int[] stateCount, int i, int j) {
        int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2];
        float centerJ = centerFromEnd(stateCount, j);
        float centerI = crossCheckVertical(i, (int) centerJ, stateCount[1] * 2, stateCountTotal);
        if (!Float.isNaN(centerI)) {
            float estimatedModuleSize = ((stateCount[0] + stateCount[1]) + stateCount[2]) / 3.0f;
            for (AlignmentPattern center : this.possibleCenters) {
                if (center.aboutEquals(estimatedModuleSize, centerI, centerJ)) {
                    return center.combineEstimate(centerI, centerJ, estimatedModuleSize);
                }
            }
            AlignmentPattern point = new AlignmentPattern(centerJ, centerI, estimatedModuleSize);
            this.possibleCenters.add(point);
            if (this.resultPointCallback != null) {
                this.resultPointCallback.foundPossibleResultPoint(point);
            }
        }
        return null;
    }
}
