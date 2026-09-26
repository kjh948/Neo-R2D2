"""Dump tokenizer facts from a LightNav GGUF: custom token presence, types, eos."""
import sys

import numpy as np
from gguf import GGUFReader

path = sys.argv[1] if len(sys.argv) > 1 else "models/LightNav-0.Q4_K_M.gguf"
r = GGUFReader(path)
f = r.fields


def contents(k):
    v = f[k]
    c = v.contents
    return c() if callable(c) else c


toks = [t.decode() if isinstance(t, bytes) else str(t) for t in contents("tokenizer.ggml.tokens")]
act = [t for t in toks if t.startswith("<act_")]
print("vocab:", len(toks))
print("act:", len(act), "sample:", act[:3])
print("apos:", sum(t.startswith("<apos_") for t in toks),
      " opos:", sum(t.startswith("<opos_") for t in toks),
      " tpos:", sum(t.startswith("<tpos_") for t in toks),
      " pos:", sum(t.startswith("<pos_") for t in toks))
if "tokenizer.ggml.eos_token_id" in f:
    eos = contents("tokenizer.ggml.eos_token_id")
    print("eos id:", eos, "->", toks[int(eos)])
if "tokenizer.ggml.token_type" in f:
    types = np.array(contents("tokenizer.ggml.token_type"))
    if act:
        print("token_type of", act[0], "=", int(types[toks.index(act[0])]))
    im_end = "<|im_end|>"
    if im_end in toks:
        print("token_type of im_end =", int(types[toks.index(im_end)]))
print("chat_template present:", "tokenizer.chat_template" in f)
pre = contents("tokenizer.ggml.pre") if "tokenizer.ggml.pre" in f else None
print("tokenizer.ggml.pre:", pre)
