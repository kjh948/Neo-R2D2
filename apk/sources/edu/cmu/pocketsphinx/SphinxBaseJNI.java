package edu.cmu.pocketsphinx;

/* JADX INFO: loaded from: classes.dex */
public class SphinxBaseJNI {
    public static final native boolean Config_exists(long j, Config config, String str);

    public static final native boolean Config_getBoolean(long j, Config config, String str);

    public static final native double Config_getFloat(long j, Config config, String str);

    public static final native int Config_getInt(long j, Config config, String str);

    public static final native String Config_getString(long j, Config config, String str);

    public static final native void Config_setBoolean(long j, Config config, String str, boolean z);

    public static final native void Config_setFloat(long j, Config config, String str, double d);

    public static final native void Config_setInt(long j, Config config, String str, int i);

    public static final native void Config_setString(long j, Config config, String str, String str2);

    public static final native void Config_setStringExtra(long j, Config config, String str, String str2);

    public static final native int FrontEnd_outputSize(long j, FrontEnd frontEnd);

    public static final native int FrontEnd_processUtt(long j, FrontEnd frontEnd, String str, long j2, long j3);

    public static final native int FsgModel_addAlt(long j, FsgModel fsgModel, String str, String str2);

    public static final native int FsgModel_addSilence(long j, FsgModel fsgModel, String str, int i, float f);

    public static final native int FsgModel_nullTransAdd(long j, FsgModel fsgModel, int i, int i2, int i3);

    public static final native int FsgModel_tagTransAdd(long j, FsgModel fsgModel, int i, int i2, int i3, int i4);

    public static final native void FsgModel_transAdd(long j, FsgModel fsgModel, int i, int i2, int i3, int i4);

    public static final native int FsgModel_wordAdd(long j, FsgModel fsgModel, String str);

    public static final native int FsgModel_wordId(long j, FsgModel fsgModel, String str);

    public static final native void FsgModel_writefile(long j, FsgModel fsgModel, String str);

    public static final native boolean JsgfIterator_hasNext(long j, JsgfIterator jsgfIterator);

    public static final native long JsgfIterator_next(long j, JsgfIterator jsgfIterator);

    public static final native long JsgfRule_fromIter(long j);

    public static final native String JsgfRule_getName(long j, JsgfRule jsgfRule);

    public static final native boolean JsgfRule_isPublic(long j, JsgfRule jsgfRule);

    public static final native long Jsgf_buildFsg(long j, Jsgf jsgf, long j2, JsgfRule jsgfRule, long j3, LogMath logMath, float f);

    public static final native String Jsgf_getName(long j, Jsgf jsgf);

    public static final native long Jsgf_getRule(long j, Jsgf jsgf, String str);

    public static final native long Jsgf_iterator(long j, Jsgf jsgf);

    public static final native double LogMath_exp(long j, LogMath logMath, int i);

    public static final native boolean NGramModelSetIterator_hasNext(long j, NGramModelSetIterator nGramModelSetIterator);

    public static final native long NGramModelSetIterator_next(long j, NGramModelSetIterator nGramModelSetIterator);

    public static final native long NGramModelSet_add(long j, NGramModelSet nGramModelSet, long j2, NGramModel nGramModel, String str, float f, boolean z);

    public static final native int NGramModelSet_count(long j, NGramModelSet nGramModelSet);

    public static final native String NGramModelSet_current(long j, NGramModelSet nGramModelSet);

    public static final native long NGramModelSet_iterator(long j, NGramModelSet nGramModelSet);

    public static final native long NGramModelSet_lookup(long j, NGramModelSet nGramModelSet, String str);

    public static final native long NGramModelSet_select(long j, NGramModelSet nGramModelSet, String str);

    public static final native int NGramModel_addWord(long j, NGramModel nGramModel, String str, float f);

    public static final native void NGramModel_casefold(long j, NGramModel nGramModel, int i);

    public static final native long NGramModel_fromIter(long j);

    public static final native int NGramModel_prob(long j, NGramModel nGramModel, String[] strArr);

    public static final native int NGramModel_size(long j, NGramModel nGramModel);

    public static final native int NGramModel_strToType(long j, NGramModel nGramModel, String str);

    public static final native String NGramModel_typeToStr(long j, NGramModel nGramModel, int i);

    public static final native void NGramModel_write(long j, NGramModel nGramModel, String str, int i);

    public static final native void delete_Config(long j);

    public static final native void delete_Feature(long j);

    public static final native void delete_FrontEnd(long j);

    public static final native void delete_FsgModel(long j);

    public static final native void delete_Jsgf(long j);

    public static final native void delete_JsgfIterator(long j);

    public static final native void delete_JsgfRule(long j);

    public static final native void delete_LogMath(long j);

    public static final native void delete_NGramModel(long j);

    public static final native void delete_NGramModelSet(long j);

    public static final native void delete_NGramModelSetIterator(long j);

    public static final native long new_FrontEnd();

    public static final native long new_FsgModel__SWIG_0(String str, long j, LogMath logMath, float f, int i);

    public static final native long new_FsgModel__SWIG_1(String str, long j, LogMath logMath, float f);

    public static final native long new_Jsgf(String str);

    public static final native long new_JsgfIterator(long j);

    public static final native long new_JsgfRule();

    public static final native long new_LogMath();

    public static final native long new_NGramModelSet(long j, Config config, long j2, LogMath logMath, String str);

    public static final native long new_NGramModelSetIterator(long j);

    public static final native long new_NGramModel__SWIG_0(String str);

    public static final native long new_NGramModel__SWIG_1(long j, Config config, long j2, LogMath logMath, String str);

    public static final native long new_feature();
}
