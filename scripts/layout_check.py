#!/usr/bin/env python3
"""布局普查几何断言（v0.68.97 布局普查批新增，常驻网）。

解析 uiautomator dump，对关键元素做几何断言：
- 两两不相交（按 resource-id=testTag 定位；Dock 面板取 dock_panel，
  缺失时退化为全部 dock_item 的并集 bounds）
- 关键元素完整落在屏内（x1>=0,y1>=0,x2<=W,y2<=H）

477 后（2026-11-08）：首页无胶囊、无常驻宠物——one-pet 断言、
Dock 宠物连接几何（check_dock_pet）、意图甲板聚焦几何
（check_deck_focus）与聚焦态 dump（tpl_*_suggest.xml、deck_focus.xml）
已随意图面板与宠物退役；486（2026-11-11）简报会行拿掉后 today_nextup
节点不再出现，check_state 对缺失节点只记 LAYOUT INFO 跳过、不判 FAIL，
故首页实际断言只剩简报（cover_hero）/Dock（dock_panel）一对。

用法：layout_check.py <dump_dir> <screen_w_px> <screen_h_px>
输入约定（smoke.sh 第 21 段与既有段产出的 dump，缺失记 INFO 跳过）：
- tpl_giant.xml / tpl_headline.xml / tpl_minimal.xml  首页三模板（静态）
- layout_home.xml                                     当前模板首页（兜底）
- layout_ac.xml                                       应用中心
- fan_dump.xml                                        扇形展开（Canvas 语义稀疏，只记 INFO）
输出行前缀 LAYOUT OK / LAYOUT FAIL / LAYOUT INFO，smoke.sh 以 FAIL 行计数。
相交判定：交叠宽与高都 > 2px 才算相交（容忍边界贴合的取整误差）。
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

EPS = 2  # px，边界贴合容差


def parse(path):
    try:
        return ET.parse(path).getroot()
    except Exception as e:
        print(f"LAYOUT INFO parse-failed {os.path.basename(path)}: {e}")
        return None


def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    return tuple(int(v) for v in m.groups())


def by_rid(root, rid):
    out = []
    for n in root.iter("node"):
        if n.get("resource-id") == rid:
            b = bounds(n)
            if b:
                out.append(b)
    return out


def union(bs):
    if not bs:
        return None
    return (min(b[0] for b in bs), min(b[1] for b in bs),
            max(b[2] for b in bs), max(b[3] for b in bs))


def inter(a, b):
    """交叠 (w,h)；无交叠返回 (0,0)"""
    w = min(a[2], b[2]) - max(a[0], b[0])
    h = min(a[3], b[3]) - max(a[1], b[1])
    return (w, h) if w > 0 and h > 0 else (0, 0)


def check_state(root, state, elements, pairs, W, H):
    """elements: {name: bounds or None}; pairs: [(a,b)] 需不相交"""
    for name, b in elements.items():
        if b is None:
            print(f"LAYOUT INFO {state}/{name}: absent, skipped")
            continue
        if b[0] < 0 or b[1] < 0 or b[2] > W or b[3] > H:
            print(f"LAYOUT FAIL {state}/{name}: out-of-screen bounds={b} screen={W}x{H}")
        else:
            print(f"LAYOUT OK {state}/{name}: in-screen bounds={b}")
    for a, b in pairs:
        ba, bb = elements.get(a), elements.get(b)
        if ba is None or bb is None:
            continue
        w, h = inter(ba, bb)
        if w > EPS and h > EPS:
            print(f"LAYOUT FAIL {state}: overlap {a} x {b}: inter={w}x{h}px "
                  f"{a}={ba} {b}={bb}")
        else:
            print(f"LAYOUT OK {state}: no-overlap {a} x {b}")


def home_elements(root):
    dock = None
    panel = by_rid(root, "dock_panel")
    if panel:
        dock = panel[0]
    else:
        dock = union(by_rid(root, "dock_item"))
    nxt = by_rid(root, "today_nextup")
    hero = by_rid(root, "cover_hero")
    return {
        "hero": hero[0] if hero else None,
        "nextup": nxt[0] if nxt else None,
        "dock": dock,
    }


HOME_PAIRS = [
    ("hero", "nextup"), ("hero", "dock"), ("nextup", "dock"),
]


def main():
    d = sys.argv[1]
    W = int(sys.argv[2]); H = int(sys.argv[3])
    for tpl in ("giant", "headline", "minimal"):
        p = os.path.join(d, f"tpl_{tpl}.xml")
        if os.path.exists(p):
            r = parse(p)
            if r is not None:
                check_state(r, f"home-{tpl}", home_elements(r), HOME_PAIRS, W, H)
    p = os.path.join(d, "layout_home.xml")
    if os.path.exists(p):
        r = parse(p)
        if r is not None:
            check_state(r, "home-current", home_elements(r), HOME_PAIRS, W, H)
    p = os.path.join(d, "layout_ac.xml")
    if os.path.exists(p):
        r = parse(p)
        if r is not None:
            cap = by_rid(r, "intent_input")
            gear = by_rid(r, "appcenter_settings")
            # 应用中心升级：rail 几何断言（节点缺失时 INFO 跳过，不破坏既有断言）——
            # 在屏 + 与胶囊不相交（rail 跟随容器高度后不应再顶进顶栏区）；
            # 行高/触控区/气泡位置无 dump 锚点，仍由 JVM 单测 + 真机验收覆盖
            rail = by_rid(r, "appcenter_rail")
            check_state(r, "appcenter",
                        {"capsule": cap[0] if cap else None,
                         "settings-gear": gear[0] if gear else None,
                         "rail": rail[0] if rail else None},
                        [("capsule", "settings-gear"),
                         ("capsule", "rail")], W, H)

    p = os.path.join(d, "layout_settings.xml")
    if os.path.exists(p):
        r = parse(p)
        if r is not None:
            card = None
            for n in r.iter("node"):
                if "让助手更聪明" in (n.get("text") or ""):
                    card = bounds(n)
                    break
            if card is None:
                print("LAYOUT INFO settings: anchor text absent, skipped")
            elif card[0] < 0 or card[1] < 0 or card[2] > W or card[3] > H:
                print(f"LAYOUT FAIL settings/card: out-of-screen bounds={card}")
            else:
                print(f"LAYOUT OK settings/card: in-screen bounds={card}")
    p = os.path.join(d, "fan_dump.xml")
    if os.path.exists(p):
        r = parse(p)
        if r is not None:
            n = sum(1 for x in r.iter("node") if x.get("clickable") == "true")
            print(f"LAYOUT INFO fan: clickable nodes={n} (Canvas 手势面，几何断言不适用，截图目检)")


if __name__ == "__main__":
    main()
