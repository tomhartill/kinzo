#!/usr/bin/env python3
"""Draws the exercise pictograms shown next to each exercise in the app.

There is one pictogram per movement pattern (squat, row, curl and so on), not one per
exercise. Each is a friendly stick figure in a fixed style: a 48 x 48 viewBox, one colour
(currentColor), a filled round head, and round-capped strokes for the body and the equipment.
Every pose is built from the same skeleton, so heads, limbs and stroke weights match across
the set.

The SVG files this writes are the editable source of the icons. A designer can replace any
of them by hand, as long as the file keeps to the rules pack_icons.py checks: only <path>
elements, each either filled with currentColor or stroked with currentColor. Once a file has
been replaced by hand, stop running this script, or it will overwrite the replacement.

Usage:
    python3 tools/exercise-icons/draw_icons.py
    python3 tools/exercise-icons/pack_icons.py

The first command rewrites svg/<key>.svg for every key below. The second packs all the SVGs
into the bundle the app reads, and must be run after any change to an SVG. Re-running gives
the same output byte for byte.

Coordinates are in viewBox units with y pointing down. Angles are in degrees on screen:
0 points right, 90 down, 180 left and -90 up. Side-view figures face right.
"""

import math
import sys
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent / "svg"

VIEWPORT = 48
LINE = 3        # body, limbs and equipment
THIN = 2        # ground lines, cables, ropes and motion arrows; the only other stroke width
ARROW_HEAD = 3.5  # length of each arm of an arrow's chevron, long enough to read at 33dp
# Every icon with a floor draws it at this height and is only centred sideways, so floors
# line up from row to row in the app's lists. Icons without a floor are centred both ways.
GROUND_Y = 44   # centre of the ground line; feet standing on it have their ankle at FOOT_Y
FOOT_Y = 41.5

# Skeleton proportions, shared by every figure.
HEAD_R = 3.5
NECK = 6.0       # shoulder point to head centre
TORSO = 10.5     # hip to shoulder
UPPER_ARM = 7.0
FOREARM = 6.5
THIGH = 8.5
SHIN = 8.5
FOOT = 3.0


# ---------------------------------------------------------------- geometry

def fmt(v):
    v = round(v + 0.0, 2)
    if v == 0:
        return "0"
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return s


def P(p):
    return f"{fmt(p[0])} {fmt(p[1])}"


def polar(p, angle, length):
    a = math.radians(angle)
    return (p[0] + length * math.cos(a), p[1] + length * math.sin(a))


def add(p, q, k=1.0):
    return (p[0] + k * q[0], p[1] + k * q[1])


def dist(p, q):
    return math.hypot(q[0] - p[0], q[1] - p[1])


def angle_of(p, q):
    return math.degrees(math.atan2(q[1] - p[1], q[0] - p[0]))


DIRECTIONS = {"up": (0, -1), "down": (0, 1), "left": (-1, 0), "right": (1, 0)}


def two_bone(root, target, a, b, bend):
    """Middle joint of a limb with segments a and b reaching from root to target.

    bend says which way the joint points: "up", "down", "left", "right" or a vector.
    """
    d = dist(root, target)
    if d > a + b + 0.05 or d < abs(a - b) - 0.05:
        raise ValueError(f"limb cannot reach {target} from {root} (distance {d:.2f})")
    d = min(max(d, abs(a - b) + 1e-6), a + b)
    ux, uy = (target[0] - root[0]) / d, (target[1] - root[1]) / d
    x = (a * a - b * b + d * d) / (2 * d)
    h = math.sqrt(max(a * a - x * x, 0.0))
    base = (root[0] + ux * x, root[1] + uy * x)
    c1 = (base[0] - uy * h, base[1] + ux * h)
    c2 = (base[0] + uy * h, base[1] - ux * h)
    v = DIRECTIONS[bend] if isinstance(bend, str) else bend
    return c1 if (c1[0] * v[0] + c1[1] * v[1]) >= (c2[0] * v[0] + c2[1] * v[1]) else c2


# ---------------------------------------------------------------- shapes
#
# Shapes are kept as segment lists until the icon is written, so the whole drawing can be
# centred in the viewBox first. A segment is one of ("M", p), ("L", p), ("Q", c, p),
# ("C", c1, c2, p), ("A", r, large, sweep, p) (a circular arc), ("E", rx, ry, large, sweep, p)
# (an elliptical arc with level axes) or ("Z",), all in absolute coordinates.

def circle_segs(c, r):
    x, y = c
    return [("M", (x - r, y)), ("A", r, 1, 1, (x + r, y)), ("A", r, 1, 1, (x - r, y)), ("Z",)]


def rrect_segs(c, w, h, r, angle=0.0):
    """Rounded rectangle centred on c, w along the angle, h across it. Clockwise."""
    r = min(r, w / 2, h / 2)
    a = math.radians(angle)
    ca, sa = math.cos(a), math.sin(a)

    def T(x, y):
        return (c[0] + x * ca - y * sa, c[1] + x * sa + y * ca)

    hw, hh = w / 2, h / 2
    return [("M", T(-hw + r, -hh)), ("L", T(hw - r, -hh)), ("A", r, 0, 1, T(hw, -hh + r)),
            ("L", T(hw, hh - r)), ("A", r, 0, 1, T(hw - r, hh)),
            ("L", T(-hw + r, hh)), ("A", r, 0, 1, T(-hw, hh - r)),
            ("L", T(-hw, -hh + r)), ("A", r, 0, 1, T(-hw + r, -hh)), ("Z",)]


def polyline_segs(points):
    return [("M", points[0])] + [("L", p) for p in points[1:]]


