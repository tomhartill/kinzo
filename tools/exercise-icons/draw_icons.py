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
THIN = 2        # ground lines, cables and ropes
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

def circle_d(c, r):
    x, y = c
    return (f"M{P((x - r, y))}A{fmt(r)} {fmt(r)} 0 1 1 {P((x + r, y))}"
            f"A{fmt(r)} {fmt(r)} 0 1 1 {P((x - r, y))}Z")


def rrect_d(c, w, h, r, angle=0.0):
    """Rounded rectangle centred on c, w along the angle, h across it. Clockwise."""
    r = min(r, w / 2, h / 2)
    a = math.radians(angle)
    ca, sa = math.cos(a), math.sin(a)

    def T(x, y):
        return (c[0] + x * ca - y * sa, c[1] + x * sa + y * ca)

    hw, hh = w / 2, h / 2
    arc = f"A{fmt(r)} {fmt(r)} 0 0 1 "
    return (f"M{P(T(-hw + r, -hh))}L{P(T(hw - r, -hh))}{arc}{P(T(hw, -hh + r))}"
            f"L{P(T(hw, hh - r))}{arc}{P(T(hw - r, hh))}"
            f"L{P(T(-hw + r, hh))}{arc}{P(T(-hw, hh - r))}"
            f"L{P(T(-hw, -hh + r))}{arc}{P(T(-hw + r, -hh))}Z")


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


GAP = 1.2   # clear space between a shape and a line passing behind it


class Icon:
    """Collects strokes and fills in drawing order, then writes them as SVG paths.

    Straight lines can be cut: in_front(mark) makes everything drawn since mark leave a gap
    in the straight lines drawn before it.
    """

    def __init__(self):
        self.items = []   # [kind, width, data, sd]: kind is "lines", "path" or "fill"

    def mark(self):
        return len(self.items)

    def _add(self, kind, width, data, sd=None):
        self.items.append([kind, width, data, sd])

    def line(self, *points, width=LINE, cut=True):
        """A polyline. cut=False keeps it whole when something is drawn in front of it."""
        caps = [sd_segment(a, b, width / 2) for a, b in zip(points, points[1:])]
        self._add("lines" if cut else "path", width,
                  [list(points)] if cut else "M" + "L".join(P(p) for p in points),
                  lambda p: min(c(p) for c in caps))

    def stroke(self, d, width=LINE, sd=None):
        self._add("path", width, d, sd)

    def arc(self, start, end, r, large=0, sweep=1, width=LINE):
        self.stroke(f"M{P(start)}A{fmt(r)} {fmt(r)} 0 {large} {sweep} {P(end)}", width)

    def curve(self, start, control, end, width=LINE):
        self.stroke(f"M{P(start)}Q{P(control)} {P(end)}", width)

    def ring(self, c, r, width=LINE):
        self.stroke(circle_d(c, r), width, sd_circle(c, r + width / 2))

    def dot(self, c, r):
        self._add("fill", None, circle_d(c, r), sd_circle(c, r))

    def block(self, c, w, h, r=1.0, angle=0.0):
        self._add("fill", None, rrect_d(c, w, h, r, angle), sd_box(c, w, h, r, angle))

    def ground(self, x0=4, x1=44, y=GROUND_Y):
        self.line((x0, y), (x1, y), width=THIN, cut=False)

    def in_front(self, mark, gap=GAP):
        """Puts everything drawn since mark in front of the straight lines drawn before it."""
        shapes = [it[3] for it in self.items[mark:] if it[3] is not None]
        if not shapes:
            return
        for it in self.items[:mark]:
            if it[0] == "lines":
                clear = gap + it[1] / 2
                visible = lambda p: min(sd(p) for sd in shapes) >= clear
                it[2] = [piece for pts in it[2] for piece in clip_polyline(pts, visible)]

    def svg(self):
        strokes, fills = {}, []
        for kind, width, data, _ in self.items:
            if kind == "lines":
                strokes.setdefault(width, []).extend("M" + "L".join(P(p) for p in pts) for pts in data)
            elif kind == "path":
                strokes.setdefault(width, []).append(data)
            else:
                fills.append(data)
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


