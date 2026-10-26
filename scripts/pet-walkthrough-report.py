#!/usr/bin/env python3
"""宠物走查 Loop P0：合并报告脚本。

输入：
  --numeric-dir  测试写出的 build/pet-walkthrough/report-*.json
  --record-dir   Paparazzi record 产物（app/src/test/snapshots/images/）
  --goldens-dir  已确认图像黄金（app/src/test/pet-walkthrough-goldens/，可为空）
  --out          输出报告 Markdown 路径
  --diff-dir     漂移项的可视化 diff 图输出目录
输出：报告 Markdown（通过／失败／新增 diff／注意／清单五栏），恒 exit 0
  （P0 是报告任务，不作门禁；失败只写进报告，不使工作流变红）。

像素 diff：PIL 可用时，逐像素 max 通道差 > 12 的像素占比；
  > 0.5% 记 fail，否则 pass；PIL 不可用时记 unknown（按新增 diff 计，不 fail）。
"""
import argparse
import glob
import json
import os
import sys

DIFF_PIXEL_THRESHOLD = 12
DIFF_FAIL_PERCENT = 0.5


def load_numeric(numeric_dir):
    items = []
    for path in sorted(glob.glob(os.path.join(numeric_dir, "report-*.json"))):
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
        except (OSError, json.JSONDecodeError):
            continue
        items.extend(data.get("items", []))
    return items


def ensure_pil():
    try:
        from PIL import Image  # noqa: F401
        return True
    except ImportError:
        pass
    try:
        import subprocess
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "--break-system-packages",
             "pillow"],
            check=False, capture_output=True, timeout=180,
        )
        from PIL import Image  # noqa: F401
        return True
    except Exception:
        return False


def pixel_diff_percent(record_path, golden_path):
    from PIL import Image, ImageChops
    a = Image.open(record_path).convert("RGB")
    b = Image.open(golden_path).convert("RGB")
    if a.size != b.size:
        return 100.0, None
    diff = ImageChops.difference(a, b)
    # C 级查表法：任一通道差 > 阈值即记为差异像素（避免逐像素 Python 循环）。
    table = [0] * (DIFF_PIXEL_THRESHOLD + 1) + [255] * (255 - DIFF_PIXEL_THRESHOLD)
    r, g, bl = diff.split()
    mask = ImageChops.lighter(ImageChops.lighter(r.point(table), g.point(table)),
                             bl.point(table))
    bad = mask.histogram()[255]
    w, h = mask.size
    return 100.0 * bad / (w * h), mask


