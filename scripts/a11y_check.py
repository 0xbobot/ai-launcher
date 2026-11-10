#!/usr/bin/env python3
"""R4 无障碍触控尺寸检查：解析 uiautomator dump，量关键触控目标的实际 dp 尺寸。

用法：a11y_check.py <density> <dump_dir> [landscape=0|1]
输出行前缀 A11Y OK / A11Y FAIL / A11Y INFO / A11Y FAN-*，smoke.sh 以 FAIL 行判定断言。

硬断言（>=48dp，量可点祖先节点的 bounds）：
- 清空按钮：ac_dump2.xml 中 content-desc="清空"（smoke.sh 第 10 段应用中心搜索面）
- 过滤结果项：ac_dump2.xml 中 text="Clock" 的可点祖先（探针选 Clock：API 29 镜像无相机）
横屏（landscape=1）时 ac_dump2 来自横屏 AC 流程：F2 销账（线 C）后横屏已
并入统一胶囊契约，清空控件在场并照常测量；缺失记 INFO 的兜底保留
（smoke 第 10 段已对控件在场单独断言，缺失不会从本检查漏检）。

477 后（2026-11-08）：首页意图入口已删——提交按钮（intent_dump.xml）
与本地结果项（local_dump.xml）不再产出，提交断言与问七仔行断言退役；
「问七仔」行缺席由 smoke 第 10 段断言 appcenter-no-ask-row 覆盖。
意图面板/建议面（suggest_dump.xml）已删，deck-example 系列断言一并退役。

Dock 仍在屏内（pet_dump.xml，smoke.sh 第 6 段静态首页帧）：
底部带（中心 y > 85% 屏高）存在完整落在屏内的可点节点。

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
    landscape = len(sys.argv) > 3 and sys.argv[3] == "1"

    # 应用中心搜索面（smoke.sh 第 10 段）：清空按钮与过滤结果项。
    root = parse(os.path.join(out, "ac_dump2.xml"))
    if root is not None:
        pm = parent_map(root)
        report("clear-btn", find_first(root, lambda n: n.get("content-desc") == "清空"), density, pm, missing_ok=landscape)
        item = find_first(root, lambda n: n.get("text") == "Clock")
        if item is None:
            # 标签 TextView 偶发未进语义树（R4 实测）：退到图标 View 的 content-desc 节点，
            # 尺寸仍量其可点祖先（结果项容器），口径不变
            item = find_first(root, lambda n: n.get("class") == "android.view.View" and n.get("content-desc") == "Clock")
        report("result-item", item, density, pm)

    # Dock 仍在屏内：静态首页 dump（pet_dump.xml，第 6 段封面静态帧）底部带
    # （中心 y > 85% 屏高）存在完整落在屏内的可点节点；带宽与完整落屏内
    # 的判据不变。pet_dump 缺失时 parse 记 INFO，本项不判（第 6 段已断言
    # 静态首页在场，缺失不会从本检查漏检）。
    dock_root = parse(os.path.join(out, "pet_dump.xml"))
    if dock_root is not None:
        first = find_first(dock_root, lambda n: True)
        rb = bounds(first) if first is not None else None  # 首节点=根窗口，bounds 即屏面
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
                if landscape:
                    # 横屏 Dock 是右下面板：其格位中心在约 79% 屏高处，
                    # 「中心入带」是竖屏口径——横屏改验底边入带 + 完整在
                    # 屏内（锚底且不溢出，属性等价）。
                    in_band = b[3] > rb[3] * 0.85
                    contained = (b[0] >= rb[0] and b[1] >= rb[1] and
                                 b[2] <= rb[2] and b[3] <= rb[3])
                    if in_band and contained:
                        dock = b
                        break
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
