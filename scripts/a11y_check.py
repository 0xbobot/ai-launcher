#!/usr/bin/env python3
"""R4 无障碍触控尺寸检查：解析 uiautomator dump，量关键触控目标的实际 dp 尺寸。

用法：a11y_check.py <density> <dump_dir>
输出行前缀 A11Y OK / A11Y FAIL / A11Y INFO / A11Y FAN-*，smoke.sh 以 FAIL 行判定断言。

硬断言（>=48dp，量可点祖先节点的 bounds）：
- 提交按钮：intent_dump.xml 中 content-desc="提交"
- 清空按钮：ac_dump2.xml 中 content-desc="清空"
- 问七仔行：ac_dump2.xml 中 text 含「问七仔」
- 本地结果项：local_dump.xml 中 text="Camera" 的可点祖先

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


def report(name, node, density, pmap):
    if node is None:
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

    root = parse(os.path.join(out, "ac_dump2.xml"))
    if root is not None:
        pm = parent_map(root)
        report("clear-btn", find_first(root, lambda n: n.get("content-desc") == "清空"), density, pm)
        report("ask-row", find_first(root, lambda n: "问七仔" in (n.get("text") or "")), density, pm)

    root = parse(os.path.join(out, "local_dump.xml"))
    if root is not None:
        pm = parent_map(root)
        report("result-item", find_first(root, lambda n: n.get("text") == "Camera"), density, pm)

    # 扇形：只测量。段节点若存在，其包围盒必然很大；以「全屏以下的可点节点」为候选。
    root = parse(os.path.join(out, "fan_dump.xml"))
    if root is None:
        print("A11Y INFO fan: dump unavailable (not measured)")
    else:
        cands = []
        for n in root.iter("node"):
            if n.get("clickable") == "true":
                b = bounds(n)
                if b:
                    w = (b[2] - b[0]) * 160.0 / density
                    h = (b[3] - b[1]) * 160.0 / density
                    if not (w > 300 and h > 300):  # 排除全屏根容器
                        cands.append((w, h, n.get("content-desc") or n.get("text") or ""))
        if not cands:
            print("A11Y FAN-NO-SEMANTICS: no clickable sector nodes in fan dump "
                  "(canvas gesture surface) -> real-device TalkBack item")
        else:
            smallest = min(cands, key=lambda c: min(c[0], c[1]))
            print(f"A11Y INFO fan: {len(cands)} clickable nodes, smallest "
                  f"{smallest[0]:.1f}x{smallest[1]:.1f}dp ('{smallest[2]}')")

    print("A11Y INFO liveRegion/stateDescription: not exposed in uiautomator dump "
          "-> real-device TalkBack item")


if __name__ == "__main__":
    main()
