#!/usr/bin/env python3
"""Packs the exercise pictograms in svg/ into the bundle the app ships.

The SVG files in svg/ are the source of the icons; draw_icons.py writes the first set, and a
designer may replace any of them by hand. The app does not read SVG. It reads this bundle,
which lists each icon's paths as plain data:

    {"version":1,"viewport":48,"icons":{"<key>":[{"d":"...","fill":true},
                                                  {"d":"...","stroke":3}, ...], ...}}

Each SVG must keep to a small subset so it converts exactly, and this script stops with an
error naming the file if one does not:
  - the root <svg> has viewBox="0 0 48 48", and only <path> elements inside it (no groups,
    transforms, styles, circles or rects: convert those to path data first);
  - each <path> is either filled, with fill="currentColor" and no stroke, or stroked, with
    stroke="currentColor", a numeric stroke-width, fill="none", and round caps and joins
    (stroke-linecap="round" stroke-linejoin="round"), which is how the app draws them;
  - all points lie inside the 48 x 48 viewBox.
Path data is normalised (explicit commands, rounded numbers) so the app's parser gets one form.

Usage:
    python3 tools/exercise-icons/pack_icons.py

Run it after any change to an SVG in svg/, or the app keeps showing the old icons. Re-running
on the same files gives the same output byte for byte.
"""

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
SVG_DIR = HERE / "svg"
OUTPUT = HERE.parents[1] / "app/shared/src/commonMain/composeResources/files/exercise_icons.json"

BUNDLE_VERSION = 1
VIEWPORT = 48
FALLBACK = "generic"   # the icon the app shows when nothing else matches; must exist

SVG_NS = "http://www.w3.org/2000/svg"
KEY = re.compile(r"[a-z][a-z0-9_]*")
ROOT_ATTRS = {"xmlns", "viewBox", "width", "height", "version", "fill"}
FILL_ATTRS = {"d", "fill", "fill-rule"}
STROKE_ATTRS = {"d", "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin"}

ARGS = {"M": 2, "L": 2, "T": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "A": 7, "Z": 0}
NUMBER = re.compile(r"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")
FLAG = re.compile(r"[01]")
SEPARATOR = re.compile(r"[\s,]*")


class IconError(Exception):
    pass


def fmt(v):
    v = round(v + 0.0, 3)
    if v == 0:
        return "0"
    return f"{v:.3f}".rstrip("0").rstrip(".")


def parse_path(d):
    """Splits path data into (command, [numbers]) segments, one per drawing command."""
    segments = []
    pos = 0
    command = None
    first = True

    def skip(p):
        return SEPARATOR.match(d, p).end()

    pos = skip(pos)
    while pos < len(d):
        c = d[pos]
        if c.upper() in ARGS:
            command = c
            pos = skip(pos + 1)
            if command.upper() == "Z":
                segments.append((command, []))
                continue
        elif command is None:
            raise IconError(f"path data must start with a command, found {d[pos:pos + 10]!r}")
        elif command.upper() == "Z":
            raise IconError(f"numbers after Z in path data near {d[pos:pos + 10]!r}")
        if first and command.upper() != "M":
            raise IconError("path data must start with M")
        first = False
        upper = command.upper()
        args = []
        for k in range(ARGS[upper]):
            pattern = FLAG if upper == "A" and k in (3, 4) else NUMBER
            m = pattern.match(d, pos)
            if not m:
                raise IconError(f"bad {command} arguments in path data near {d[pos:pos + 12]!r}")
            args.append(float(m.group()))
            pos = skip(m.end())
        segments.append((command, args))
        # A moveto followed by more pairs continues as lineto.
        if upper == "M":
            command = "l" if command == "m" else "L"
    if not segments:
        raise IconError("empty path data")
    return segments


def normalise(d, margin, where):
    """Returns canonical path data, checking every point stays inside the viewBox."""
    out = []
    x = y = sx = sy = 0.0

    def check(px, py):
        if not (margin - 1e-6 <= px <= VIEWPORT - margin + 1e-6
                and margin - 1e-6 <= py <= VIEWPORT - margin + 1e-6):
            raise IconError(f"{where}: point ({fmt(px)}, {fmt(py)}) is outside the "
                            f"{VIEWPORT} x {VIEWPORT} viewBox (allowing for the stroke)")

    for command, args in parse_path(d):
        upper = command.upper()
        rel = command.islower()
        ox, oy = (x, y) if rel else (0.0, 0.0)
        if upper == "Z":
            x, y = sx, sy
        elif upper == "H":
            x = args[0] + (x if rel else 0.0)
            check(x, y)
        elif upper == "V":
            y = args[0] + (y if rel else 0.0)
            check(x, y)
        elif upper == "A":
            if args[0] <= 0 or args[1] <= 0:
                raise IconError(f"{where}: arc with a zero or negative radius")
            x, y = args[5] + ox, args[6] + oy
            check(x, y)
        else:
            # Only the end point lies on the curve; control points may sit outside.
            pts = [(args[k] + ox, args[k + 1] + oy) for k in range(0, len(args), 2)]
            x, y = pts[-1]
            check(x, y)
            if upper == "M":
                sx, sy = x, y
        out.append(command + " ".join(fmt(a) for a in args))
    return "".join(out)