def kettlebell(icon, handle, angle=90.0, r=4.0, front=True):
    """Kettlebell hanging from the handle point in the given direction."""
    mark = icon.mark()
    body = polar(handle, angle, 2.0 + r)
    icon.dot(body, r)
    if front:
        icon.in_front(mark)
    left = polar(body, angle - 180 + 50, r * 0.9)
    right = polar(body, angle - 180 - 50, r * 0.9)
    icon.stroke(f"M{P(left)}Q{P(polar(handle, angle, -1.0))} {P(right)}", 2.5)


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


def box(icon, x0, y0, x1, y1, r=1.5):
    """A plyo box or step, drawn as an outline."""
    icon.stroke(
        f"M{P((x0 + r, y0))}L{P((x1 - r, y0))}A{fmt(r)} {fmt(r)} 0 0 1 {P((x1, y0 + r))}"
        f"L{P((x1, y1 - r))}A{fmt(r)} {fmt(r)} 0 0 1 {P((x1 - r, y1))}"
        f"L{P((x0 + r, y1))}A{fmt(r)} {fmt(r)} 0 0 1 {P((x0, y1 - r))}"
        f"L{P((x0, y0 + r))}A{fmt(r)} {fmt(r)} 0 0 1 {P((x0 + r, y0))}Z")


def ball(icon, center, r=4.0):
    icon.ring(center, r - LINE / 2)


def foam_roller(icon, center, r=4.0):
    icon.ring(center, r - LINE / 2)


def arrow(icon, start, end, head=2.5, width=THIN):
    """A short motion arrow from start to end with an open chevron head."""
    a = angle_of(start, end)
    icon.line(start, end, width=width)
    icon.line(polar(end, a + 140, head), end, polar(end, a - 140, head), width=width)


# ---------------------------------------------------------------- pictograms

ICONS = {}


def icon(fn):
    ICONS[fn.__name__] = fn
    return fn


@icon
def squat(i):
    f = Figure(i, (20.5, 31.5), torso=-56, head=-74)
    barbell(i, (26.5, 23.5), half=13)
    f.arm(to=(21.5, 23.5), bend="down")
    f.leg(to=(27, FOOT_Y), bend="right")


@icon
def leg_press(i):
    hip = (18.5, 34)
    f = Figure(i, hip, torso=-128, head=-112)
    # Backrest behind the torso, and the frame under the seat.
    back = polar(hip, -128 + 90, 3.4)
    i.line(polar(back, -128, 11.5), polar(back, -128, -1.5), (21, 43))
    f.arm(to=(21.5, 32.5), bend="down")
    knee, foot = f.leg(to=(29.5, 25), bend="up")
    push = angle_of(hip, foot)
    plate = polar(foot, push, 2.6)
    pad(i, plate, 2.6, 11, push)
    i.line(polar(plate, push, 2), polar(plate, push, 10), width=THIN)


@icon
def lunge(i):
    i.ground()
    f = Figure(i, (23, 32), torso=-90)
    f.arm(to=(21.5, 30), bend="left")
    f.leg(to=(32.5, FOOT_Y), bend="up")
    f.leg(to=(9.5, FOOT_Y), bend="down")


@icon
def deadlift(i):
    i.ground()
    f = Figure(i, (12, 27.5), torso=-18, head=-14)
    elbow, hand = f.arm(angles=(90, 90))
    barbell(i, hand, half=8, plate=(3, 9))
    f.leg(to=(18.5, FOOT_Y), bend="right")


@icon
def hip_thrust(i):
    i.ground()
    bench(i, 4, 13, 37, legs=(6, 11))
    f = Figure(i, (24, 30.5), torso=165, head=188)
    barbell(i, (23.5, 24.5), half=8, plate=(3, 8))
    f.arm(to=(22, 24.5), bend="up")
    f.leg(to=(33, FOOT_Y), bend="up")


@icon
def kettlebell_swing(i):
    f = Figure(i, (18, 24.5), torso=-92)
    elbow, hand = f.arm(angles=(-5, -5))
    kettlebell(i, hand, angle=-5)
    f.leg(to=(18.5, FOOT_Y), bend="right")


@icon
def olympic_lift(i):
    f = Figure(i, (24, 32), torso=-90)
    elbow, hand = f.arm(angles=(-90, -90))
    barbell(i, hand, half=13)
    f.leg(to=(33.5, FOOT_Y), bend="up")
    f.leg(to=(12, FOOT_Y), bend="down")


