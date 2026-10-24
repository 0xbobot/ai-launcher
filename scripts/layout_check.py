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
        # 七仔——静态 = Dock 坐姿（dock_pet_cell）、输入态 = 紧凑行伴随体
        # （pet_qizai，v4 站位），两者永不同时在场、也永不双缺（主页形态下）。
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
    # v4：输入态七仔已移出胶囊上沿、与胶囊不再交叠，pet×capsule
    # 回归不相交检查（由 check_deck_focus 的紧凑行断言补间隙上限）
]
FOCUS_PAIRS = [
    ("hero", "capsule"), ("capsule", "nextup"), ("hero", "nextup"),
    ("pet", "nextup"),
    # v0.68.101（R-2）：甲板在 hero 与胶囊之间、三者与 NextUp 互不相交
    # （横屏同样适用——L-7：横屏输入态与竖屏同语义，断言不放水）
    ("hero", "deck"), ("deck", "capsule"), ("deck", "nextup"),
    # v4：紧凑行在甲板与胶囊之间，七仔与甲板/胶囊互不相交
    ("pet", "deck"), ("pet", "capsule"),
    # 聚焦态键盘升起，Dock 被 IME 覆盖，dump 中的 Dock bounds 不代表可见几何，不查
]


def _inside(inner, outer):
    """inner 是否完整落在 outer 内（EPS 容差）"""
    return (inner[0] >= outer[0] - EPS and inner[1] >= outer[1] - EPS
            and inner[2] <= outer[2] + EPS and inner[3] <= outer[3] + EPS)


def check_deck_focus(root, W, H):
    """v4 紧凑行几何（第 25 段 deck_focus.xml）：
    通用在屏/不相交 + one_pet 之外，加四组 v4 专属断言——
    ① 七仔可见高：bounds 高 × 资产填充比 0.53（F 批实测：角色在盒子内
       纵向占比约 53%）与胶囊高之比 ∈ [0.70, 1.00]（原型定稿：约 0.88）；
    ② 同行同带：七仔中心 y 与推荐行（deck_example_* 并集）中心 y 之差 ≤12dp；
    ③ 紧凑间隙：行底（七仔/推荐行底边最大值）到胶囊顶 ∈ [0, 12dp]，
       不重叠、不留大缝；
    ④ 左右次序：推荐行左缘在七仔右缘之右；pet_qizai 全屏唯一。
    （F 批的坐沿断言 pet-sits-on-edge 随七仔移出胶囊上沿而退役。）
    dp 换算：CI 模拟器腿统一 1080 宽、420dpi 级（宽约 411dp），密度取 W/411。
    """
    state = "deck-focus"
    els = home_elements(root)
    check_state(root, state, els, FOCUS_PAIRS, W, H, one_pet=True)
    density = W / 411.0 if W else 2.625

    def dp(px):
        return px / density

    pet, cap, panel = els.get("pet"), els.get("capsule"), els.get("panel")
    pet_nodes = by_rid(root, "pet_qizai")
    if len(pet_nodes) == 1:
        print(f"LAYOUT OK {state}: pet_qizai unique count=1")
    else:
        print(f"LAYOUT FAIL {state}: pet_qizai count={len(pet_nodes)} (expected exactly 1)")
    ex_nodes = []
    for n in root.iter("node"):
        rid = n.get("resource-id") or ""
        if rid.startswith("deck_example_"):
            b = bounds(n)
            if b:
                ex_nodes.append(b)
    examples = union(ex_nodes) if ex_nodes else None
    if pet is None or cap is None:
        print(f"LAYOUT INFO {state}: pet/capsule absent, compact assertions skipped")
    else:
        cap_h = cap[3] - cap[1]
        ratio = (pet[3] - pet[1]) * 0.53 / float(cap_h) if cap_h > 0 else -1
        if 0.70 <= ratio <= 1.00:
            print(f"LAYOUT OK {state}: pet visible-height ratio={ratio:.3f} within [0.70,1.00]")
        else:
            print(f"LAYOUT FAIL {state}: pet visible-height ratio={ratio:.3f} outside [0.70,1.00] "
                  f"pet={pet} capsule={cap} (bounds_h x 0.53 / capsule_h)")
        row_bottom = pet[3]
        if examples is not None:
            pet_cy = (pet[1] + pet[3]) / 2.0
            ex_cy = (examples[1] + examples[3]) / 2.0
            if abs(dp(pet_cy - ex_cy)) <= 12.0:
                print(f"LAYOUT OK {state}: pet/examples same band, center-y diff="
                      f"{dp(pet_cy - ex_cy):.1f}dp")
            else:
                print(f"LAYOUT FAIL {state}: pet/examples center-y diff="
                      f"{dp(pet_cy - ex_cy):.1f}dp exceeds 12dp pet={pet} examples={examples}")
            if examples[0] >= pet[2] - EPS:
                print(f"LAYOUT OK {state}: examples start right of pet "
                      f"examples_left={examples[0]} pet_right={pet[2]}")
            else:
                print(f"LAYOUT FAIL {state}: examples start left of pet right edge "
                      f"examples={examples} pet={pet}")
            row_bottom = max(row_bottom, examples[3])
        gap = dp(cap[1] - row_bottom)
        if -EPS / density <= gap <= 12.0:
            print(f"LAYOUT OK {state}: compact-row to capsule gap={gap:.1f}dp within [0,12]")
        else:
            print(f"LAYOUT FAIL {state}: compact-row to capsule gap={gap:.1f}dp outside [0,12] "
                  f"row_bottom={row_bottom} capsule_top={cap[1]}")
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
        # v4：紧凑行整体（七仔 + 推荐行）也须落在面板内
        for name, b in (("pet", pet), ("examples", examples)):
            if b is None:
                print(f"LAYOUT INFO {state}/{name}: absent, containment skipped")
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