def write_diff_image(mask, out_path):
    # 白色＝差异像素的二值图。
    mask.convert("L").save(out_path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--numeric-dir", required=True)
    ap.add_argument("--record-dir", required=True)
    ap.add_argument("--goldens-dir", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--diff-dir", required=True)
    args = ap.parse_args()

    numeric = load_numeric(args.numeric_dir)
    if not numeric:
        # 测试工作目录口径容差：模块目录 vs 仓库根目录。
        for alt in ("app/build/pet-walkthrough", "build/pet-walkthrough"):
            if alt != args.numeric_dir:
                numeric = load_numeric(alt)
                if numeric:
                    break
    pil_ok = ensure_pil()

    images = []
    if os.path.isdir(args.record_dir):
        for path in sorted(glob.glob(os.path.join(args.record_dir, "*.png"))):
            name = os.path.basename(path)
            golden = os.path.join(args.goldens_dir, name)
            if not os.path.exists(golden):
                images.append({"id": name, "status": "new", "detail": "no golden yet"})
                continue
            if not pil_ok:
                images.append({"id": name, "status": "new",
                               "detail": "PIL unavailable, diff unknown"})
                continue
            try:
                pct, diff = pixel_diff_percent(path, golden)
            except Exception as e:  # noqa: BLE001
                images.append({"id": name, "status": "new",
                               "detail": f"diff error: {e}"})
                continue
            if pct > DIFF_FAIL_PERCENT:
                os.makedirs(args.diff_dir, exist_ok=True)
                write_diff_image(diff, os.path.join(args.diff_dir, name))
                images.append({"id": name, "status": "fail",
                               "detail": f"pixel diff {pct:.2f}% > {DIFF_FAIL_PERCENT}%"})
            else:
                images.append({"id": name, "status": "pass",
                               "detail": f"pixel diff {pct:.2f}%"})

    def fmt_num(it):
        actual = it.get("actual")
        golden = it.get("golden")
        a = f"{actual:.4g}" if isinstance(actual, (int, float)) else "-"
        g = f"{golden:.4g}" if isinstance(golden, (int, float)) else "-"
        return f"| {it['id']} | {a} | {g} | {it.get('tol')} | {it.get('unit')} |"

    passed = [i for i in numeric if i["status"] == "pass"]
    failed = [i for i in numeric if i["status"] == "fail"]
    new = [i for i in numeric if i["status"] == "new"]
    notes = [i for i in numeric if i["status"] == "note"]
    attention = [i for i in numeric if i.get("attention")]
    img_pass = [i for i in images if i["status"] == "pass"]
    img_fail = [i for i in images if i["status"] == "fail"]
    img_new = [i for i in images if i["status"] == "new"]

    lines = []
    lines.append("# 宠物走查报告（P0，非阻塞）")
    lines.append("")
    lines.append(
        f"数值：通过 {len(passed)}／失败 {len(failed)}／新增 {len(new)}；"
        f"图像：通过 {len(img_pass)}／失败 {len(img_fail)}／新增 {len(img_new)}；"
        f"注意 {len(attention)} 项；清单 {len(notes)} 项。"
    )
    lines.append("")
    if failed or img_fail:
        lines.append("## 失败（相对黄金基线漂移）")
        lines.append("")
        lines.append("| id | actual | golden | tol | unit |")
        lines.append("|---|---|---|---|---|")
        for it in failed:
            lines.append(fmt_num(it))
        for it in img_fail:
            lines.append(f"| {it['id']} | - | - | - | {it['detail']} |")
        lines.append("")
    if new or img_new:
        lines.append("## 新增 diff（无基线，待确认）")
        lines.append("")
        lines.append("| id | actual | detail |")
        lines.append("|---|---|---|")
        for it in new:
            a = it.get("actual")
            a = f"{a:.4g}" if isinstance(a, (int, float)) else "-"
            lines.append(f"| {it['id']} | {a} {it.get('unit')} | 首次量测 |")
        for it in img_new:
            lines.append(f"| {it['id']} | - | {it['detail']} |")
        lines.append("")
    if attention:
        lines.append("## 注意（数值越界，P0 只量测不判 fail）")
        lines.append("")
        lines.append("| id | actual | unit | 说明 |")
        lines.append("|---|---|---|---|")
        for it in attention:
            a = it.get("actual")
            a = f"{a:.4g}" if isinstance(a, (int, float)) else "-"
            lines.append(f"| {it['id']} | {a} | {it.get('unit')} | 净空为负＝气泡探出屏顶 |")
        lines.append("")
    if passed or img_pass:
        lines.append("## 通过")
        lines.append("")
        lines.append("| id | actual | golden | tol | unit |")
        lines.append("|---|---|---|---|---|")
        for it in passed:
            lines.append(fmt_num(it))
        for it in img_pass:
            lines.append(f"| {it['id']} | - | - | - | {it['detail']} |")
        lines.append("")
    if notes:
        lines.append("## 清单（不判分）")
        lines.append("")
        for it in notes:
            lines.append(f"- {it['id']}：{it.get('detail', '')}")
        lines.append("")

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"report written: {args.out}")
    print(f"numeric pass={len(passed)} fail={len(failed)} new={len(new)} "
          f"images pass={len(img_pass)} fail={len(img_fail)} new={len(img_new)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
