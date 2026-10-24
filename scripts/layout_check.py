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
    # v6：七仔与甲板互不相交；七仔×胶囊不再查不相交——七仔脚底按规格
    # 压进胶囊顶缘（ intentional overlap，见 check_deck_focus 压边断言）
    ("pet", "deck"),
    # 聚焦态键盘升起，Dock 被 IME 覆盖，dump 中的 Dock bounds 不代表可见几何，不查
]


def _inside(inner, outer):
    """inner 是否完整落在 outer 内（EPS 容差）"""
    return (inner[0] >= outer[0] - EPS and inner[1] >= outer[1] - EPS
            and inner[2] <= outer[2] + EPS and inner[3] <= outer[3] + EPS)


def first_index(root, rid):
    """dump 文档序中首个指定 resource-id 节点的序号（Compose 语义树序 =
    组合序；Box 内后画的兄弟在后——前面图层断言以它为准）。"""
    for i, n in enumerate(root.iter("node")):
        if n.get("resource-id") == rid:
            return i
    return None


def check_no_pill_wrappers(root, state, label, nodes, panel):
    """素行断言（v6：不要圆角白 pill）：每个示例节点不得被一个「容器」
    节点包裹——容器 = 上下各留 ≥4dp 内边距地包含它。面板本身与包含
    全部示例的横滚行不算容器（前者是面板、后者上下无内边距）。"""
    density = 2.625
    pad = 4 * density
    all_nodes = []
    for n in root.iter("node"):
        b = bounds(n)
        if b:
            all_nodes.append((n.get("resource-id") or "", b))
    panel_b = panel
    bad = []
    for (rid, b) in nodes:
        for (nrid, nb) in all_nodes:
            if nrid == rid or nb == b:
                continue
            if panel_b is not None and nb == panel_b:
                continue
            # 横滚行：包含全部示例节点时不算包裹
            if sum(1 for (_, eb) in nodes
                   if eb[0] >= nb[0] - EPS and eb[1] >= nb[1] - EPS
                   and eb[2] <= nb[2] + EPS and eb[3] <= nb[3] + EPS) >= 2:
                continue
            if nb[0] <= b[0] + EPS and nb[1] <= b[1] - pad and \
               nb[2] >= b[2] - EPS and nb[3] >= b[3] + pad:
                bad.append((rid, nrid, nb))
    if bad:
        for (rid, nrid, nb) in bad:
            print(f"LAYOUT FAIL {state}: {label} {rid} wrapped by pill-like "
                  f"container {nrid or '(untagged)'}={nb}")
    else:
        print(f"LAYOUT OK {state}: {label} plain rows, no pill wrappers "
              f"({len(nodes)} items)")