def segs_d(segs, dx=0.0, dy=0.0):
    """Path data for a segment list, moved by (dx, dy)."""
    def Q(p):
        return P((p[0] + dx, p[1] + dy))
    out = []
    for seg in segs:
        kind = seg[0]
        if kind in "ML":
            out.append(kind + Q(seg[1]))
        elif kind == "Q":
            out.append(f"Q{Q(seg[1])} {Q(seg[2])}")
        elif kind == "C":
            out.append(f"C{Q(seg[1])} {Q(seg[2])} {Q(seg[3])}")
        elif kind == "A":
            out.append(f"A{fmt(seg[1])} {fmt(seg[1])} 0 {seg[2]} {seg[3]} {Q(seg[4])}")
        elif kind == "E":
            out.append(f"A{fmt(seg[1])} {fmt(seg[2])} 0 {seg[3]} {seg[4]} {Q(seg[5])}")
        else:
            out.append("Z")
    return "".join(out)


def arc_points(p0, r, large, sweep, p1, n=24):
    """Points along an SVG circular arc, for measuring its extent."""
    x1, y1 = (p0[0] - p1[0]) / 2, (p0[1] - p1[1]) / 2
    d2 = x1 * x1 + y1 * y1
    if d2 == 0:
        return [p0]
    r = max(r, math.sqrt(d2))
    coef = math.sqrt(max(r * r - d2, 0.0) / d2) * (1 if large != sweep else -1)
    cx, cy = coef * y1, -coef * x1
    centre = (cx + (p0[0] + p1[0]) / 2, cy + (p0[1] + p1[1]) / 2)
    t0 = math.atan2((y1 - cy) / r, (x1 - cx) / r)
    t1 = math.atan2((-y1 - cy) / r, (-x1 - cx) / r)
    sweep_angle = t1 - t0
    if sweep and sweep_angle < 0:
        sweep_angle += 2 * math.pi
    if not sweep and sweep_angle > 0:
        sweep_angle -= 2 * math.pi
    return [(centre[0] + r * math.cos(t0 + sweep_angle * k / n),
             centre[1] + r * math.sin(t0 + sweep_angle * k / n)) for k in range(n + 1)]


def segs_points(segs):
    """Points along a segment list, close enough to measure its bounding box."""
    pts, cur, start = [], (0.0, 0.0), (0.0, 0.0)
    for seg in segs:
        kind = seg[0]
        if kind == "M":
            cur = start = seg[1]
            pts.append(cur)
        elif kind == "L":
            cur = seg[1]
            pts.append(cur)
        elif kind == "Q":
            c, p = seg[1], seg[2]
            pts += [((1 - t) ** 2 * cur[0] + 2 * (1 - t) * t * c[0] + t * t * p[0],
                     (1 - t) ** 2 * cur[1] + 2 * (1 - t) * t * c[1] + t * t * p[1])
                    for t in (k / 16 for k in range(17))]
            cur = p
        elif kind == "C":
            c1, c2, p = seg[1], seg[2], seg[3]
            pts += [((1 - t) ** 3 * cur[0] + 3 * (1 - t) ** 2 * t * c1[0] + 3 * (1 - t) * t * t * c2[0] + t ** 3 * p[0],
                     (1 - t) ** 3 * cur[1] + 3 * (1 - t) ** 2 * t * c1[1] + 3 * (1 - t) * t * t * c2[1] + t ** 3 * p[1])
                    for t in (k / 24 for k in range(25))]
            cur = p
        elif kind == "A":
            pts += arc_points(cur, seg[1], seg[2], seg[3], seg[4])
            cur = seg[4]
        elif kind == "E":
            rx, ry, p = seg[1], seg[2], seg[5]
            k = rx / ry
            pts += [(x, y / k) for x, y in arc_points((cur[0], cur[1] * k), rx, seg[3], seg[4], (p[0], p[1] * k))]
            cur = p
        else:
            cur = start
    return pts


# Signed distance functions: negative inside a shape, positive outside. They let a shape
# drawn in front leave a gap in the lines behind it, so overlaps read as depth, not clutter.

def sd_circle(c, r):
    return lambda p: dist(p, c) - r


def sd_box(c, w, h, r=0.0, angle=0.0):
    a = math.radians(angle)
    ca, sa = math.cos(a), math.sin(a)

    def sd(p):
        x, y = p[0] - c[0], p[1] - c[1]
        qx, qy = abs(x * ca + y * sa) - w / 2 + r, abs(-x * sa + y * ca) - h / 2 + r
        return math.hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - r
    return sd


def sd_segment(a, b, r):
    def sd(p):
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = dx * dx + dy * dy
        t = 0.0 if L == 0 else max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / L))
        return dist(p, (a[0] + t * dx, a[1] + t * dy)) - r
    return sd


def clip_polyline(points, visible):
    """Splits a polyline into the pieces where visible(point) holds."""
    pieces, cur = [], []

    def at(a, b, t):
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

    for a, b in zip(points, points[1:]):
        n = max(4, int(dist(a, b) / 0.05))
        prev_t, prev_v = 0.0, visible(a)
        if prev_v and not cur:
            cur = [a]
        for k in range(1, n + 1):
            t = k / n
            v = visible(at(a, b, t))
            if v != prev_v:
                lo, hi = prev_t, t
                for _ in range(30):
                    mid = (lo + hi) / 2
                    if visible(at(a, b, mid)) == prev_v:
                        lo = mid
                    else:
                        hi = mid
                q = at(a, b, (lo + hi) / 2)
                if prev_v:
                    cur.append(q)
                    pieces.append(cur)
                    cur = []
                else:
                    cur = [q]
            prev_t, prev_v = t, v
        if prev_v:
            cur.append(b)
    if cur:
        pieces.append(cur)
    out = []
    for piece in pieces:
        pts = [piece[0]]
        for q in piece[1:]:
            if dist(q, pts[-1]) > 1e-6:
                pts.append(q)
        if sum(dist(u, v) for u, v in zip(pts, pts[1:])) > 0.3:
            out.append(pts)
    return out


GAP = 1.2       # clear space between a shape and a line passing behind it
PADDING = 3.0   # least space between the drawing and the edge of the viewBox


