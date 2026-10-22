#!/usr/bin/env python3
"""R4 无障碍触控尺寸检查：解析 uiautomator dump，量关键触控目标的实际 dp 尺寸。

用法：a11y_check.py <density> <dump_dir> [landscape=0|1]
输出行前缀 A11Y OK / A11Y FAIL / A11Y INFO / A11Y FAN-*，smoke.sh 以 FAIL 行判定断言。

硬断言（>=48dp，量可点祖先节点的 bounds）：
- 提交按钮：intent_dump.xml 中 content-desc="提交"
- 清空按钮：ac_dump2.xml 中 content-desc="清空"
- 问七仔行：ac_dump2.xml 中 text 含「问七仔」
- 本地结果项：local_dump.xml 中 text="Clock" 的可点祖先（探针选 Clock：API 29 镜像无相机）
横屏（landscape=1）时 ac_dump2 来自横屏旧搜索框流程，无清空/问七仔控件，
这两项缺失记 INFO 跳过（横屏未并入统一胶囊，R4 发现 F2），在场则照常测量。

意图改进一批追加（suggest_dump.xml，smoke.sh 第 15 段在收键盘后拉取，建议面在场）：
布局不变量——建议项触控 >=48dp（标题后第一个应用项的可点祖先）；胶囊占位节点
与建议面标题节点 bounds 不重叠；建议项完整落在屏内；Dock 仍在屏内（底部带内
存在完整落在屏内的可点节点）。任一不满足输出 A11Y FAIL。
v0.68.99（R-1）：Dock 在屏内一项改在静态主页 pet_dump.xml 上验（输入态 Dock
按设计让位，suggest_dump 底部带为空属设计内状态）——属性与判据不变。

只测量不判失败：
- 扇形段（fan_dump.xml）：扇形是 Canvas 手势面，统计可点节点；无段语义节点时
  输出 FAN-NO-SEMANTICS 记为发现项（TalkBack 可用性归真机验收）。
- liveRegion / stateDescription 不出现在 uiautomator dump 里，明确不在覆盖内。
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

MIN_DP = 48.0


def parse(path):
    try:
        return ET.parse(path).getroot()
    except Exception as e:  # dump 缺失/损坏只记 INFO，硬断言目标各自再报 MISSING
        print(f"A11Y INFO parse-failed {os.path.basename(path)}: {e}")
        return None


def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    return tuple(int(v) for v in m.groups())


def parent_map(root):
    return {c: p for p in root.iter() for c in p}


def find_first(root, pred):
    for n in root.iter("node"):
        if pred(n):
            return n
    return None


def clickable_ancestor(node, pmap):
    cur = node
    while cur is not None:
        if cur.get("clickable") == "true":
            return cur
        cur = pmap.get(cur)
    return node


def report(name, node, density, pmap, missing_ok=False):
    if node is None:
        if missing_ok:
            print(f"A11Y INFO {name}: skipped in landscape (control not present)")
        else:
            print(f"A11Y FAIL {name}: node missing in dump")
        return
    target = clickable_ancestor(node, pmap)
    b = bounds(target)
    if not b:
        print(f"A11Y FAIL {name}: node has no bounds")
        return
    w = (b[2] - b[0]) * 160.0 / density
    h = (b[3] - b[1]) * 160.0 / density
    verdict = "OK" if (w >= MIN_DP and h >= MIN_DP) else "FAIL"
    print(f"A11Y {verdict} {name}: {w:.1f}x{h:.1f}dp (min {MIN_DP:.0f}dp)")


def main():
    density = float(sys.argv[1])
    out = sys.argv[2]

    root = parse(os.path.join(out, "intent_dump.xml"))
    if root is not None:
        pm = parent_map(root)
        report("submit-btn", find_first(root, lambda n: n.get("content-desc") == "提交"), density, pm)

    landscape = len(sys.argv) > 3 and sys.argv[3] == "1"
    root = parse(os.path.join(out, "ac_dump2.xml"))
    if root is not None:
        pm = parent_map(root)
        report("clear-btn", find_first(root, lambda n: n.get("content-desc") == "清空"), density, pm, missing_ok=landscape)
        report("ask-row", find_first(root, lambda n: "问七仔" in (n.get("text") or "")), density, pm, missing_ok=landscape)

    root = parse(os.path.join(out, "local_dump.xml"))
    if root is not None:
        pm = parent_map(root)
        item = find_first(root, lambda n: n.get("text") == "Clock")
        if item is None:
            # 标签 TextView 偶发未进语义树（R4 实测）：退到图标 View 的 content-desc 节点，
            # 尺寸仍量其可点祖先（结果项容器），口径不变
            item = find_first(root, lambda n: n.get("class") == "android.view.View" and n.get("content-desc") == "Clock")
        report("result-item", item, density, pm)

    # 意图改进一批：建议面布局不变量（suggest_dump.xml，聚焦+空文本+收键盘后拉取）
    root = parse(os.path.join(out, "suggest_dump.xml"))
    if root is not None:
        pm = parent_map(root)
        first = find_first(root, lambda n: True)
        rb = bounds(first) if first is not None else None  # 首节点=根窗口，bounds 即屏面
        title = find_first(root, lambda n: n.get("text") == "七仔猜你现在想用")
        if title is None:
            print("A11Y FAIL suggest-panel: title node missing in suggest_dump")
        else:
            # 建议项：标题之后（文档序≈布局序）第一个带 content-desc 的节点
            seen_title = False
            item = None
            for n in root.iter("node"):
                if n is title:
                    seen_title = True
                    continue
                if seen_title and (n.get("content-desc") or ""):
                    item = n
                    break
            report("suggest-item", item, density, pm)
            # 胶囊（占位节点）与建议面标题不重叠
            cap = find_first(root, lambda n: n.get("text") == "想做什么？")
            tb = bounds(title)
            cb = bounds(cap) if cap is not None else None
            if tb and cb:
                overlap = not (cb[2] <= tb[0] or tb[2] <= cb[0] or
                               cb[3] <= tb[1] or tb[3] <= cb[1])
                print(f"A11Y {'FAIL' if overlap else 'OK'} suggest-overlap: "
                      f"capsule={cb} suggest-title={tb}")
            # 建议项完整落在屏内
            if rb and item is not None:
                ib = bounds(clickable_ancestor(item, pm))
                if ib:
                    inside = (ib[0] >= rb[0] and ib[1] >= rb[1] and
                              ib[2] <= rb[2] and ib[3] <= rb[3])
                    print(f"A11Y {'OK' if inside else 'FAIL'} suggest-in-bounds: "
                          f"item={ib} screen={rb}")
            # Dock 仍在屏内：底部带（中心 y > 85% 屏高）存在完整落在屏内的可点节点。
            # v0.68.99（R-1 断言迁移）：输入态下 Dock 按设计让位（胶囊进简报槽），
            # suggest_dump 的底部带按定义为空——「Dock 在屏内」这一属性改在静态
            # 主页 dump（pet_dump.xml，第 6 段封面静态帧，Dock 在场）上验，带宽
            # 与完整落屏内的判据不变；pet_dump 缺失时回退 suggest_dump 旧口径。
            dock_root = parse(os.path.join(out, "pet_dump.xml")) or root
            if rb:
                dock = None
                for n in dock_root.iter("node"):
                    if n.get("clickable") != "true":
                        continue
                    b = bounds(n)
                    if not b:
                        continue
                    w = (b[2] - b[0]) * 160.0 / density
                    h = (b[3] - b[1]) * 160.0 / density
                    if w > 300 and h > 300:  # 排除全屏根容器
                        continue
                    cy = (b[1] + b[3]) / 2.0
                    if cy > rb[3] * 0.85 and b[2] <= rb[2] and b[3] <= rb[3]:
                        dock = b
                        break
                if dock:
                    print(f"A11Y OK dock-in-bounds: bottom clickable node={dock} "
                          f"screen={rb}")
                else:
                    print("A11Y FAIL dock-in-bounds: no clickable node in bottom "
                          "band of static home dump")

    # 扇形：只测量，逐节点列出（smoke.sh 第 5b 段在按住窗口内 dump）。
    # 扇区为 Canvas 绘制，是否有语义节点、尺寸多大，由报告人工据此判定，不自动下结论。
    root = parse(os.path.join(out, "fan_dump.xml"))
    if root is None:
        print("A11Y INFO fan: dump unavailable (not measured)")
    else:
        nodes = []
        for n in root.iter("node"):
            if n.get("clickable") == "true":
                b = bounds(n)
                if b:
                    w = (b[2] - b[0]) * 160.0 / density
                    h = (b[3] - b[1]) * 160.0 / density
                    if not (w > 300 and h > 300):  # 排除全屏根容器
                        nodes.append((b, w, h, n.get("content-desc") or "", n.get("text") or ""))
        if not nodes:
            print("A11Y FAN-NO-SEMANTICS: no clickable nodes in fan dump "
                  "(canvas gesture surface) -> real-device TalkBack item")
        else:
            for b, w, h, desc, text in nodes[:14]:
                print(f"A11Y INFO fan-node: {w:.1f}x{h:.1f}dp bounds={b} desc='{desc}' text='{text}'")
            print(f"A11Y INFO fan: {len(nodes)} clickable nodes during drag (see fan-node lines)")

    print("A11Y INFO liveRegion/stateDescription: not exposed in uiautomator dump "
          "-> real-device TalkBack item")


if __name__ == "__main__":
    main()
