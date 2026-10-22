#!/usr/bin/env python3
"""冒烟取证用测试壁纸生成（纯 python 标准库，无第三方依赖）。

photo：沙丘场景——暖橙天空渐变 + 太阳 + 四层山脊剪影（高起伏、高饱和，
       模拟照片/场景壁纸，玻璃应判 TEXTURED 全开强材质）
sky：蓝天+远山——上部平滑浅蓝天 + 下部雾感远山（低对比），Bob .94 真机
     壁纸结构；试点参数在此图上被判「没有玻璃感」，v0.68.96 起作回归取证
paper：纯色暖纸 #F4F2EE（与浅色主题 Background 同色，玻璃应判 FLAT 轻霜）

用法：gen_wallpaper.py photo|sky|paper <out.png> [W H]（默认 1080x2400）
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


def sky_rows(w: int, h: int) -> list[bytes]:
    """蓝天+远山（Bob .94 真机壁纸结构，v0.68.96 新增取证）：
    上 ~62% 平滑浅蓝天（低纹理、低起伏），下部两层雾感远山（蓝绿、低对比）。
    山体亮度用低频调制（周期 ~百 px 级）而非细噪点——分类走 48×48 缩略图，
    细噪点会被下采样平均掉，低频起伏才能在 Dock 带留下可判的局部细节。
    期望：整图观感是「没什么可透的雾山」，但 Dock 带分类 = TEXTURED，
    玻璃全开档应能看出通透与切面（试点参数在此图上被 Bob 判「没有玻璃感」）。
    """
    sky_top, sky_bot = (170, 203, 233), (203, 226, 242)
    far_base, near_base = (118, 146, 134), (88, 120, 94)

    def edge_a(x):
        return (0.60 + 0.030 * math.sin(x / w * 5.1) + 0.015 * math.sin(x / w * 13.7)) * h

    def edge_b(x):
        return (0.76 + 0.035 * math.sin(x / w * 4.3 + 2.0) + 0.020 * math.sin(x / w * 11.0)) * h

    ea = [edge_a(x) for x in range(w)]
    eb = [edge_b(x) for x in range(w)]
    rows = []
    for y in range(h):
        t = y / h
        row = bytearray(b"\x00")
        for x in range(w):
            if y < ea[x]:
                col = _lerp(sky_top, sky_bot, min(t / 0.62, 1.0))
            elif y < eb[x]:
                m = int(10 * math.sin(x / w * 9.0 + 1.3) * math.sin(t * 22.0))
                col = tuple(max(0, min(255, c + m)) for c in far_base)
            else:
                m = int(12 * math.sin(x / w * 7.0 + 0.6) * math.sin(t * 18.0 + 2.0))
                base = _lerp(near_base, (64, 96, 74), max(0.0, (t - 0.76) / 0.24))
                col = tuple(max(0, min(255, c + m)) for c in base)
            row += bytes(col)
        rows.append(bytes(row))
    return rows


def main() -> int:
    mode, out = sys.argv[1], sys.argv[2]
    w = int(sys.argv[3]) if len(sys.argv) > 3 else 1080
    h = int(sys.argv[4]) if len(sys.argv) > 4 else 2400
    if mode == "photo":
        _write_png(out, w, h, photo_rows(w, h))
    elif mode == "sky":
        _write_png(out, w, h, sky_rows(w, h))
    elif mode == "paper":
        row = b"\x00" + bytes((0xF4, 0xF2, 0xEE)) * w
        _write_png(out, w, h, [row] * h)
    else:
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
