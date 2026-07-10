from __future__ import annotations

import os
import time

ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
TIME_LENGTH = 10
RANDOM_LENGTH_BYTES = 10
MASK = 0x1F


def new_ulid() -> str:
    timestamp = int(time.time() * 1000)
    if timestamp < 0 or timestamp > 0xFFFFFFFFFFFF:
        raise ValueError("ULID timestamp must fit in 48 bits")

    chars = [""] * 26
    time_value = timestamp
    for index in range(TIME_LENGTH - 1, -1, -1):
        chars[index] = ENCODING[time_value & MASK]
        time_value >>= 5

    bit_buffer = 0
    bit_count = 0
    char_index = TIME_LENGTH
    for random_byte in os.urandom(RANDOM_LENGTH_BYTES):
        bit_buffer = (bit_buffer << 8) | random_byte
        bit_count += 8
        while bit_count >= 5:
            chars[char_index] = ENCODING[(bit_buffer >> (bit_count - 5)) & MASK]
            char_index += 1
            bit_count -= 5

    return "".join(chars)