class Icon:
    """Collects strokes and fills in drawing order, then writes them as SVG paths.

    Straight lines can be cut: in_front(mark) makes everything drawn since mark leave a gap
    in the straight lines drawn before it. The finished drawing is centred in the viewBox,
    except that a drawing with a floor keeps the floor at GROUND_Y and is only centred sideways.
    """

    def __init__(self):
        # Each item is [kind, width, data, sd]. kind "lines": data is a list of polylines
        # (cuttable). kind "stroke", "fill" or "ground": data is a segment list. width is None
        # for fills.
        self.items = []
        self.grounded = False

    def mark(self):
        return len(self.items)

    def line(self, *points, width=LINE, cut=True):
        """A polyline. cut=False keeps it whole when something is drawn in front of it."""
        caps = [sd_segment(a, b, width / 2) for a, b in zip(points, points[1:])]
        sd = lambda p: min(c(p) for c in caps)
        if cut:
            self.items.append(["lines", width, [list(points)], sd])
        else:
            self.items.append(["stroke", width, polyline_segs(points), sd])

    def stroke(self, segs, width=LINE, sd=None):
        self.items.append(["stroke", width, segs, sd])

    def arc(self, start, end, r, large=0, sweep=1, width=LINE):
        self.stroke([("M", start), ("A", r, large, sweep, end)], width)

    def ring(self, c, r, width=LINE):
        self.stroke(circle_segs(c, r), width, sd_circle(c, r + width / 2))

    def dot(self, c, r):
        self.items.append(["fill", None, circle_segs(c, r), sd_circle(c, r)])

    def block(self, c, w, h, r=1.0, angle=0.0):
        self.items.append(["fill", None, rrect_segs(c, w, h, r, angle), sd_box(c, w, h, r, angle)])

    def ground(self, x0=4, x1=44):
        """The floor, at the fixed height GROUND_Y; it is not moved when the drawing is centred."""
        self.grounded = True
        self.items.append(["ground", THIN, polyline_segs([(x0, GROUND_Y), (x1, GROUND_Y)]), None])

    def in_front(self, mark, gap=GAP, since=0):
        """Puts everything drawn since mark in front of the straight lines drawn before it.

        since: only lines drawn from this mark on are cut, so a limb can pass in front of a
        bar without also cutting the body it joins.
        """
        shapes = [it[3] for it in self.items[mark:] if it[3] is not None]
        if not shapes:
            return
        for it in self.items[since:mark]:
            if it[0] == "lines":
                clear = gap + it[1] / 2
                visible = lambda p: min(sd(p) for sd in shapes) >= clear
                it[2] = [piece for pts in it[2] for piece in clip_polyline(pts, visible)]

    def paths(self):
        """(kind, width, segment list) for every visible piece."""
        for kind, width, data, _ in self.items:
            if kind == "lines":
                for pts in data:
                    yield "stroke", width, polyline_segs(pts)
            else:
                yield kind, width, data

    def bounds(self, floor=True):
        x0 = y0 = math.inf
        x1 = y1 = -math.inf
        for kind, width, segs in self.paths():
            if kind == "ground" and not floor:
                continue
            pad = 0 if width is None else width / 2
            for x, y in segs_points(segs):
                x0, y0, x1, y1 = min(x0, x - pad), min(y0, y - pad), max(x1, x + pad), max(y1, y + pad)
        return x0, y0, x1, y1

    def svg(self):
        x0, y0, x1, y1 = self.bounds(floor=False)
        dx = VIEWPORT / 2 - (x0 + x1) / 2
        dy = 0.0 if self.grounded else VIEWPORT / 2 - (y0 + y1) / 2
        lo, hi = PADDING - 0.05, VIEWPORT - PADDING + 0.05
        if x0 + dx < lo or x1 + dx > hi or y0 + dy < lo or y1 + dy > hi:
            raise ValueError(f"drawing spans x {x0 + dx:.1f}..{x1 + dx:.1f}, y {y0 + dy:.1f}..{y1 + dy:.1f}, "
                             f"outside the {fmt(PADDING)} unit padding")
        strokes, fills = {}, []
        for kind, width, segs in self.paths():
            if kind == "fill":
                fills.append(segs_d(segs, dx, dy))
            elif kind == "ground":
                strokes.setdefault(width, []).append(segs_d(segs))
            else:
                strokes.setdefault(width, []).append(segs_d(segs, dx, dy))
        lines = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {VIEWPORT} {VIEWPORT}" '
                 f'width="{VIEWPORT}" height="{VIEWPORT}">']
        for width in sorted(strokes, reverse=True):
            lines.append(f'  <path d="{"".join(strokes[width])}" fill="none" stroke="currentColor" '
                         f'stroke-width="{fmt(width)}" stroke-linecap="round" stroke-linejoin="round"/>')
        if fills:
            lines.append(f'  <path d="{"".join(fills)}" fill="currentColor"/>')
        lines.append("</svg>")
        return "\n".join(lines) + "\n"


class Figure:
    """A stick figure built from the shared skeleton.

    hip: hip joint. torso: angle from hip to shoulder. head: angle from shoulder to the head
    centre (defaults to the torso angle). shoulders: half-width of the shoulder line for
    front-view figures; arms then start at its ends instead of the shoulder point.
    """

    def __init__(self, icon, hip, torso=-90.0, head=None, shoulders=0.0):
        self.icon = icon
        self.hip = hip
        self.shoulder = polar(hip, torso, TORSO)
        self.head = polar(self.shoulder, torso if head is None else head, NECK)
        icon.line(hip, self.shoulder)
        icon.dot(self.head, HEAD_R)
        self.roots = [self.shoulder, self.shoulder]
        if shoulders:
            left = (self.shoulder[0] - shoulders, self.shoulder[1])
            right = (self.shoulder[0] + shoulders, self.shoulder[1])
            icon.line(left, right)
            self.roots = [left, right]

    def _limb(self, root, a, b, to, bend, angles, extra):
        if to is not None:
            joint = two_bone(root, to, a, b, bend)
            end = to
        else:
            joint = polar(root, angles[0], a)
            end = polar(joint, angles[1], b)
        pts = [root, joint, end]
        if extra is not None:
            pts.append(extra(end))
        self.icon.line(*pts)
        return joint, end

    def arm(self, to=None, bend="down", angles=None, side=0, root=None):
        """Draws an arm; returns (elbow, hand). side picks the shoulder end on front views."""
        start = root or self.roots[side]
        return self._limb(start, UPPER_ARM, FOREARM, to, bend, angles, None)

    def leg(self, to=None, bend="right", angles=None, foot=None, root=None):
        """Draws a leg; returns (knee, ankle). foot is the angle from ankle to toes."""
        start = root or self.hip
        extra = None if foot is None else (lambda end: polar(end, foot, FOOT))
        return self._limb(start, THIGH, SHIN, to, bend, angles, extra)