def number(text, what, where):
    if text is None or not NUMBER.fullmatch(text.strip()):
        raise IconError(f"{where}: {what} must be a plain number, got {text!r}")
    v = float(text)
    if v <= 0:
        raise IconError(f"{where}: {what} must be positive")
    return int(v) if v == int(v) else v


def convert(path):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        raise IconError(f"not valid XML: {e}")
    if root.tag != f"{{{SVG_NS}}}svg":
        raise IconError("root element must be <svg> in the SVG namespace")
    for name, value in root.attrib.items():
        if name not in ROOT_ATTRS:
            raise IconError(f"unsupported attribute {name!r} on <svg>")
        if name == "fill" and value != "none":
            raise IconError('the only fill allowed on <svg> is fill="none"')
    viewbox = [v for v in re.split(r"[\s,]+", root.get("viewBox", "").strip()) if v]
    if [float(v) for v in viewbox] != [0, 0, VIEWPORT, VIEWPORT] if viewbox else True:
        raise IconError(f'viewBox must be "0 0 {VIEWPORT} {VIEWPORT}"')
    if (root.text or "").strip():
        raise IconError("text inside <svg>")

    elements = []
    for n, el in enumerate(root, 1):
        where = f"path {n}"
        if el.tag != f"{{{SVG_NS}}}path":
            tag = el.tag.split("}")[-1]
            raise IconError(f"element {n} is <{tag}>; only <path> is allowed (convert it to path data)")
        if len(el) or (el.text or "").strip():
            raise IconError(f"{where}: <path> must be empty")
        a = el.attrib
        d = a.get("d", "")
        stroke = a.get("stroke")
        fill = a.get("fill")
        if stroke is None or stroke == "none":
            extra = set(a) - FILL_ATTRS - ({"stroke"} if stroke == "none" else set())
            if extra:
                raise IconError(f"{where}: unsupported attribute(s) on a filled path: {sorted(extra)}")
            if fill != "currentColor":
                raise IconError(f'{where}: a filled path needs fill="currentColor", got {fill!r}')
            if a.get("fill-rule", "nonzero") != "nonzero":
                raise IconError(f"{where}: only the nonzero fill rule is supported")
            elements.append({"d": normalise(d, 0, where), "fill": True})
        else:
            extra = set(a) - STROKE_ATTRS
            if extra:
                raise IconError(f"{where}: unsupported attribute(s) on a stroked path: {sorted(extra)}")
            if stroke != "currentColor":
                raise IconError(f'{where}: stroke must be "currentColor", got {stroke!r}')
            if fill != "none":
                raise IconError(f'{where}: a stroked path needs fill="none", got {fill!r}')
            for cap in ("stroke-linecap", "stroke-linejoin"):
                if a.get(cap) != "round":
                    raise IconError(f'{where}: needs {cap}="round" (the app draws round caps and joins)')
            width = number(a.get("stroke-width"), "stroke-width", where)
            elements.append({"d": normalise(d, width / 2, where), "stroke": width})
    if not elements:
        raise IconError("no paths")
    return elements


def main():
    files = sorted(SVG_DIR.glob("*.svg"))
    if not files:
        sys.exit(f"No SVG files in {SVG_DIR}. Run draw_icons.py first.")
    icons = {}
    for path in files:
        key = path.stem
        if not KEY.fullmatch(key):
            sys.exit(f"{path.name}: file names must be lower_snake_case icon keys")
        try:
            icons[key] = convert(path)
        except IconError as e:
            sys.exit(f"{path.name}: {e}")
    if FALLBACK not in icons:
        sys.exit(f"Missing {FALLBACK}.svg, the icon shown when no other icon fits.")

    bundle = {"version": BUNDLE_VERSION, "viewport": VIEWPORT, "icons": {k: icons[k] for k in sorted(icons)}}
    text = json.dumps(bundle, ensure_ascii=False, separators=(",", ":")) + "\n"
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(text, encoding="utf-8")
    print(f"Packed {len(icons)} icons into {OUTPUT} ({len(text.encode())} bytes)")


if __name__ == "__main__":
    main()
