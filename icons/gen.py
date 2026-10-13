# -*- coding: utf-8 -*-
"""用同一份幾何描述同時產生 SVG（預覽）與 VectorDrawable（實際 App 圖示）。"""
import math

def rr(l, t, r, b, rad):
    """圓角矩形的 path data。"""
    return (f"M{l+rad},{t} L{r-rad},{t} A{rad},{rad} 0 0 1 {r},{t+rad} "
            f"L{r},{b-rad} A{rad},{rad} 0 0 1 {r-rad},{b} "
            f"L{l+rad},{b} A{rad},{rad} 0 0 1 {l},{b-rad} "
            f"L{l},{t+rad} A{rad},{rad} 0 0 1 {l+rad},{t} Z")

def circ(cx, cy, r):
    return (f"M{cx-r},{cy} a{r},{r} 0 1 0 {2*r},0 a{r},{r} 0 1 0 {-2*r},0 Z")

def S(d, fill, rot=None, ft=None):
    return {"d": d, "fill": fill, "rot": rot, "ft": ft}

def G(x1, y1, x2, y2, c1, c2):
    return {"grad": (x1, y1, x2, y2, c1, c2)}

# ---------------- 三個方案 ----------------

def concept_a():
    """A：滑動牌堆 — 最上面那張正被滑開。"""
    bg = [S("M0,0 H108 V108 H0 Z", G(0, 0, 108, 108, "#2E3568", "#101219")),
          S("M0,108 L108,0 V40 L22,108 Z", "#FFFFFF0E")]
    fg = [
        S(rr(35, 33, 73, 75, 5), "#414A73", rot=(-19, 54, 55)),
        S(rr(34, 32, 74, 77, 5), "#636D9E", rot=(-9.5, 54, 55)),
        S(rr(33, 31, 75, 79, 6), G(33, 31, 75, 79, "#FFFFFF", "#DCE3F1"), rot=(7, 54, 55)),
        S(circ(44, 45.5, 4.5), G(40, 41, 48, 50, "#F6C25C", "#E5A034"), rot=(7, 54, 55)),
        S("M33.4,73 L46,57 L55,66.5 L62,59 L74.6,73 A6,6 0 0 1 69,79 H39 A6,6 0 0 1 33.4,73 Z",
          G(33, 57, 75, 79, "#45DCA6", "#23A278"), rot=(7, 54, 55)),
    ]
    return bg, fg

def concept_b():
    """B：整理完成 — 相片加上勾勾徽章。"""
    bg = [S("M0,0 H108 V108 H0 Z", G(108, 0, 0, 108, "#26335C", "#0C0E15")),
          S(circ(28, 24, 44), "#FFFFFF0C")]
    fg = [
        S(rr(36, 30, 74, 68, 5), "#4A5379", rot=(-10, 55, 50)),
        S(rr(31, 33, 75, 75, 6), G(31, 33, 75, 75, "#FFFFFF", "#DBE2F0")),
        S(circ(41.5, 45.5, 4.4), G(37, 41, 46, 50, "#F6C25C", "#E5A034")),
        S("M31.4,69 L44,54 L52.5,63 L59,55.5 L70,69 A6,6 0 0 1 69,75 H37.4 A6,6 0 0 1 31.4,69 Z",
          G(31, 54, 75, 75, "#45DCA6", "#23A278")),
        S(circ(68.5, 69, 12.2), "#0E1017"),
        S(circ(68.5, 69, 9.6), G(60, 60, 78, 78, "#4BE0AC", "#1E9E73")),
        S("M64.2,69.2 L67.6,72.6 L73.2,66.2", None),
    ]
    return bg, fg

def concept_c():
    """C：左右分流 — 把照片分成留下與丟掉兩堆。"""
    bg = [S("M0,0 H108 V108 H0 Z", G(0, 108, 108, 0, "#2E2856", "#0E1017")),
          S("M0,0 H108 V34 L0,70 Z", "#FFFFFF0A")]
    fg = [
        S(rr(31, 36, 59, 78, 5), G(31, 36, 59, 78, "#F6D3D0", "#DFA9A4"), rot=(-21, 45, 58)),
        S(circ(40, 50, 3.6), "#C9908B", rot=(-21, 45, 58)),
        S("M31.4,72 L40,61 L46,67 L50,62.5 L58.6,72 A5,5 0 0 1 54,78 H36 A5,5 0 0 1 31.4,72 Z",
          "#C9908B", rot=(-21, 45, 58)),
        S(rr(50, 34, 80, 78, 5), G(50, 34, 80, 78, "#FFFFFF", "#D6EBE2"), rot=(19, 65, 57)),
        S(circ(59, 47, 4), G(55, 43, 63, 51, "#F6C25C", "#E5A034"), rot=(19, 65, 57)),
        S("M50.4,72 L59,60 L65,67 L69.5,62 L79.6,72 A5,5 0 0 1 75,78 H55 A5,5 0 0 1 50.4,72 Z",
          G(50, 60, 80, 78, "#45DCA6", "#23A278"), rot=(19, 65, 57)),
    ]
    return bg, fg