# ---------------------------------------------------------------- equipment

def barbell(icon, center, half=11.0, angle=0.0, plate=(3.0, 9.0), sleeve=3.0, front=True):
    """A bar drawn in the picture plane, with a plate near each end.

    front: the plates pass in front of the lines drawn before them.
    """
    mark = icon.mark()
    for k in (-1, 1):
        icon.block(polar(center, angle, k * (half - sleeve)), plate[0], plate[1], 1.0, angle)
    if front:
        icon.in_front(mark)
    icon.line(polar(center, angle, -half), polar(center, angle, half))


def dumbbell(icon, center, angle=0.0, half=3.5, plate=(2.6, 6.5), front=True):
    mark = icon.mark()
    for k in (-1, 1):
        icon.block(polar(center, angle, k * half), plate[0], plate[1], 1.0, angle)
    if front:
        icon.in_front(mark)
    icon.line(polar(center, angle, -half), polar(center, angle, half))


KB_R = 4.5        # kettlebell body radius
KB_BASE = 0.72    # the flat base sits this far below the body centre, as a fraction of KB_R
KB_HANDLE = 2.6   # half-width of the handle loop (to its centre line)
KB_RISE = 3.6     # how far the top of the handle loop rises above the body


def kettlebell(icon, grip, angle=90.0, front=True):
    """A kettlebell held by the top of its handle at grip, its body hanging towards angle.

    The body is a filled ball with a flat base; the handle is an open loop above it, so the
    bell does not read as a ball. front: the bell passes in front of the lines drawn before it.
    """
    rot = math.radians(angle - 90)
    ca, sa = math.cos(rot), math.sin(rot)
    apex = -(KB_R + KB_RISE)          # top of the handle, in the bell's own frame (y down)

    def T(x, y):
        y -= apex
        return (grip[0] + x * ca - y * sa, grip[1] + x * sa + y * ca)

    mark = icon.mark()
    base_y = KB_R * KB_BASE
    base_x = math.sqrt(KB_R ** 2 - base_y ** 2)
    body = [("M", T(-base_x, base_y)), ("A", KB_R, 1, 1, T(base_x, base_y)), ("Z",)]
    icon.items.append(["fill", None, body, sd_circle(T(0, 0), KB_R)])
    leg_y = -math.sqrt(KB_R ** 2 - KB_HANDLE ** 2) + 0.4    # where the loop meets the body
    top = apex + KB_HANDLE
    loop = [("M", T(-KB_HANDLE, leg_y)), ("L", T(-KB_HANDLE, top)),
            ("A", KB_HANDLE, 0, 1, T(KB_HANDLE, top)), ("L", T(KB_HANDLE, leg_y))]
    icon.stroke(loop, THIN, sd_circle(T(0, apex + KB_HANDLE), KB_HANDLE + THIN / 2))
    if front:
        icon.in_front(mark)


def bench(icon, x0, x1, y, legs=None, floor=GROUND_Y - 1):
    """A flat bench: a top and legs down to the floor."""
    icon.line((x0, y), (x1, y))
    for x in legs or (x0 + 2, x1 - 2):
        icon.line((x, y), (x, floor))


def pull_bar(icon, x0, x1, y):
    icon.line((x0, y), (x1, y))


def pulley(icon, wheel, handle, r=2.2):
    """A pulley wheel and the cable running from it to a handle."""
    icon.dot(wheel, r)
    icon.line(wheel, handle, width=THIN)


def pad(icon, center, w=5.0, h=3.5, angle=0.0):
    """A machine pad (seat, roller or foot plate)."""
    icon.block(center, w, h, min(w, h) / 2, angle)


BALL_R = 4.0     # medicine ball: a ring this big at the thin weight, plainly hollow and
                 # plainly bigger than the solid head


def ball(icon, center):
    icon.ring(center, BALL_R, THIN)


ROLLER_R = 3.5   # foam roller seen end on: a ring with a hole of radius 2.5


def foam_roller(icon, center):
    icon.ring(center, ROLLER_R, THIN)


def chevron(icon, tip, direction, head=ARROW_HEAD, width=THIN):
    """An open arrow head with its point at tip, pointing towards direction (degrees)."""
    icon.line(polar(tip, direction + 140, head), tip, polar(tip, direction - 140, head), width=width)


def arrow(icon, start, end, head=ARROW_HEAD, width=THIN):
    """A short motion arrow from start to end with an open chevron head."""
    icon.line(start, end, width=width)
    chevron(icon, end, angle_of(start, end), head, width)


def swing_arc(icon, center, r, start, end, width=THIN):
    """A motion arc around center from angle start to angle end, with an arrow head."""
    p0, p1 = polar(center, start, r), polar(center, end, r)
    icon.arc(p0, p1, r, sweep=1 if end > start else 0, width=width)
    chevron(icon, p1, end + (90 if end > start else -90), width=width)


def arc_arrow(icon, center, r, start, end, width=THIN):
    """A circular arrow round center, from angle start to angle end (clockwise when end > start)."""
    p0, p1 = polar(center, start, r), polar(center, end, r)
    icon.arc(p0, p1, r, large=1 if abs(end - start) > 180 else 0, sweep=1 if end > start else 0, width=width)
    chevron(icon, p1, end + (90 if end > start else -90), width=width)