@icon
def bench_press(i):
    i.ground()
    bench(i, 6, 30, 35.5, legs=(9, 27))
    f = Figure(i, (25, 31.5), torso=180, head=182)
    elbow, hand = f.arm(angles=(-90, -90))
    barbell(i, hand, half=10)
    f.leg(to=(37, FOOT_Y), bend="up")


@icon
def overhead_press(i):
    f = Figure(i, (24, 25.5), torso=-90, shoulders=3)
    for side, k in ((0, -1), (1, 1)):
        elbow, hand = f.arm(angles=(90 - k * 90 - k * 10, -90), side=side)
        dumbbell(i, hand)
    f.leg(to=(19.5, FOOT_Y), bend="left")
    f.leg(to=(28.5, FOOT_Y), bend="right")


@icon
def push_up(i):
    i.ground()
    a = -27
    hip = (22.5, 33.5)
    f = Figure(i, hip, torso=a)
    f.arm(to=(f.shoulder[0], FOOT_Y + 0.5), bend="left")
    f.leg(angles=(a + 180, a + 180))


@icon
def dip(i):
    i.ground()
    f = Figure(i, (22, 30), torso=-72, head=-78)
    f.arm(to=(20, 27.5), bend="up")
    f.leg(angles=(82, 195))
    i.line((6, 27.5), (17, 27.5))
    i.line((25, 27.5), (41, 27.5))
    i.line((9, 27.5), (9, 43))
    i.line((38, 27.5), (38, 43))


@icon
def chest_fly(i):
    f = Figure(i, (24, 26), torso=-90, shoulders=3)
    for side, k in ((0, -1), (1, 1)):
        elbow, hand = f.arm(to=(24 + k * 2.5, 22), bend="left" if k < 0 else "right", side=side)
        pulley(i, (24 + k * 19, 40), hand)
    f.leg(to=(20, FOOT_Y), bend="left")
    f.leg(to=(28, FOOT_Y), bend="right")


@icon
def lateral_raise(i):
    f = Figure(i, (24, 25.5), torso=-90, shoulders=3)
    for side, k in ((0, -1), (1, 1)):
        elbow, hand = f.arm(angles=(90 - k * 90 + k * 4, 90 - k * 90 + k * 4), side=side)
        dumbbell(i, hand, angle=90, half=3, plate=(2.6, 6))
    f.leg(to=(20, FOOT_Y), bend="left")
    f.leg(to=(28, FOOT_Y), bend="right")


@icon
def row(i):
    i.ground()
    f = Figure(i, (12.5, 25.5), torso=-25, head=-18)
    elbow, hand = f.arm(to=(21, 31), bend="up")
    barbell(i, hand, half=7.5, plate=(3, 8))
    f.leg(to=(16, FOOT_Y), bend="right")


@icon
def pull_up(i):
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
    i.ground()
    f = Figure(i, (16, 33), torso=-96)
    elbow, hand = f.arm(to=(19.5, 10.5), bend="left")
    i.line((13, 10.5), (26, 10.5))
    i.line((19.5, 10.5), (19.5, 5), (38, 5), (38, 43), width=THIN)
    i.line((11, 36.5), (21, 36.5))
    i.line((16, 36.5), (16, 43))
    pad(i, (25, 29.5), 5, 3.2)
    f.leg(to=(25.5, FOOT_Y), bend="up")


@icon
def shrug(i):
    f = Figure(i, (24, 26), torso=-90)
    s = f.shoulder
    for k in (-1, 1):
        tip = (s[0] + k * 3.5, s[1] - 2)
        i.line(s, tip)
        elbow, hand = f.arm(angles=(90 + k * -8, 90 + k * -4), root=tip)
        dumbbell(i, (hand[0], hand[1] + 1), half=3.2)
        arrow(i, (s[0] + k * 6.5, s[1] - 3), (s[0] + k * 6.5, s[1] - 9))
    f.leg(to=(20, FOOT_Y), bend="left")
    f.leg(to=(28, FOOT_Y), bend="right")