def concept_c_mono():
    """C 的單色剪影：兩張卡明確分開，不重疊才不會糊成一團。"""
    right = (rr(57, 36, 82, 77, 5) + " " + circ(64.6, 48, 3.3))
    return [
        S(rr(27, 36, 51, 77, 5), None, rot=(-24, 39, 57)),
        S(right, "#FFFFFF", rot=(17, 70, 57), ft="evenOdd"),
    ]

CONCEPTS = {"A": concept_a, "B": concept_b, "C": concept_c}

# ---------------- 輸出 ----------------

def to_svg(shapes, prefix):
    defs, body, gi = [], [], 0
    for s in shapes:
        fill = s["fill"]
        if fill is None:
            stroke = ' fill="none" stroke="#FFFFFF" stroke-width="4.2" stroke-linecap="round" stroke-linejoin="round"'
            attr = stroke
        elif isinstance(fill, dict):
            gi += 1
            gid = f"{prefix}g{gi}"
            x1, y1, x2, y2, c1, c2 = fill["grad"]
            defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                        f'x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">'
                        f'<stop offset="0" stop-color="{c1}"/><stop offset="1" stop-color="{c2}"/>'
                        f'</linearGradient>')
            attr = f' fill="url(#{gid})"'
        else:
            if len(fill) == 9:
                attr = f' fill="{fill[:7]}" fill-opacity="{int(fill[7:],16)/255:.3f}"'
            else:
                attr = f' fill="{fill}"'
        if s.get("ft"):
            attr += ' fill-rule="evenodd"'
        p = f'<path d="{s["d"]}"{attr}/>'
        if s["rot"]:
            a, cx, cy = s["rot"]
            p = f'<g transform="rotate({a} {cx} {cy})">{p}</g>'
        body.append(p)
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
            + ("<defs>" + "".join(defs) + "</defs>" if defs else "") + "".join(body) + "</svg>")

def to_vector(shapes):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           '    android:width="108dp" android:height="108dp"',
           '    android:viewportWidth="108" android:viewportHeight="108">']
    for s in shapes:
        ind = "    "
        if s["rot"]:
            a, cx, cy = s["rot"]
            out.append(f'{ind}<group android:pivotX="{cx}" android:pivotY="{cy}" android:rotation="{a}">')
            ind = "        "
        fill = s["fill"]
        if fill is None:
            out.append(f'{ind}<path android:pathData="{s["d"]}"')
            out.append(f'{ind}    android:strokeColor="#FFFFFFFF" android:strokeWidth="4.2"')
            out.append(f'{ind}    android:strokeLineCap="round" android:strokeLineJoin="round" />')
        elif isinstance(fill, dict):
            x1, y1, x2, y2, c1, c2 = fill["grad"]
            out.append(f'{ind}<path android:pathData="{s["d"]}">')
            out.append(f'{ind}    <aapt:attr name="android:fillColor">')
            out.append(f'{ind}        <gradient android:type="linear"')
            out.append(f'{ind}            android:startX="{x1}" android:startY="{y1}"')
            out.append(f'{ind}            android:endX="{x2}" android:endY="{y2}"')
            out.append(f'{ind}            android:startColor="#FF{c1[1:]}" android:endColor="#FF{c2[1:]}" />')
            out.append(f'{ind}    </aapt:attr>')
            out.append(f'{ind}</path>')
        else:
            if len(fill) == 9:
                c = "#" + fill[7:] + fill[1:7]
            else:
                c = "#FF" + fill[1:]
            ftattr = ' android:fillType="evenOdd"' if s.get("ft") else ''
            out.append(f'{ind}<path android:pathData="{s["d"]}" android:fillColor="{c}"{ftattr} />')
        if s["rot"]:
            out.append('    </group>')
    out.append('</vector>')
    return "\n".join(out)
