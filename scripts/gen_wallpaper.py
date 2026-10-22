#!/usr/bin/env python3
"""冒烟取证用测试壁纸生成（纯 python 标准库，无第三方依赖）。

photo：沙丘场景——暖橙天空渐变 + 太阳 + 四层山脊剪影（高起伏、高饱和，
       模拟照片/场景壁纸，玻璃应判 TEXTURED 全开强材质）
paper：纯色暖纸 #F4F2EE（与浅色主题 Background 同色，玻璃应判 FLAT 轻霜）

用法：gen_wallpaper.py photo|paper <out.png> [W H]（默认 1080x2400）
"""
import math
import struct
import sys
import zlib


def _chunk(typ: bytes, data: bytes) -> bytes:
    return (
        struct.pack(">I", len(data))
        + typ
        + data
        + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)
    )


def _write_png(path: str, w: int, h: int, rows: list[bytes]) -> None:
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)  # 8-bit RGB
    png = (
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", ihdr)
        + _chunk(b"IDAT", zlib.compress(b"".join(rows), 6))
        + _chunk(b"IEND", b"")
    )
    with open(path, "wb") as f:
        f.write(png)


def _lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def photo_rows(w: int, h: int) -> list[bytes]:
    sky_top, sky_mid, sky_low = (56, 90, 158), (240, 168, 94), (252, 208, 142)
    sun_x, sun_y, sun_r = w * 0.68, h * 0.30, w * 0.085
    # 每层山脊：(基线 y 比, 振幅比, 相位, 颜色)；每列预计算边缘 y
    ridges = [
        (0.52, 0.035, 0.0, (198, 130, 84)),
        (0.64, 0.045, 1.7, (152, 94, 62)),
        (0.78, 0.050, 3.4, (106, 64, 48)),
        (0.92, 0.040, 5.1, (68, 42, 36)),
    ]
    edges = []
    for base, amp, ph, _ in ridges:
        edges.append(
            [
                (base + amp * math.sin(x / w * 6.283 + ph) + amp * 0.4 * math.sin(x / w * 17.0 + ph * 2.0)) * h
                for x in range(w)
            ]
        )
    rows = []
    for y in range(h):
        t = y / h
        if t < 0.55:
            sky = _lerp(sky_top, sky_mid, t / 0.55)
        else:
            sky = _lerp(sky_mid, sky_low, (t - 0.55) / 0.45)
        row = bytearray(b"\x00")  # filter: none
        for x in range(w):
            col = sky
            d = math.hypot(x - sun_x, y - sun_y)
            if d < sun_r:
                col = (255, 238, 192)
            elif d < sun_r * 1.35:
                col = _lerp(col, (255, 222, 162), 0.45)
            for i, (_, _, _, rc) in enumerate(ridges):
                if y > edges[i][x]:
                    col = rc
            row += bytes(col)
        rows.append(bytes(row))
    return rows


def main() -> int:
    mode, out = sys.argv[1], sys.argv[2]
    w = int(sys.argv[3]) if len(sys.argv) > 3 else 1080
    h = int(sys.argv[4]) if len(sys.argv) > 4 else 2400
    if mode == "photo":
        _write_png(out, w, h, photo_rows(w, h))
    elif mode == "paper":
        row = b"\x00" + bytes((0xF4, 0xF2, 0xEE)) * w
        _write_png(out, w, h, [row] * h)
    else:
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
