package com.koushikdutta.async.http.filter;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.Util;

/* JADX INFO: loaded from: classes.dex */
public class ChunkedInputFilter extends FilteredDataEmitter {
    static final /* synthetic */ boolean $assertionsDisabled;
    private int mChunkLength = 0;
    private int mChunkLengthRemaining = 0;
    private State mState = State.CHUNK_LEN;
    ByteBufferList pending = new ByteBufferList();

    private enum State {
        CHUNK_LEN,
        CHUNK_LEN_CR,
        CHUNK_LEN_CRLF,
        CHUNK,
        CHUNK_CR,
        CHUNK_CRLF,
        COMPLETE
    }

    static {
        $assertionsDisabled = !ChunkedInputFilter.class.desiredAssertionStatus();
    }

    private boolean checkByte(char b, char value) {
        if (b == value) {
            return true;
        }
        report(new ChunkedDataException(value + " was expected, got " + b));
        return false;
    }

    private boolean checkLF(char b) {
        return checkByte(b, '\n');
    }

    private boolean checkCR(char b) {
        return checkByte(b, '\r');
    }

    @Override // com.koushikdutta.async.DataEmitterBase
    protected void report(Exception e) {
        if (e == null && this.mState != State.COMPLETE) {
            e = new ChunkedDataException("chunked input ended before final chunk");
        }
        super.report(e);
    }

    @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.callback.DataCallback
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        while (bb.remaining() > 0) {
            try {
                switch (this.mState) {
                    case CHUNK_LEN:
                        char c = bb.getByteChar();
                        if (c == '\r') {
                            this.mState = State.CHUNK_LEN_CR;
                        } else {
                            this.mChunkLength *= 16;
                            if (c >= 'a' && c <= 'f') {
                                this.mChunkLength += (c - 'a') + 10;
                            } else if (c >= '0' && c <= '9') {
                                this.mChunkLength += c - '0';
                            } else {
                                if (c < 'A' || c > 'F') {
                                    report(new ChunkedDataException("invalid chunk length: " + c));
                                    return;
                                }
                                this.mChunkLength += (c - 'A') + 10;
                            }
                        }
                        this.mChunkLengthRemaining = this.mChunkLength;
                        break;
                    case CHUNK_LEN_CR:
                        if (checkLF(bb.getByteChar())) {
                            this.mState = State.CHUNK;
                        } else {
                            return;
                        }
                        break;
                    case CHUNK:
                        int remaining = bb.remaining();
                        int reading = Math.min(this.mChunkLengthRemaining, remaining);
                        this.mChunkLengthRemaining -= reading;
                        if (this.mChunkLengthRemaining == 0) {
                            this.mState = State.CHUNK_CR;
                        }
                        if (reading != 0) {
                            bb.get(this.pending, reading);
                            Util.emitAllData(this, this.pending);
                        }
                        break;
                    case CHUNK_CR:
                        if (checkCR(bb.getByteChar())) {
                            this.mState = State.CHUNK_CRLF;
                        } else {
                            return;
                        }
                        break;
                    case CHUNK_CRLF:
                        if (checkLF(bb.getByteChar())) {
                            if (this.mChunkLength > 0) {
                                this.mState = State.CHUNK_LEN;
                            } else {
                                this.mState = State.COMPLETE;
                                report(null);
                            }
                            this.mChunkLength = 0;
                        } else {
                            return;
                        }
                        break;
                    case COMPLETE:
                        if (!$assertionsDisabled) {
                            throw new AssertionError();
                        }
                        return;
                }
            } catch (Exception ex) {
                report(ex);
                return;
            }
        }
    }
}
