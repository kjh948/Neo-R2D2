"""Build the chat payload for llama-server: byte-exact LightNav prompt split
into OpenAI content parts (text <-> image interleaving).

Trained layout (lightnav.processing): one temporal tubelet (2 frames) renders as
``<{t:.1f} seconds>`` + vision block; segments are oldest-first, tubelets of a
segment are space-separated, ``{videos}`` slots join with a space. llama.cpp mtmd
emits its own vision markers per image part, so we place the timestamp text
immediately before each tubelet's images and keep ALL surrounding text from the
template byte-identical (the template's ``<video>`` placeholders are where the
image parts go, and we drop the surrounding ``vision_start/vision_end`` spelling
since mtmd owns that).
"""

from __future__ import annotations

from typing import Any, Dict, List

from lightnav.prompts import (
    UNIFIED_TRAJ_PROMPT_TEMPLATE,
    build_video_block,
    to_rvq_prompt,
)


def tubelet_timestamp(
    pair_abs_ids: List[int], ref_abs: int, fps: float, relative: bool
) -> float:
    """Tubelet timestamp = mean of the two frames' seconds, absolute or
    time-before-current (lightnav.processing._calculate_timestamps)."""
    if relative:
        secs = [(ref_abs - i) / fps for i in pair_abs_ids]
    else:
        secs = [i / fps for i in pair_abs_ids]
    return (secs[0] + secs[1]) / 2.0


def build_prompt_text(num_segments: int, task_instruction: str, rvq: bool = True) -> str:
    """The full user-turn text WITH ``<video>`` placeholders inline (for tests/reference)."""
    tmpl = UNIFIED_TRAJ_PROMPT_TEMPLATE
    if rvq:
        tmpl = to_rvq_prompt(tmpl)
    filled = tmpl.replace("{videos}", build_video_block(num_segments))
    return filled.replace("{task}", task_instruction)


def split_template_rvq() -> tuple[str, str]:
    """(prefix up to and including the {videos} slot's leading text, suffix after it).

    The unified template is "...most recent: {videos}. Your assigned task is:
    <navigation_task>{task}</navigation_task>...". We cut at "{videos}" and drop
    the "<video> " repetitions: each placeholder is consumed by its segment's
    images; the ", "-style spacing around placeholders is preserved exactly.
    """
    tmpl = to_rvq_prompt(UNIFIED_TRAJ_PROMPT_TEMPLATE)
    head, sep, tail = tmpl.partition("{videos}")
    assert sep, "unified template must contain the {videos} slot"
    # tail starts with ". Your assigned..." -- the slot itself was only
    # placeholders, so the suffix keeps its leading period/space verbatim.
    return head, tail


def build_content_parts(
    segments: List[Dict[str, Any]],
    instruction: str,
) -> List[Dict[str, Any]]:
    """OpenAI content parts for one /v1/chat/completions user message.

    ``segments``: oldest-first list of
    ``{"tubelets": [(ts_seconds_float, [image_data_url, ...]), ...]}``
    """
    head, tail = split_template_rvq()
    parts: List[Dict[str, Any]] = [{"type": "text", "text": head}]
    for si, seg in enumerate(segments):
        if si > 0:
            # the {videos} slot joined placeholders with " ": keep that separator
            parts.append({"type": "text", "text": " "})
        for ts, urls in seg["tubelets"]:
            parts.append({"type": "text", "text": f"<{ts:.1f} seconds>"})
            for url in urls:
                parts.append({"type": "image_url", "image_url": {"url": url}})
    parts.append({"type": "text", "text": tail.replace("{task}", instruction)})
    return parts