def check_deck_focus(root, W, H):
    """v6 输入态几何（第 25 段 deck_focus.xml）：
    通用在屏/不相交 + one_pet 之外，加六组 v6 专属断言——
    ① 七仔可见高：节点高（intentPose 资产撑满盒子，可见 ≈ 盒子高；
       F 批 0.53 填充比是站姿实测、输入姿态下失效，v6 起退役）与胶囊
       高之比 ∈ [1.10, 1.50]（规格约 1.3）；
    ② 压边：七仔底边压进胶囊顶缘 = 底边 - 胶囊顶 ∈ 七仔高 [5%, 20%]
       （规格约 10%，不是悬空、不是深埋）；
    ③ 底部对齐：推荐主行底边到胶囊顶 ∈ [4dp, 12dp]（规格 8dp，
       不顶七仔顶部——v6 原型的顶部对齐作废）；
    ④ 前面图层：dump 文档序中 pet_qizai 在 intent_capsule 之后（后画）；
    ⑤ 素行：主行无 pill 容器包裹；猜你想问行已删（v6 后调整，
       deck_guess_ 节点残留即 FAIL）；
    ⑥ 左右次序：推荐行左缘在七仔右缘之右；pet_qizai 全屏唯一。
    dp 换算：CI 模拟器腿统一 1080 宽、420dpi 级（宽约 411dp），密度取 W/411。
    （F 批的坐沿断言 pet-sits-on-edge 与 v4 的同行同带/紧凑间隙断言
    随 v6 站位退役。）
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
    guess_nodes = []
    for n in root.iter("node"):
        rid = n.get("resource-id") or ""
        b = bounds(n)
        if not b:
            continue
        if rid.startswith("deck_example_"):
            ex_nodes.append((rid, b))
        elif rid.startswith("deck_guess_"):
            guess_nodes.append((rid, b))
    examples = union([b for (_, b) in ex_nodes]) if ex_nodes else None
    # v6 后调整：猜你想问行已删，任何 deck_guess_ 节点残留即 FAIL。
    if guess_nodes:
        print(f"LAYOUT FAIL {state}: guess row residual deck_guess_ nodes present "
              f"({len(guess_nodes)} nodes, e.g. {guess_nodes[0][0]}) -- guess row must be deleted")
    else:
        print(f"LAYOUT OK {state}: no deck_guess_ nodes (guess row deleted)")
    if pet is None or cap is None:
        print(f"LAYOUT INFO {state}: pet/capsule absent, v6 assertions skipped")
    else:
        cap_h = cap[3] - cap[1]
        pet_h = pet[3] - pet[1]
        ratio = pet_h / float(cap_h) if cap_h > 0 else -1
        if 1.10 <= ratio <= 1.50:
            print(f"LAYOUT OK {state}: pet visible-height ratio={ratio:.3f} within [1.10,1.50]")
        else:
            print(f"LAYOUT FAIL {state}: pet visible-height ratio={ratio:.3f} outside [1.10,1.50] "
                  f"pet={pet} capsule={cap}")
        overlap = pet[3] - cap[1]
        ov_ratio = overlap / float(pet_h) if pet_h > 0 else -1
        if 0.05 <= ov_ratio <= 0.20:
            print(f"LAYOUT OK {state}: pet overlap into capsule={ov_ratio:.3f} within [0.05,0.20]")
        else:
            print(f"LAYOUT FAIL {state}: pet overlap into capsule={ov_ratio:.3f} outside [0.05,0.20] "
                  f"pet={pet} capsule={cap}")
        pet_idx = first_index(root, "pet_qizai")
        cap_idx = first_index(root, "intent_capsule")
        if pet_idx is not None and cap_idx is not None and pet_idx > cap_idx:
            print(f"LAYOUT OK {state}: pet drawn after capsule (front layer) "
                  f"pet_idx={pet_idx} capsule_idx={cap_idx}")
        else:
            print(f"LAYOUT FAIL {state}: pet NOT after capsule in document order "
                  f"(front layer violated) pet_idx={pet_idx} capsule_idx={cap_idx}")
        if examples is not None:
            if examples[0] >= pet[2] - EPS:
                print(f"LAYOUT OK {state}: examples start right of pet "
                      f"examples_left={examples[0]} pet_right={pet[2]}")
            else:
                print(f"LAYOUT FAIL {state}: examples start left of pet right edge "
                      f"examples={examples} pet={pet}")
            gap = dp(cap[1] - examples[3])
            if 4.0 <= gap <= 12.0:
                print(f"LAYOUT OK {state}: examples bottom to capsule top gap="
                      f"{gap:.1f}dp within [4,12] (bottom-aligned)")
            else:
                print(f"LAYOUT FAIL {state}: examples bottom to capsule top gap="
                      f"{gap:.1f}dp outside [4,12] examples={examples} capsule={cap}")
            check_no_pill_wrappers(root, state, "examples", ex_nodes, panel)
        else:
            print(f"LAYOUT INFO {state}: no deck_example_ nodes, main-row assertions skipped")
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
        # v6：输入区整体（七仔 + 推荐行）也须落在面板内
        for name, b in (("pet", pet), ("examples", examples)):
            if b is None:
                print(f"LAYOUT INFO {state}/{name}: absent, containment skipped")
                continue
            if _inside(b, panel):
                print(f"LAYOUT OK {state}: {name} inside panel {b} in {panel}")
            else:
                print(f"LAYOUT FAIL {state}: {name} escapes panel {b} not in {panel}")


def check_dock_pet(root, W, H, state):
    """v6 静态 Dock 栖坐几何（首页 dumps，dock_pet_cell 在场时）：
    ① 露出比例：（面板顶 - 七仔顶）/ 七仔高 ∈ [0.65, 0.85]（规格约 0.75）；
    ② 压边：（七仔底 - 面板顶）/ 七仔高 ∈ [0.15, 0.35]（规格约 0.25）；
    ③ 高比（仅两排面板：面板高 ≥160dp 时——D2 面板约 184dp、D1 约
       136dp，门槛取两者之间）：七仔高 / 面板高 ∈ [0.44, 0.60]
       （规格 0.52，v6 后调整再大一倍；一排面板矮、比值口径按两排
       面板定义，故跳过）；
    ④ 前面图层：dump 文档序中 dock_pet_cell 在 dock_panel 之后（后画）；
    ⑤ 居中：七仔中心 x 与面板中心 x 之差 ≤12dp。
    """
    els = home_elements(root)
    pet, dock = els.get("dockpet"), els.get("dock")
    if pet is None or dock is None:
        print(f"LAYOUT INFO {state}/dockpet: pet/panel absent, static assertions skipped")
        return
    density = W / 411.0 if W else 2.625

    def dp(px):
        return px / density

    pet_h = pet[3] - pet[1]
    dock_h = dock[3] - dock[1]
    exposure = (dock[1] - pet[1]) / float(pet_h) if pet_h > 0 else -1
    if 0.65 <= exposure <= 0.85:
        print(f"LAYOUT OK {state}: dockpet exposure={exposure:.3f} within [0.65,0.85]")
    else:
        print(f"LAYOUT FAIL {state}: dockpet exposure={exposure:.3f} outside [0.65,0.85] "
              f"pet={pet} dock={dock}")
    ov = (pet[3] - dock[1]) / float(pet_h) if pet_h > 0 else -1
    if 0.15 <= ov <= 0.35:
        print(f"LAYOUT OK {state}: dockpet overlap into panel={ov:.3f} within [0.15,0.35]")
    else:
        print(f"LAYOUT FAIL {state}: dockpet overlap into panel={ov:.3f} outside [0.15,0.35] "
              f"pet={pet} dock={dock}")
    if dp(dock_h) >= 160.0:
        ratio = pet_h / float(dock_h) if dock_h > 0 else -1
        if 0.44 <= ratio <= 0.60:
            print(f"LAYOUT OK {state}: dockpet height ratio={ratio:.3f} within [0.44,0.60]")
        else:
            print(f"LAYOUT FAIL {state}: dockpet height ratio={ratio:.3f} outside [0.44,0.60] "
                  f"pet={pet} dock={dock}")
    else:
        print(f"LAYOUT INFO {state}: dock height {dp(dock_h):.0f}dp < 160dp "
              f"(one-row panel; ratio defined vs two-row panel, skipped)")
    pet_idx = first_index(root, "dock_pet_cell")
    dock_idx = first_index(root, "dock_panel")
    if pet_idx is not None and dock_idx is not None and pet_idx > dock_idx:
        print(f"LAYOUT OK {state}: dockpet drawn after panel (front layer) "
              f"pet_idx={pet_idx} panel_idx={dock_idx}")
    else:
        print(f"LAYOUT FAIL {state}: dockpet NOT after panel in document order "
              f"(front layer violated) pet_idx={pet_idx} panel_idx={dock_idx}")
    pet_cx = (pet[0] + pet[2]) / 2.0
    dock_cx = (dock[0] + dock[2]) / 2.0
    if abs(dp(pet_cx - dock_cx)) <= 12.0:
        print(f"LAYOUT OK {state}: dockpet centered, center-x diff="
              f"{dp(pet_cx - dock_cx):.1f}dp")
    else:
        print(f"LAYOUT FAIL {state}: dockpet center-x diff="
              f"{dp(pet_cx - dock_cx):.1f}dp exceeds 12dp pet={pet} dock={dock}")


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
                check_dock_pet(r, W, H, f"home-{tpl}")
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
            check_dock_pet(r, W, H, "home-current")
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
