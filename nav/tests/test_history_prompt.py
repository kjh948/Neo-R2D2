"""Unit tests for tier sampling and prompt assembly (CPU, no weights)."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from navstack.config import PRESETS  # noqa: E402
from navstack.history import EpisodeBuffer  # noqa: E402
from navstack.prompt import (  # noqa: E402
    build_content_parts,
    build_prompt_text,
    split_template_rvq,
    tubelet_timestamp,
)

TIERS = PRESETS["micro"]  # current 2 @pool1 + fast 10 @pool2


class BufferTest(unittest.TestCase):
    def test_segments_even_and_ascending(self):
        buf = EpisodeBuffer(TIERS)
        for _ in range(9):
            buf.append(np.zeros((4, 4, 3), np.uint8))
        segs = buf.sample_segments()
        ids = []
        for s in segs:
            self.assertEqual(len(s["frame_ids"]) % 2, 0, "tubelet pairing requires even")
            fids = s["frame_ids"]
            # non-decreasing: pad-to-even duplicates the newest frame (e.g. [7,8,8] -> [7,8,8,8])
            self.assertTrue(all(b >= a for a, b in zip(fids, fids[1:])), fids)
            self.assertLessEqual(sum(1 for a, b in zip(fids, fids[1:]) if a == b), 1)
            ids.extend(fids)
        self.assertEqual(ids, sorted(ids), "chronological, oldest segment first")
        self.assertEqual(ids[0], 0, "episode start is kept")
        self.assertIn(8, segs[-1]["frame_ids"], "current frame in the newest segment")

    def test_pool_levels(self):
        buf = EpisodeBuffer(TIERS)
        for _ in range(9):
            buf.append(np.zeros((4, 4, 3), np.uint8))
        segs = buf.sample_segments()
        pools = {s["pool_spatial"] for s in segs}
        self.assertTrue(pools <= {1, 2}, pools)
        self.assertEqual(segs[-1]["pool_spatial"], 1, "newest observation stays pool 1")

    def test_reset(self):
        buf = EpisodeBuffer(TIERS)
        buf.append(np.zeros((4, 4, 3), np.uint8))
        buf.reset()
        self.assertEqual(len(buf), 0)
        with self.assertRaises(RuntimeError):
            buf.sample_segments()


class TimestampTest(unittest.TestCase):
    def test_absolute_mean_of_pair(self):
        # frames 4,5 @4fps -> (1.0+1.25)/2 = 1.125 -> "<1.1 seconds>"
        self.assertAlmostEqual(tubelet_timestamp([4, 5], 5, 4.0, relative=False), 1.125)

    def test_relative_to_current(self):
        # time-before-current: pair (2,3) with current 7 -> ((5+4)/2)/4... = (7-2)/4,(7-3)/4
        self.assertAlmostEqual(tubelet_timestamp([2, 3], 7, 4.0, relative=True), 1.125)


class PromptTest(unittest.TestCase):
    def test_template_contract(self):
        text = build_prompt_text(2, "go to the door")
        self.assertTrue(text.startswith("You are a mobile robot. You are given visual"))
        self.assertIn("ordered from earliest to most recent: <video> <video>", text)
        self.assertIn("<navigation_task>go to the door</navigation_task>", text)
        self.assertTrue(
            text.endswith("Predict your future trajectory as a sequence of "
                          "coarse-to-fine trajectory tokens. "))

    def test_content_parts_structure(self):
        segs = [
            {"tubelets": [(25.1, ["data:image/jpeg;base64,AAA", "data:image/jpeg;base64,BBB"])]},
            {"tubelets": [(0.1, ["data:image/jpeg;base64,CCC"]),
                          (0.6, ["data:image/jpeg;base64,DDD", "data:image/jpeg;base64,EEE"])]},
        ]
        parts = build_content_parts(segs, "find the chair")
        texts = [p["text"] for p in parts if p["type"] == "text"]
        images = [p for p in parts if p["type"] == "image_url"]
        self.assertEqual(len(images), 5)
        self.assertTrue(texts[0].endswith("most recent: "))
        self.assertIn("<25.1 seconds>", texts)
        self.assertIn("<0.1 seconds>", texts)
        # segment separator (the {videos} slot joined placeholders with " ")
        self.assertIn(" ", texts)
        self.assertIn("<navigation_task>find the chair</navigation_task>", texts[-1])

    def test_no_literal_video_tags_in_parts(self):
        # placeholders must be consumed by image parts, not leaked into text
        head, tail = split_template_rvq()
        joined = head + tail
        self.assertNotIn("<video>", joined)
        self.assertNotIn("{videos}", joined)


if __name__ == "__main__":
    unittest.main()