# ---------------------------------------------------------------- pictograms

ICONS = {}


def icon(fn):
    ICONS[fn.__name__] = fn
    return fn


@icon
def squat(i):
    """Squat at depth: hips back, thighs level, arms out front for balance."""
    i.ground()
    f = Figure(i, (17, 32.5), torso=-55, head=-70)
    f.leg(to=(22.5, FOOT_Y), bend="right", foot=0)
    f.arm(angles=(-3, -3))


@icon
def leg_press(i):
    """Reclined in the seat, feet driving the loaded foot plate up and away."""
    i.ground()
    hip = (17.5, 35)
    f = Figure(i, hip, torso=-132, head=-115)
    back = polar(hip, 138, 4.2)
    base = polar(back, -132, -1)
    i.line(polar(back, -132, 11), base, polar(base, 0, 4))
    i.line(polar(base, 0, 2), (base[0] + 2, 43))
    foot = (29, 25)
    push = angle_of(hip, foot)
    f.leg(to=foot, bend="up")
    plate = polar(foot, push, 2.8)
    pad(i, plate, 3, 12, push)
    i.block(polar(plate, push, 4.5), 5, 8, 1.2, push)


@icon
def lunge(i):
    """Split stance, back knee near the floor, hands on hips."""
    i.ground()
    f = Figure(i, (22.5, 32), torso=-90)
    f.leg(to=(32, FOOT_Y), bend="up")
    f.leg(to=(9.5, FOOT_Y), bend="down")
    f.arm(to=(f.shoulder[0] - 1.5, 29.5), bend="left")


@icon
def deadlift(i):
    """Start of the pull: bar on the floor over the midfoot, shins against it, hips low,
    back flat at about 35 degrees, arms straight down to the bar."""
    i.ground()
    plate = (3.2, 9.0)
    ankle = (19, FOOT_Y)
    hand = (ankle[0] + 1.5, GROUND_Y - 1 - plate[1] / 2)   # the plates stand on the floor
    shoulder = polar(hand, -90, UPPER_ARM + FOREARM)
    f = Figure(i, polar(shoulder, 145, TORSO), torso=-35, head=-15)
    bar = i.mark()
    barbell(i, hand, half=15, plate=plate, front=False)
    i.line(shoulder, hand)
    legs = i.mark()
    f.leg(to=ankle, bend="right")
    i.in_front(legs, since=bar)   # the leg passes in front of the bar and the arm


@icon
def hip_thrust(i):
    """Top of a hip thrust: shoulders on the bench, shoulders, hips and knees in one level
    line, shins upright, the barbell resting in the hip crease."""
    i.ground()
    y = FOOT_Y - SHIN
    shoulder = (13, y)
    pad(i, (shoulder[0] - 2, y + 3.2), 8, 3.4)
    i.line((shoulder[0] - 2, y + 4.5), (shoulder[0] - 2, GROUND_Y - 1))
    f = Figure(i, (shoulder[0] + TORSO, y), torso=180, head=225)   # chin tucked, looking forward
    f.leg(angles=(0, 90), foot=0)
    barbell(i, (f.hip[0], y - LINE), half=8, plate=(3, 6.5), sleeve=2.5, front=False)


@icon
def kettlebell_swing(i):
    """Top of the swing: standing tall with the hips through, arms straight out front and
    the bell in line with them, with the arc it rose along."""
    i.ground()
    f = Figure(i, (12, FOOT_Y - THIGH - SHIN), torso=-95)
    f.leg(angles=(90, 90), foot=0)
    elbow, hand = f.arm(angles=(-5, -5))
    kettlebell(i, hand, angle=-5)
    swing_arc(i, f.shoulder, 20, 78, 30)


@icon
def olympic_lift(i):
    """Front view: barbell locked out overhead in a deep, wide squat."""
    f = Figure(i, (24, 29.5), torso=-90, shoulders=3)
    for side, k in ((0, -1), (1, 1)):
        f.arm(to=(24 + k * 9, 7), bend="left" if k < 0 else "right", side=side)
    f.leg(to=(14.5, FOOT_Y), bend="left")
    f.leg(to=(33.5, FOOT_Y), bend="right")
    barbell(i, (24, 7), half=18, plate=(3, 9), sleeve=2.5)


@icon
def bench_press(i):
    """Lying on a flat bench, barbell pressed up over the chest."""
    i.ground()
    bench(i, 6, 30, 36.5, legs=(9, 27))
    f = Figure(i, (25.5, 32), torso=180, head=195)
    elbow, hand = f.arm(angles=(-90, -90))
    f.leg(to=(36, FOOT_Y), bend="up")
    barbell(i, hand, half=10.5)


@icon
def overhead_press(i):
    """Front view: dumbbells pressed overhead from the shoulders."""
    f = Figure(i, (24, 25.5), torso=-90, shoulders=3)
    for side, k in ((0, -1), (1, 1)):
        elbow, hand = f.arm(angles=(90 - k * 90 - k * 10, -90), side=side)
        dumbbell(i, hand, half=3.2)
    f.leg(to=(19.5, FOOT_Y), bend="left")
    f.leg(to=(28.5, FOOT_Y), bend="right")


@icon
def push_up(i):
    """Top of a push-up: straight body, straight arms."""
    i.ground()
    shoulder_y = FOOT_Y + 0.5 - UPPER_ARM - FOREARM
    a = -math.degrees(math.asin((FOOT_Y - shoulder_y) / (THIGH + SHIN + TORSO)))
    ankle = (7.5, FOOT_Y)
    f = Figure(i, polar(ankle, a, THIGH + SHIN), torso=a, head=a + 8)
    f.arm(angles=(90, 90))
    f.leg(angles=(a + 180, a + 180))


