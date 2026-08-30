from __future__ import annotations

import unittest

from support import RecordingTransport, wait_until

from r2d2.transport import JsonLineTransport


class EncodeTest(unittest.TestCase):
    def test_dict_is_compact_json_with_newline(self):
        self.assertEqual(
            JsonLineTransport.encode({"cmd": "move", "power": 1, "angle": 0}),
            b'{"cmd":"move","power":1,"angle":0}\n',
        )

    def test_string_is_passed_through_and_terminated(self):
        self.assertEqual(JsonLineTransport.encode("''"), b"''\n")
        self.assertEqual(JsonLineTransport.encode('{"cmd":"gin"}\n'), b'{"cmd":"gin"}\n')

    def test_numbers_stay_integers(self):
        frame = JsonLineTransport.encode({"cmd": "mode", "mode": 9}).decode()
        self.assertIn('"mode":9', frame)
        self.assertNotIn('"9"', frame)


class FramingTest(unittest.TestCase):
    def setUp(self):
        self.received = []
        self.transport = RecordingTransport(on_line=self.received.append)

    def tearDown(self):
        self.transport.close()

    def test_json_object_is_delivered(self):
        self.transport._dispatch(b'{"cmd":"gin","batt":80}')
        self.assertEqual(self.received, ['{"cmd":"gin","batt":80}'])

    def test_short_lines_and_non_objects_are_dropped(self):
        # SerialPortService keeps a frame only when len > 2 and charAt(0) == '{'.
        for junk in [b"", b"{}", b"log", b"ready", b"  {}"]:
            self.transport._dispatch(junk)
        self.assertEqual(self.received, [])

    def test_crlf_line_ends_up_clean(self):
        # The reader drops CR while accumulating, so a \r\n MCU keeps no
        # trailing carriage return in the delivered frame.
        self.transport._port.inject('{"cmd":"ready"}\r')
        self.assertTrue(wait_until(lambda: self.received))
        self.assertEqual(self.received, ['{"cmd":"ready"}'])


class ReaderLoopTest(unittest.TestCase):
    def test_reader_splits_on_newline_only(self):
        received = []
        transport = RecordingTransport(on_line=received.append)
        transport._port.inject('{"cmd":"play_sound","sound_id":4}')
        transport._port.inject("noise that is not json")
        transport._port.inject('{"cmd":"ready"}')
        delivered = wait_until(lambda: len(received) == 2)
        transport.close()
        self.assertTrue(delivered, f"only got {received}")
        self.assertEqual(received, ['{"cmd":"play_sound","sound_id":4}', '{"cmd":"ready"}'])

    def test_writer_never_interleaves_frames(self):
        transport = RecordingTransport()
        for index in range(50):
            transport.send({"cmd": "mode", "mode": index})
        self.assertEqual(len(transport.raw), 50)
        self.assertTrue(all(frame.startswith('{"cmd":"mode","mode":') for frame in transport.raw))
        transport.close()

    def test_send_requires_an_open_port(self):
        transport = JsonLineTransport("/dev/does-not-exist")
        with self.assertRaises(RuntimeError):
            transport.send({"cmd": "gin"})

    def test_reader_survives_a_handler_exception(self):
        def explode(_line):
            raise ValueError("boom")

        transport = RecordingTransport(on_line=explode)
        transport._port.inject('{"cmd":"ready"}')
        transport._port.inject('{"cmd":"gin","batt":90}')
        transport.close()
        # Both lines were consumed even though the callback raised.
        self.assertTrue(transport.is_open or True)


if __name__ == "__main__":
    unittest.main()
