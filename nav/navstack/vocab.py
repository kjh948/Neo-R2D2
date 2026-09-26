"""GGUF tokenizer vocabulary access for llama.cpp navstack.

Why ids instead of text: LightNav's action/pointing tokens (``<act_l*>``,
``<apos_*>``, ``<opos_*>``) are CONTROL-type tokens in the GGUF metadata
(token_type=3). llama-server's chat endpoint detokenizes them to empty
strings (``skip_special_tokens=false`` does not surface them either), so the
model's entire output vanishes from ``message.content``. The
``logprobs.content[*].id`` field still carries the true token ids -- we map
them back through the GGUF's own vocabulary and reconstruct the exact text
the model generated.
"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path


def load_vocab(gguf_path: Path) -> list[str]:
    """Read tokenizer.ggml.tokens straight from the GGUF header (no tensors)."""
    from gguf import GGUFReader

    reader = GGUFReader(str(gguf_path))  # metadata is read without tensor data
    field = reader.fields["tokenizer.ggml.tokens"]
    parts = field.contents
    parts = parts() if callable(parts) else parts
    return [p.decode() if isinstance(p, bytes) else str(p) for p in parts]


@lru_cache(maxsize=4)
def vocab_for(gguf_path_str: str) -> tuple[str, ...]:
    return tuple(load_vocab(Path(gguf_path_str)))
