#!/usr/bin/env python3
"""布局普查几何断言（v0.68.97 布局普查批新增，常驻网）。

解析 uiautomator dump，对关键元素做几何断言：
- 两两不相交（按 resource-id=testTag 定位；Dock 面板取 dock_panel，
  缺失时退化为全部 dock_item 的并集 bounds）
- 关键元素完整落在屏内（x1>=0,y1>=0,x2<=W,y2<=H）

用法：layout_check.py <dump_dir> <screen_w_px> <screen_h_px>
输入约定（smoke.sh 第 22 段与既有段产出的 dump，缺失记 INFO 跳过）：
- tpl_giant.xml / tpl_headline.xml / tpl_minimal.xml  首页三模板（未聚焦）
- tpl_<tpl>_suggest.xml                               意图聚焦/建议面
- deck_focus.xml                                      输入态甲板在场态（第 25 段，F 批面板）
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


def check_state(root, state, elements, pairs, W, H, one_pet=False):
    """elements: {name: bounds or None}; pairs: [(a,b)] 需不相交"""
    if one_pet:
        # v0.68.101（单宠物原则，Bob 08:08 拍板）：任何时刻屏幕上恰一只
        # 七仔——静态 = Dock 坐姿（dock_pet_cell）、输入态 = 坐胶囊上沿
        # （pet_qizai，F 批站位），两者永不同时在场、也永不双缺（主页形态下）。
        n = len(by_rid(root, "dock_pet_cell")) + len(by_rid(root, "pet_qizai"))
        if n == 1:
            print(f"LAYOUT OK {state}: one-pet count={n}")
        else:
            print(f"LAYOUT FAIL {state}: one-pet count={n} (expected exactly 1)")
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
    # F 批：胶囊几何以 intent_capsule（整胶囊行）为准；旧 dump 无此锚点
    # 时退化到 intent_input（文本区，口径略窄但仍可判在屏/不相交）
    capfull = by_rid(root, "intent_capsule")
    cap = capfull if capfull else by_rid(root, "intent_input")
    sub = by_rid(root, "intent_submit")
    nxt = by_rid(root, "today_nextup")
    pet = by_rid(root, "pet_qizai")
    seat = by_rid(root, "dock_pet_cell")
    hero = by_rid(root, "cover_hero")
    deck = by_rid(root, "deck_root")
    ipanel = by_rid(root, "intent_panel")
    rslot = by_rid(root, "intent_result_slot")
    return {
        "hero": hero[0] if hero else None,
        "capsule": cap[0] if cap else None,
        "submit": sub[0] if sub else None,
        "nextup": nxt[0] if nxt else None,
        "pet": pet[0] if pet else None,
        "dockpet": seat[0] if seat else None,
        "dock": dock,
        # v0.68.101（R-2）：输入甲板（胶囊上方槽位）——静态态不存在，
        # absent 时 INFO 跳过，不影响既有状态的断言口径
        "deck": deck[0] if deck else None,
        # F 批：一体化面板与结果槽位（面板包含断言用，见 check_deck_focus）
        "panel": ipanel[0] if ipanel else None,
        "resultslot": rslot[0] if rslot else None,
    }


HOME_PAIRS = [
    ("hero", "capsule"), ("hero", "nextup"), ("hero", "dock"),
    ("capsule", "nextup"), ("capsule", "dock"), ("submit", "dock"),
    ("nextup", "dock"), ("pet", "nextup"),
    ("pet", "dock"),
    # v0.68.101：坐姿七仔（dockpet）是 dock 面板的子节点（包含关系不查
    # dockpet×dock）；它探出面板上沿，必须与会行/hero 不相交
    ("dockpet", "nextup"), ("dockpet", "hero"), ("dockpet", "capsule"),
    # F 批：输入态七仔与胶囊是「坐沿」交叠关系（下半身被胶囊盖住），
    # pet×capsule 不再查不相交——改由 check_deck_focus 的坐沿断言管
]
FOCUS_PAIRS = [
    ("hero", "capsule"), ("capsule", "nextup"), ("hero", "nextup"),
    ("pet", "nextup"),
    # v0.68.101（R-2）：甲板在 hero 与胶囊之间、三者与 NextUp 互不相交
    # （横屏同样适用——L-7：横屏输入态与竖屏同语义，断言不放水）
    ("hero", "deck"), ("deck", "capsule"), ("deck", "nextup"),
    # F 批：pet×capsule 改坐沿断言（见 check_deck_focus），不在此查不相交
    # 聚焦态键盘升起，Dock 被 IME 覆盖，dump 中的 Dock bounds 不代表可见几何，不查
]


def _inside(inner, outer):
    """inner 是否完整落在 outer 内（EPS 容差）"""
    return (inner[0] >= outer[0] - EPS and inner[1] >= outer[1] - EPS
            and inner[2] <= outer[2] + EPS and inner[3] <= outer[3] + EPS)


def check_deck_focus(root, W, H):
    """F 批·输入态面板几何（第 25 段 deck_focus.xml）：
    通用在屏/不相交 + one_pet 之外，加三组 F 专属断言——
    ① 七仔坐沿：pet 底边低于胶囊顶边（被胶囊盖住）、pet 顶边高于
       胶囊顶边（头身露在胶囊之上）；
    ② 七仔水平中心落在胶囊宽的 10%–40% 区间（原型定稿：左起约 25%）；
    ③ 同面包含：甲板 / 胶囊 / 结果槽位（在场且有高度时）完整落在
       一体化面板 bounds 内。
    """
    state = "deck-focus"
    els = home_elements(root)
    check_state(root, state, els, FOCUS_PAIRS, W, H, one_pet=True)
    pet, cap, panel = els.get("pet"), els.get("capsule"), els.get("panel")
    if pet is None or cap is None:
        print(f"LAYOUT INFO {state}: pet/capsule absent, sit assertions skipped")
    else:
        if pet[3] > cap[1] + EPS and pet[1] < cap[1] - EPS:
            print(f"LAYOUT OK {state}: pet sits on capsule edge pet={pet} capsule={cap}")
        else:
            print(f"LAYOUT FAIL {state}: pet not sitting on capsule edge "
                  f"pet={pet} capsule_top={cap[1]} (need pet_top<capsule_top<pet_bottom)")
        cx = (pet[0] + pet[2]) / 2.0
        ratio = (cx - cap[0]) / float(cap[2] - cap[0]) if cap[2] > cap[0] else -1
        if 0.10 <= ratio <= 0.40:
            print(f"LAYOUT OK {state}: pet center-x ratio={ratio:.3f} within [0.10,0.40]")
        else:
            print(f"LAYOUT FAIL {state}: pet center-x ratio={ratio:.3f} outside [0.10,0.40] "
                  f"pet={pet} capsule={cap}")
    if panel is None:
        print(f"LAYOUT FAIL {state}: intent_panel absent (integrated panel missing)")
    else:
        for name in ("deck", "capsule", "resultslot"):
            b = els.get(name)
            if b is None:
                print(f"LAYOUT INFO {state}/{name}: absent, containment skipped")
                continue
            if name == "resultslot" and b[3] - b[1] <= EPS:
                print(f"LAYOUT INFO {state}/resultslot: empty slot, containment skipped")
                continue
            if _inside(b, panel):
                print(f"LAYOUT OK {state}: {name} inside panel {b} in {panel}")
            else:
                print(f"LAYOUT FAIL {state}: {name} escapes panel {b} not in {panel}")


def main():
    d = sys.argv[1]
    W = int(sys.argv[2]); H = int(sys.argv[3])
    for tpl in ("giant", "headline", "minimal"):
        p = os.path.join(d, f"tpl_{tpl}.xml")
        if os.path.exists(p):
            r = parse(p)
            if r is not None:
                check_state(r, f"home-{tpl}", home_elements(r), HOME_PAIRS, W, H,
                            one_pet=True)
        p = os.path.join(d, f"tpl_{tpl}_suggest.xml")
        if os.path.exists(p):
            r = parse(p)
            if r is not None:
                check_state(r, f"focus-{tpl}", home_elements(r), FOCUS_PAIRS, W, H,
                            one_pet=True)
    p = os.path.join(d, "deck_focus.xml")
    if os.path.exists(p):
        r = parse(p)
        if r is not None:
            check_deck_focus(r, W, H)
    p = os.path.join(d, "layout_home.xml")
    if os.path.exists(p):
        r = parse(p)
        if r is not None:
            check_state(r, "home-current", home_elements(r), HOME_PAIRS, W, H,
                        one_pet=True)
    p = os.path.join(d, "layout_ac.xml")
    if os.path.exists(p):
        r = parse(p)
        if r is not None:
            cap = by_rid(r, "intent_input")
            gear = by_rid(r, "appcenter_settings")
            check_state(r, "appcenter",
                        {"capsule": cap[0] if cap else None,
                         "settings-gear": gear[0] if gear else None},
                        [("capsule", "settings-gear")], W, H)

    p = os.path.join(d, "layout_settings.xml")
    if os.path.exists(p):
        r = parse(p)
        if r is not None:
            card = None
            for n in r.iter("node"):
                if "让七仔更聪明" in (n.get("text") or ""):
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
