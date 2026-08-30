package edu.cmu.pocketsphinx;

/* JADX INFO: loaded from: classes.dex */
public class PocketSphinxJNI {
    public static final native void Decoder_addWord(long j, Decoder decoder, String str, String str2, int i);

    public static final native long Decoder_defaultConfig();

    public static final native void Decoder_endUtt(long j, Decoder decoder);

    public static final native long Decoder_fileConfig(String str);

    public static final native long Decoder_getConfig(long j, Decoder decoder);

    public static final native long Decoder_getFe(long j, Decoder decoder);

    public static final native long Decoder_getFeat(long j, Decoder decoder);

    public static final native long Decoder_getFsg(long j, Decoder decoder, String str);

    public static final native boolean Decoder_getInSpeech(long j, Decoder decoder);

    public static final native String Decoder_getKws(long j, Decoder decoder, String str);

    public static final native long Decoder_getLattice(long j, Decoder decoder);

    public static final native long Decoder_getLm(long j, Decoder decoder, String str);

    public static final native long Decoder_getLogmath(long j, Decoder decoder);

    public static final native short[] Decoder_getRawdata(long j, Decoder decoder);

    public static final native String Decoder_getSearch(long j, Decoder decoder);

    public static final native long Decoder_hyp(long j, Decoder decoder);

    public static final native void Decoder_loadDict(long j, Decoder decoder, String str, String str2, String str3);

    public static final native String Decoder_lookupWord(long j, Decoder decoder, String str);

    public static final native int Decoder_nFrames(long j, Decoder decoder);

    public static final native long Decoder_nbest(long j, Decoder decoder);

    public static final native int Decoder_processRaw(long j, Decoder decoder, short[] sArr, long j2, boolean z, boolean z2);

    public static final native void Decoder_reinit(long j, Decoder decoder, long j2, Config config);

    public static final native void Decoder_saveDict(long j, Decoder decoder, String str, String str2);

    public static final native long Decoder_seg(long j, Decoder decoder);

    public static final native void Decoder_setAllphoneFile(long j, Decoder decoder, String str, String str2);

    public static final native void Decoder_setFsg(long j, Decoder decoder, String str, long j2, FsgModel fsgModel);

    public static final native void Decoder_setJsgfFile(long j, Decoder decoder, String str, String str2);

    public static final native void Decoder_setJsgfString(long j, Decoder decoder, String str, String str2);

    public static final native void Decoder_setKeyphrase(long j, Decoder decoder, String str, String str2);

    public static final native void Decoder_setKws(long j, Decoder decoder, String str, String str2);

    public static final native void Decoder_setLm(long j, Decoder decoder, String str, long j2, NGramModel nGramModel);

    public static final native void Decoder_setLmFile(long j, Decoder decoder, String str, String str2);

    public static final native void Decoder_setRawdataSize(long j, Decoder decoder, long j2);

    public static final native void Decoder_setSearch(long j, Decoder decoder, String str);

    public static final native void Decoder_startStream(long j, Decoder decoder);

    public static final native void Decoder_startUtt(long j, Decoder decoder);

    public static final native void Decoder_unsetSearch(long j, Decoder decoder, String str);

    public static final native int Hypothesis_bestScore_get(long j, Hypothesis hypothesis);

    public static final native void Hypothesis_bestScore_set(long j, Hypothesis hypothesis, int i);

    public static final native String Hypothesis_hypstr_get(long j, Hypothesis hypothesis);

    public static final native void Hypothesis_hypstr_set(long j, Hypothesis hypothesis, String str);

    public static final native int Hypothesis_prob_get(long j, Hypothesis hypothesis);

    public static final native void Hypothesis_prob_set(long j, Hypothesis hypothesis, int i);

    public static final native void Lattice_write(long j, Lattice lattice, String str);

    public static final native void Lattice_writeHtk(long j, Lattice lattice, String str);

    public static final native boolean NBestIterator_hasNext(long j, NBestIterator nBestIterator);

    public static final native long NBestIterator_next(long j, NBestIterator nBestIterator);

    public static final native long NBestList_iterator(long j, NBestList nBestList);

    public static final native long NBest_fromIter(long j);

    public static final native long NBest_hyp(long j, NBest nBest);

    public static final native String NBest_hypstr_get(long j, NBest nBest);

    public static final native void NBest_hypstr_set(long j, NBest nBest, String str);

    public static final native int NBest_score_get(long j, NBest nBest);

    public static final native void NBest_score_set(long j, NBest nBest, int i);

    public static final native boolean SegmentIterator_hasNext(long j, SegmentIterator segmentIterator);

    public static final native long SegmentIterator_next(long j, SegmentIterator segmentIterator);

    public static final native long SegmentList_iterator(long j, SegmentList segmentList);

    public static final native int Segment_ascore_get(long j, Segment segment);

    public static final native void Segment_ascore_set(long j, Segment segment, int i);

    public static final native int Segment_endFrame_get(long j, Segment segment);

    public static final native void Segment_endFrame_set(long j, Segment segment, int i);

    public static final native long Segment_fromIter(long j);

    public static final native int Segment_lback_get(long j, Segment segment);

    public static final native void Segment_lback_set(long j, Segment segment, int i);

    public static final native int Segment_lscore_get(long j, Segment segment);

    public static final native void Segment_lscore_set(long j, Segment segment, int i);

    public static final native int Segment_prob_get(long j, Segment segment);

    public static final native void Segment_prob_set(long j, Segment segment, int i);

    public static final native int Segment_startFrame_get(long j, Segment segment);

    public static final native void Segment_startFrame_set(long j, Segment segment, int i);

    public static final native String Segment_word_get(long j, Segment segment);

    public static final native void Segment_word_set(long j, Segment segment, String str);

    public static final native void delete_Decoder(long j);

    public static final native void delete_Hypothesis(long j);

    public static final native void delete_Lattice(long j);

    public static final native void delete_NBest(long j);

    public static final native void delete_NBestIterator(long j);

    public static final native void delete_NBestList(long j);

    public static final native void delete_Segment(long j);

    public static final native void delete_SegmentIterator(long j);

    public static final native void delete_SegmentList(long j);

    public static final native long new_Decoder__SWIG_0();

    public static final native long new_Decoder__SWIG_1(long j, Config config);

    public static final native long new_Hypothesis(String str, int i, int i2);

    public static final native long new_Lattice__SWIG_0(String str);

    public static final native long new_Lattice__SWIG_1(long j, Decoder decoder, String str);

    public static final native long new_NBestIterator(long j);

    public static final native long new_SegmentIterator(long j);

    public static final native long new_nBest();

    public static final native long new_segment();
}