@icon
def dip(i):
    """Front view of a parallel-bar dip: hands on the two bars, elbows bent, knees bent,
    the body held clear of the floor between the uprights.

    Side views of the bar dip read as someone on a bar stool at 33dp; from the front the two
    uprights make the station plain.
    """
    i.ground()
    f = Figure(i, (24, 25.5), torso=-90, shoulders=3)
    for side, k in ((0, -1), (1, 1)):
        hand = (24 + k * 7.5, 26.5)
        f.arm(to=hand, bend="left" if k < 0 else "right", side=side)
        i.line((hand[0] + k * 1.5, hand[1] + 1.5), (hand[0] + k * 1.5, GROUND_Y - 1))
    for k in (-1, 1):   # knees bent with the feet behind, so the legs look short from the front
        i.line(f.hip, (24 + k * 2.5, f.hip[1] + 10))


@icon
def chest_fly(i):
    """Front view, the closed end of a fly: elbows wide and soft, forearms curving in so the
    hands meet in front of the chest with the dumbbells upright side by side."""
    f = Figure(i, (24, 25.5), torso=-90, shoulders=3.5)
    f.leg(to=(20, FOOT_Y), bend="left")
    f.leg(to=(28, FOOT_Y), bend="right")
    y = f.shoulder[1] + 4.5
    mark = i.mark()
    for k in (-1, 1):
        dumbbell(i, (24 + k * 2.6, y), angle=90, half=2.2, plate=(2, 4), front=False)
    i.in_front(mark)
    for side, k in ((0, -1), (1, 1)):
        root = f.roots[side]
        hand = (24 + k * 2.6, y)
        out = (k * math.cos(math.radians(20)), math.sin(math.radians(20)))   # 20 degrees below level
        i.stroke([("M", root), ("C", add(root, out, 10), (hand[0] + k * 7, hand[1] + 4), hand)])


@icon
def lateral_raise(i):
    """Front view: arms raised out to the sides, dumbbells in hand."""
    f = Figure(i, (24, 25.5), torso=-90, shoulders=3)
    for side, k in ((0, -1), (1, 1)):
        elbow, hand = f.arm(angles=(90 - k * 90 + k * 3, 90 - k * 90 + k * 3), side=side)
        dumbbell(i, hand, angle=90, half=3, plate=(2.6, 6))
    f.leg(to=(20, FOOT_Y), bend="left")
    f.leg(to=(28, FOOT_Y), bend="right")


@icon
def row(i):
    """Seated cable row: handle pulled to the waist, elbows back."""
    i.ground()
    i.line((7, 36.5), (17, 36.5))
    i.line((12, 36.5), (12, 43))
    f = Figure(i, (12, 33), torso=-95, head=-88)
    hand = (f.shoulder[0] + 1.2, 28)
    f.arm(to=hand, bend="left")
    f.leg(to=(28, 36), bend="up")
    pad(i, (30.5, 36), 2.6, 7)
    pulley(i, (38.5, 28), hand)
    i.line((41.5, 10), (41.5, 43))


@icon
def pull_up(i):
    """Front view: hanging from an overhead bar."""
    pull_bar(i, 6, 42, 5)
    f = Figure(i, (24, 27.5), torso=-90)
    s = f.shoulder
    for k in (-1, 1):
        tip = (s[0] + k * 3.5, s[1] - 1.5)
        i.line(s, tip)
        f.arm(to=(24 + k * 9.5, 5), bend="left" if k < 0 else "right", root=tip)
    f.leg(to=(21.5, 43), bend="left")
    f.leg(to=(26.5, 43), bend="right")


@icon
def pulldown(i):
    """Seated at a lat pulldown, leaning back a little, reaching up to the wide bar above
    the head, its cable running straight up to the pulley."""
    i.ground()
    hip = (16, 34)
    i.line((hip[0] - 4, hip[1] + LINE), (hip[0] + 4, hip[1] + LINE))
    i.line((hip[0], hip[1] + LINE), (hip[0], GROUND_Y - 1))
    f = Figure(i, hip, torso=-100, head=-112)
    knee, ankle = f.leg(to=(hip[0] + THIGH + 0.5, FOOT_Y), bend="up")
    pad(i, (knee[0] - 1.5, knee[1] - 3.2), 5, 3)
    elbow, hand = f.arm(angles=(-50, -85))
    i.line((hand[0] - 6.5, hand[1]), (hand[0] + 6.5, hand[1]))
    pulley(i, (hand[0], hand[1] - 6), hand)


@icon
def shrug(i):
    """Front view: shoulders drawn up under the chin, arms straight down, dumbbells at the sides."""
    f = Figure(i, (24, 25.5), torso=-90, shoulders=5)
    for k in (-1, 1):
        a = 90 - k * math.degrees(math.asin(3 / (THIGH + SHIN)))
        f.leg(angles=(a, a))
    for side, k in ((0, -1), (1, 1)):
        elbow, hand = f.arm(angles=(90 - k * 6, 90 - k * 6), side=side)
        dumbbell(i, hand, half=2.2, plate=(2, 5), front=False)


@icon
def curl(i):
    """Standing curl: elbow just forward of the body, dumbbell curled up to shoulder height."""
    f = Figure(i, (19, 25), torso=-90)
    f.leg(angles=(90, 90), foot=0)
    elbow, hand = f.arm(angles=(65, -55))
    dumbbell(i, (hand[0] + 1, hand[1]), half=3, front=False)


@icon
def triceps_extension(i):
    """Cable pushdown: elbows pinned at the sides, pressing the handle down."""
    i.ground()
    f = Figure(i, (16.5, 25.5), torso=-80, head=-75)
    f.leg(to=(15, FOOT_Y), bend="right")
    f.leg(to=(18.5, FOOT_Y), bend="right")
    elbow, hand = f.arm(angles=(98, 45))
    pulley(i, (32, 6.8), hand)
    i.line((32, 4.6), (37.5, 4.6), (37.5, 43))
    arrow(i, (28, 24), (28, 31))