@icon
def curl(i):
    f = Figure(i, (20, 25), torso=-90)
    elbow, hand = f.arm(angles=(92, -40))
    dumbbell(i, hand, angle=-40 + 90, half=3.2)
    f.leg(to=(18.5, FOOT_Y), bend="right")
    f.leg(to=(21.5, FOOT_Y), bend="right")


@icon
def triceps_extension(i):
    f = Figure(i, (19, 25.5), torso=-86)
    elbow, hand = f.arm(angles=(95, 45))
    pulley(i, (30, 5), hand)
    i.line((27, hand[1]), (hand[0] + 1.5, hand[1] + 1.5))
    i.line((30, 5), (37, 5), (37, 43), width=THIN)
    f.leg(to=(18, FOOT_Y), bend="right")
    f.leg(to=(21, FOOT_Y), bend="right")


@icon
def leg_extension(i):
    i.line((11, 16), (11, 35), (24, 35))
    i.line((17, 35), (17, 43))
    f = Figure(i, (15, 31.5), torso=-90)
    f.arm(to=(18.5, 32.5), bend="down")
    knee, ankle = f.leg(angles=(0, -12))
    pad(i, polar(ankle, -12 - 90, 3.3), 4.5, 3.3, -12)


@icon
def leg_curl(i):
    bench(i, 7, 38, 35, legs=(10, 35))
    f = Figure(i, (18, 31.5), torso=0, head=-3)
    f.arm(to=(36, 31.5), bend="down")
    knee, ankle = f.leg(angles=(180, -75))
    pad(i, polar(ankle, 15, 3.2), 3.3, 4.5, 15)


@icon
def calf_raise(i):
    i.ground()
    f = Figure(i, (22, 22.5), torso=-90)
    elbow, hand = f.arm(angles=(95, 95))
    dumbbell(i, (hand[0], hand[1] + 1), half=3)
    f.leg(to=(22, 39.5), bend="right", foot=62)
    arrow(i, (14, 40), (14, 33))


@icon
def crunch(i):
    i.ground()
    f = Figure(i, (22, 40.5), torso=-148, head=-140)
    f.arm(to=polar(f.head, 160, 2), bend="up")
    f.leg(to=(36, FOOT_Y), bend="up")


@icon
def leg_raise(i):
    i.ground()
    f = Figure(i, (25, 40.5), torso=180, head=190)
    f.arm(angles=(0, 0))
    f.leg(angles=(-72, -72))


@icon
def plank(i):
    i.ground()
    elbow = (33, FOOT_Y)
    shoulder = (33, FOOT_Y - UPPER_ARM)
    a = -math.degrees(math.asin((FOOT_Y - shoulder[1]) / (THIGH + SHIN + TORSO)))
    hip = polar(shoulder, a + 180, TORSO)
    f = Figure(i, hip, torso=a)
    f.arm(angles=(90, 0))
    f.leg(angles=(a + 180, a + 180))


@icon
def side_plank(i):
    i.ground()
    shoulder = (33, FOOT_Y - UPPER_ARM)
    a = -math.degrees(math.asin((FOOT_Y - shoulder[1]) / (THIGH + SHIN + TORSO)))
    hip = polar(shoulder, a + 180, TORSO)
    f = Figure(i, hip, torso=a)
    f.arm(angles=(90, 0))
    f.arm(angles=(-90, -90))
    f.leg(angles=(a + 180, a + 180))


@icon
def twist(i):
    i.ground()
    f = Figure(i, (17, 40.5), torso=-118, head=-104)
    elbow, hand = f.arm(to=(23, 31), bend="down")
    ball(i, (25.5, 30))
    f.leg(to=(33, 36), bend="up")
    i.arc((6, 18), (18, 16), 8, sweep=1, width=THIN)


@icon
def back_extension(i):
    a = -33
    ankle = (9, 38)
    hip = polar(ankle, a, THIGH + SHIN)
    f = Figure(i, hip, torso=a)
    f.arm(to=polar(f.head, 200, 1), bend="up")
    f.leg(angles=(a + 180, a + 180))
    hp = polar(hip, a + 90, 3.3)
    pad(i, hp, 6.5, 3.2, a)
    fp = polar(ankle, a - 90, 3.2)
    pad(i, fp, 4, 3.2, a)
    i.line(hp, (hp[0] + 2, 43), (8, 43), fp)


