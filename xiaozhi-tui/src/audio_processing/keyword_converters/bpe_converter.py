# -*- coding: utf-8 -*-
"""BPE converter for English wake words.

Output format required by sherpa-onnx KeywordSpotter (one keyword per line):

    <token> <token> ... @LABEL

Two rules that the C++ encoder enforces and that used to break English wake words:

1. Every space-separated field before ``@`` must exist verbatim in ``tokens.txt``.
   A digit such as ``2`` is not part of the 500-unit gigaspeech BPE vocabulary and
   would be emitted as ``<unk>`` (encode failure), so digits are spelled out.
2. ``@LABEL`` must be a *single* field. sherpa splits the whole line on spaces, so a
   label like ``@HEY ARE TWO`` makes it look up "ARE"/"TWO" as tokens and the encode
   fails. Labels are therefore sanitized to one word (spaces/punctuation -> ``_``).
"""

import re
from pathlib import Path
from typing import Dict, List, Optional

from .base import KeywordConverter

# Digits are not in the English BPE vocabulary; spell them out.
DIGIT_WORDS: Dict[str, str] = {
    "0": "zero",
    "1": "one",
    "2": "two",
    "3": "three",
    "4": "four",
    "5": "five",
    "6": "six",
    "7": "seven",
    "8": "eight",
    "9": "nine",
}

# Label separator used when a wake word has several spoken words.
LABEL_SEP = "_"

# Pieces longer than this are unlikely to be in a 500-unit vocabulary.
_MAX_PIECE_LEN = 20


class BpeConverter(KeywordConverter):
    def __init__(self, tokens_path: Optional[str] = None):
        self._tokens_path = tokens_path
        self._token_to_id: Optional[Dict[str, int]] = None

    def _get_tokens_path(self) -> Path:
        if self._tokens_path:
            return Path(self._tokens_path)

        # Default path: models/en/tokens.txt
        from src.utils.resource_finder import get_app_root
        return get_app_root() / "models" / "en" / "tokens.txt"

    def _load_tokens(self):
        """Load tokens from tokens.txt file."""
        if self._token_to_id is not None:
            return

        tokens_path = self._get_tokens_path()
        if not tokens_path.exists():
            raise FileNotFoundError(f"BPE tokens file not found: {tokens_path}")

        self._token_to_id = {}

        with open(tokens_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                parts = line.split()
                if len(parts) >= 2:
                    token = parts[0]
                    token_id = int(parts[-1])
                    self._token_to_id[token] = token_id

    @property
    def language(self) -> str:
        return "en"

    @property
    def model_path(self) -> str:
        return "models/en"

    def can_convert(self, text: str) -> bool:
        chinese_pattern = re.compile(r"[一-鿿]")
        has_chinese = bool(chinese_pattern.search(text))
        has_letters = bool(re.search(r"[a-zA-Z]", text))
        return not has_chinese and has_letters

    def _greedy_tokenize(self, text: str) -> List[str]:
        tokens = []
        i = 0

        while i < len(text):
            matched = False

            max_len = min(_MAX_PIECE_LEN, len(text) - i)
            for length in range(max_len, 0, -1):
                substr = text[i:i + length]

                if substr in self._token_to_id:
                    tokens.append(substr)
                    i += length
                    matched = True
                    break

            if not matched:
                char = text[i]
                if char in self._token_to_id:
                    tokens.append(char)
                else:
                    tokens.append("<unk>")
                i += 1

        return tokens

    @staticmethod
    def _split_words(text: str) -> List[str]:
        """Split input into words, keeping letters and digits apart ("r2" -> R, 2)."""
        return [w for w in re.findall(r"[A-Za-z]+|[0-9]+", text)]

    @staticmethod
    def _make_label(text: str) -> str:
        """Build sherpa's single-field label: no spaces allowed after '@'."""
        cleaned = re.sub(r"[^A-Za-z0-9]+", LABEL_SEP, text.strip().upper())
        return cleaned.strip(LABEL_SEP) or "KEYWORD"

    def _spoken_words(self, spoken: str) -> List[str]:
        """Expand a spoken form into vocabulary-friendly words."""
        words: List[str] = []
        for word in self._split_words(spoken):
            if word.isdigit():
                words.extend(DIGIT_WORDS[d] for d in word)
            else:
                words.append(word)
        return words

    def _tokenize_word(self, word: str) -> List[str]:
        """Tokenize one word, preferring word-initial pieces (▁X).

        The model's vocabulary holds both ``▁HE`` (word start) and ``HE`` (mid-word),
        and matching is better with the word-start variant, so ▁ is kept when the
        greedy split produces one.
        """
        upper = word.upper()

        # A digit that survived expansion cannot be encoded; skip it instead of
        # emitting <unk>, which makes sherpa reject the whole keyword line.
        if upper.isdigit():
            return []

        tokens = [t for t in self._greedy_tokenize(f"▁{upper}") if t != "<unk>"]
        # A lone ▁ carries no sound; drop it and keep the bare piece.
        if tokens[:1] == ["▁"]:
            tokens = tokens[1:]
        return tokens

    def convert(self, text: str, spoken: Optional[str] = None) -> str:
        """Convert a wake word into a sherpa-onnx keyword line.

        Args:
            text: wake word as displayed / reported (e.g. "Hey R2")
            spoken: pronunciation to encode; defaults to ``text``
                    (e.g. "hey are two" for "Hey R2")

        Returns:
            one keyword line, e.g. "▁HE Y ▁ARE ▁TWO @HEY_R2"
        """
        self._load_tokens()

        words = self._spoken_words(spoken if spoken is not None else text)

        all_tokens: List[str] = []
        dropped: List[str] = []
        for word in words:
            tokens = self._tokenize_word(word)
            if not tokens:
                dropped.append(word)
            all_tokens.extend(tokens)

        if dropped:
            import logging

            logging.getLogger(__name__).warning(
                "BPE: skipped non-encodable word(s) %s for wake word %r",
                dropped,
                text,
            )

        bpe_str = " ".join(all_tokens)
        return f"{bpe_str} @{self._make_label(text)}"