@icon
def leg_extension(i):
    """Seated leg extension mid-lift: shin rising against the roller pad on the front of the ankle."""
    i.ground()
    hip = (15, 31)
    f = Figure(i, hip, torso=-90)
    knee, ankle = f.leg(angles=(0, 35))
    i.line((hip[0] - LINE, f.shoulder[1]), (hip[0] - LINE, hip[1] + LINE), (knee[0] - 1, hip[1] + LINE))
    i.line((hip[0] + 2, hip[1] + LINE), (hip[0] + 2, GROUND_Y - 1))
    pad(i, polar(polar(ankle, 35, -1.2), 35 - 90, 3.3), 4.5, 3.3, 35)
    swing_arc(i, knee, SHIN + 5.5, 35 + 22, 35 - 22)


@icon
def leg_curl(i):
    """Lying face down, heel curled up against the roller pad."""
    i.ground()
    bench(i, 6, 34, 36, legs=(9, 30))
    f = Figure(i, (17.5, 32), torso=0, head=-20)
    knee, ankle = f.leg(angles=(180, -85))
    pad(i, polar(polar(ankle, -85, -1.2), 5, 3.4), 4, 5.5, 5)


@icon
def calf_raise(i):
    """Seated calf raise: the ball of the foot on a step, the heel lifted well above its edge.

    Seated so the figure keeps the shared proportions: a standing figure up on its toes on a
    step is taller than the viewBox allows.
    """
    i.ground()
    step_top = GROUND_Y - 1 - 3.5
    ankle = (23.5, step_top - 1.5 - 5 * math.sin(math.radians(45)))
    toe = polar(ankle, 45, 5)
    knee = (ankle[0], ankle[1] - SHIN)
    hip = (knee[0] - THIGH, knee[1])
    i.block((toe[0] + 0.5, step_top + 1.75), 9, 3.5, 1.0)
    Figure(i, hip, torso=-90)
    i.line(hip, knee, ankle, toe)
    i.line((hip[0] - 3.5, hip[1] + LINE), (hip[0] + 2, hip[1] + LINE))
    i.line((hip[0] - 2, hip[1] + LINE), (hip[0] - 2, GROUND_Y - 1))
    arrow(i, (hip[0] + 4, GROUND_Y - 2), (hip[0] + 4, ankle[1] + 2.5))


@icon
def crunch(i):
    """Lying on the back, knees up, shoulders curled off the floor, reaching for the knees."""
    i.ground()
    f = Figure(i, (23, 40.5), torso=-150, head=-140)
    f.leg(to=(36.5, FOOT_Y), bend="up")
    f.arm(angles=(-14, -14))


@icon
def leg_raise(i):
    """Lying on the back, straight legs raised."""
    i.ground()
    f = Figure(i, (25, 40.5), torso=180, head=192)
    f.leg(angles=(-72, -72))
    f.arm(angles=(2, 2))


@icon
def plank(i):
    """Forearm plank: body straight, elbows under the shoulders."""
    i.ground()
    shoulder_y = FOOT_Y - UPPER_ARM
    a = -math.degrees(math.asin((FOOT_Y - shoulder_y) / (THIGH + SHIN + TORSO)))
    ankle = (6.5, FOOT_Y)
    f = Figure(i, polar(ankle, a, THIGH + SHIN), torso=a, head=a + 6)
    f.arm(angles=(90, 0))
    f.leg(angles=(a + 180, a + 180))


@icon
def side_plank(i):
    """Side plank on one forearm, top arm reaching up."""
    i.ground()
    shoulder_y = FOOT_Y - UPPER_ARM
    a = -math.degrees(math.asin((FOOT_Y - shoulder_y) / (THIGH + SHIN + TORSO)))
    ankle = (6.5, FOOT_Y)
    f = Figure(i, polar(ankle, a, THIGH + SHIN), torso=a, head=a + 6)
    f.arm(angles=(90, 0))
    f.arm(angles=(-90, -90))
    f.leg(angles=(a + 180, a + 180))


@icon
def twist(i):
    """Seated Russian twist: leaning back, knees up and feet off the floor, the ball held in
    both hands, with an arrow over it showing the turn."""
    i.ground()
    hip = (15, FOOT_Y)
    f = Figure(i, hip, torso=-135, head=-120)
    knee = polar(hip, -45, THIGH)
    i.line(hip, knee, polar(knee, 22, SHIN))
    elbow, hand = f.arm(angles=(-20, -20))
    centre = polar(hand, -20, BALL_R + 0.5)
    mark = i.mark()
    ball(i, centre)
    i.in_front(mark)
    arc_arrow(i, centre, BALL_R + 3.2, -160, -20)


@icon
def back_extension(i):
    """45 degree back extension: hips on the pad, feet on the foot plate, torso hanging
    forward over the pad, with the arc it rises along back to the line of the legs."""
    i.ground()
    a = -32                   # the line of the legs
    t = a + 50                # the torso, hanging below that line
    ankle = (7, 40)
    hip = polar(ankle, a, THIGH + SHIN)
    f = Figure(i, hip, torso=t, head=t - 6)
    f.leg(angles=(a + 180, a + 180))
    below = a + 90            # the frame is all on this side of the body
    hip_pad = polar(polar(hip, a, -2.0), below, 3.2)
    pad(i, hip_pad, 7, 3.4, a)
    post = polar(hip_pad, below, 1.5)
    i.line(post, (post[0], GROUND_Y - 1))
    pad(i, polar(polar(ankle, a, 0.5), below, 2.9), 3.2, 3, a)
    swing_arc(i, hip, TORSO + NECK + HEAD_R + 2.0, t + 4, a + 2)


@icon
def carry(i):
    """Farmer's walk: mid-stride, upright, arm straight down with a dumbbell at the hip."""
    i.ground()
    f = Figure(i, (23, 26.5), torso=-90)
    f.leg(to=(29.5, FOOT_Y), bend="right")
    f.leg(to=(15.5, FOOT_Y), bend="right")
    elbow, hand = f.arm(angles=(90, 90))
    dumbbell(i, hand, half=3.2, plate=(2.6, 6.5), front=False)