@icon
def carry(i):
    f = Figure(i, (22, 26.5), torso=-88)
    elbow, hand = f.arm(angles=(84, 86))
    kettlebell(i, hand)
    f.leg(to=(28, FOOT_Y), bend="right")
    f.leg(to=(15.5, FOOT_Y), bend="right")


@icon
def throw(i):
    f = Figure(i, (18, 27), torso=-80, head=-80)
    elbow, hand = f.arm(angles=(-25, -20))
    ball(i, (36, 9.5))
    f.leg(to=(26, FOOT_Y), bend="right")
    f.leg(to=(10, FOOT_Y), bend="right")
    i.line((24, 6), (29, 5.5), width=THIN)
    i.line((25, 11.5), (29.5, 10.5), width=THIN)


@icon
def jump(i):
    i.ground()
    box(i, 28, 30, 42, 43)
    f = Figure(i, (15, 23.5), torso=-72)
    f.arm(angles=(-60, -50))
    f.leg(angles=(15, 115))


@icon
def run(i):
    f = Figure(i, (22, 25), torso=-78)
    f.arm(angles=(65, -15))
    f.arm(angles=(125, 165))
    f.leg(angles=(25, 100))
    f.leg(angles=(115, 170))


@icon
def bike(i):
    i.ground()
    crank = (22, 35)
    i.ring((33, 35.5), 5.5 - LINE / 2)
    i.line((16, 26), crank, (33, 35.5), (31, 18), (35, 18))
    i.line((12, 43), (38, 43), width=THIN)
    f = Figure(i, (16, 24.5), torso=-38, head=-45)
    f.arm(to=(33, 18.5), bend="down")
    f.leg(to=polar(crank, 60, 3.5), bend="right")
    f.leg(to=polar(crank, -120, 3.5), bend="up")


@icon
def rowing_machine(i):
    i.line((5, 41), (36, 41))
    i.ring((38, 35), 5 - LINE / 2)
    f = Figure(i, (12, 37.5), torso=-62, head=-70)
    elbow, hand = f.arm(angles=(-12, -10))
    i.line(hand, (36, 33), width=THIN)
    f.leg(to=(27, 39.5), bend="up")


@icon
def jump_rope(i):
    f = Figure(i, (24, 24), torso=-90, shoulders=3)
    hands = []
    for side, k in ((0, -1), (1, 1)):
        elbow, hand = f.arm(angles=(90 - k * 30, 90 - k * 55), side=side)
        hands.append(hand)
    f.leg(to=(21.5, 40), bend="left")
    f.leg(to=(26.5, 40), bend="right")
    i.stroke(f"M{P(hands[0])}C{P((2, 50))} {P((46, 50))} {P(hands[1])}", THIN)


@icon
def stretch_standing(i):
    f = Figure(i, (22, 24.5), torso=-90)
    f.arm(angles=(-25, -30))
    knee, ankle = f.leg(angles=(95, 250))
    f.arm(to=ankle, bend="down")
    f.leg(to=(22, FOOT_Y), bend="right")


@icon
def stretch_floor(i):
    i.ground()
    f = Figure(i, (12, 40.5), torso=-32, head=-25)
    f.arm(angles=(10, 12))
    f.leg(angles=(0, 0))


@icon
def foam_roll(i):
    i.ground()
    foam_roller(i, (29, 39))
    f = Figure(i, (17, 36), torso=-150, head=-120)
    f.arm(to=(6.5, FOOT_Y), bend="up")
    f.leg(angles=(8, 5))


@icon
def generic(i):
    i.line((12, 24), (36, 24))
    for x, h in ((10, 16), (14, 12), (34, 12), (38, 16)):
        i.block((x, 24), 3.4, h, 1.4)


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    errors = []
    for name in sorted(ICONS):
        i = Icon()
        try:
            ICONS[name](i)
        except ValueError as e:
            errors.append(f"{name}: {e}")
            continue
        (OUT_DIR / f"{name}.svg").write_text(i.svg(), encoding="utf-8")
    if errors:
        sys.exit("\n".join(errors))
    print(f"Wrote {len(ICONS)} icons to {OUT_DIR}")


if __name__ == "__main__":
    main()