@icon
def throw(i):
    """Overhead medicine ball throw, wound up: forward step, ball held above and behind the head."""
    i.ground()
    f = Figure(i, (22, 31), torso=-100, head=-95)
    f.leg(to=(31, FOOT_Y), bend="right")
    f.leg(to=(9.5, FOOT_Y), bend="up")     # back leg nearly straight
    elbow, hand = f.arm(angles=(-170, -115))
    mark = i.mark()
    ball(i, polar(hand, -115, BALL_R + 0.7))
    i.in_front(mark)


@icon
def jump(i):
    """Tuck jump: in the air, knees up, arms flung up."""
    i.ground()
    f = Figure(i, (22, 27), torso=-82)
    f.leg(angles=(-10, 95))
    f.arm(angles=(-45, -62))
    f.arm(angles=(-135, -118))
    for x, y in ((17, 38), (22, 37), (27, 38)):
        i.line((x, y), (x, 41), width=THIN)


@icon
def run(i):
    """Running stride."""
    f = Figure(i, (22, 25), torso=-78)
    f.arm(angles=(65, -15))
    f.arm(angles=(125, 165))
    f.leg(angles=(25, 100))
    f.leg(angles=(115, 170))


@icon
def bike(i):
    """Cycling: rider leaning to the bars, two wheels, the pedal forward of the crank."""
    r = 5.5
    rear, front = (11, 36), (37, 36)
    i.ring(rear, r)
    i.ring(front, r)
    crank = (21, 36.5)
    bars = (34, 27)
    stem = (bars[0] - 1.5, bars[1] + 1.5)
    frame = i.mark()
    i.line(rear, crank, stem, front)
    hip = (17.5, 23.5)
    i.line(crank, (hip[0], hip[1] + 2))
    i.line(stem, bars)
    f = Figure(i, hip, torso=-35, head=-40)
    f.arm(to=bars, bend="down")
    leg = i.mark()
    f.leg(to=polar(crank, 20, 3.5), bend="right")
    i.in_front(leg, since=frame)   # the leg passes in front of the frame


@icon
def rowing_machine(i):
    """Rowing machine at the finish: legs straight, leaning back, handle drawn to the lower
    ribs, its chain running forward to the flywheel."""
    i.ground()
    hip = (12, 38.5)
    pad(i, (hip[0], hip[1] + 3), 5, 2.2)
    f = Figure(i, hip, torso=-105, head=-100)
    handle = add(polar(hip, -105, TORSO * 0.55), (3.5, 0))
    elbow = polar(f.shoulder, 112, UPPER_ARM)
    arm = i.mark()
    i.line(f.shoulder, elbow, handle)
    i.in_front(arm, gap=0.8)   # the forearm passes in front of the body
    f.leg(angles=(4, 4))
    ankle = polar(hip, 4, THIGH + SHIN)
    pad(i, polar(ankle, -55, 2.3), 2.4, 5, 35)
    wheel = (38, 33)
    i.ring(wheel, 4)          # a hole of radius 2.5, so it reads as a wheel at 33dp
    i.line((wheel[0], wheel[1] + 5.5), (wheel[0], GROUND_Y - 1))
    i.line(handle, polar(wheel, 180, 4), width=THIN)


@icon
def jump_rope(i):
    """Front view: skipping, the rope looping under the feet."""
    f = Figure(i, (24, 24), torso=-90, shoulders=3)
    hands = []
    for side, k in ((0, -1), (1, 1)):
        elbow, hand = f.arm(angles=(90 - k * 30, 90 - k * 55), side=side)
        hands.append(hand)
    f.leg(to=(21.5, 40), bend="left")
    f.leg(to=(26.5, 40), bend="right")
    i.stroke([("M", hands[0]), ("C", (2, 50), (46, 50), hands[1])], THIN)


@icon
def stretch_standing(i):
    """Front view standing side bend: legs straight at hip width, leaning a little, one arm
    sweeping over the head towards the lean, the other hanging down the side of the leg."""
    a = -90 + 16
    f = Figure(i, (24, 25), torso=a, head=a + 4)
    for k in (-1, 1):
        leg = 90 - k * math.degrees(math.asin(2.5 / (THIGH + SHIN)))
        f.leg(angles=(leg, leg))
    f.arm(angles=(84, 84))
    s, h = f.shoulder, f.head
    i.stroke([("M", s), ("C", (s[0] - 8, s[1] - 8), (h[0] + 0.5, h[1] - 10.5), (h[0] + 8.5, h[1] - 3))])


@icon
def stretch_floor(i):
    """Seated forward fold, arms reaching along the legs to the toes."""
    i.ground()
    f = Figure(i, (10, 40.5), torso=-48, head=-40)
    f.leg(angles=(0, 0))
    f.arm(angles=(8, 8))


@icon
def foam_roll(i):
    """Rolling the backs of the legs on a foam roller, hands on the floor behind."""
    i.ground()
    f = Figure(i, (19, 33.5), torso=-150, head=-118)
    f.leg(angles=(0, 0))
    f.arm(to=(10, 41.5), bend="up")
    foam_roller(i, (33, GROUND_Y - 1 - ROLLER_R - THIN / 2))


@icon
def generic(i):
    """A plain dumbbell, for anything that fits no other pattern."""
    i.line((12, 24), (36, 24))
    for x, h in ((10, 12), (14, 16), (34, 16), (38, 12)):
        i.block((x, 24), 3.4, h, 1.4)


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    errors = []
    for name in sorted(ICONS):
        i = Icon()
        try:
            ICONS[name](i)
            svg = i.svg()
        except ValueError as e:
            errors.append(f"{name}: {e}")
            continue
        (OUT_DIR / f"{name}.svg").write_text(svg, encoding="utf-8")
    if errors:
        sys.exit("\n".join(errors))
    print(f"Wrote {len(ICONS)} icons to {OUT_DIR}")


if __name__ == "__main__":
    main()
