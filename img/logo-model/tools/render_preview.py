#!/usr/bin/env python3
"""Render a textured, logo-style isometric preview of the .bbmodel."""

from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
MODEL_PATH = ROOT / "grass_fence_logo.bbmodel"
TEXTURE_PATH = ROOT / "grass_fence_logo.png"
OUTPUT_PATH = ROOT / "grass_fence_logo_render.png"
MODEL_YAW = 0.0

SCALE = 18
WIDTH = 1180
HEIGHT = 1180
CENTER_X = WIDTH / 2
CENTER_Y = 600


FACE_CORNERS = {
    "north": ([0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0]),
    "south": ([1, 0, 1], [0, 0, 1], [0, 1, 1], [1, 1, 1]),
    "west": ([0, 0, 1], [0, 0, 0], [0, 1, 0], [0, 1, 1]),
    "east": ([1, 0, 0], [1, 0, 1], [1, 1, 1], [1, 1, 0]),
    "up": ([0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]),
    "down": ([0, 0, 1], [0, 0, 0], [1, 0, 0], [1, 0, 1]),
}

NORMALS = {
    "north": np.array([0.0, 0.0, -1.0]),
    "south": np.array([0.0, 0.0, 1.0]),
    "west": np.array([-1.0, 0.0, 0.0]),
    "east": np.array([1.0, 0.0, 0.0]),
    "up": np.array([0.0, 1.0, 0.0]),
    "down": np.array([0.0, -1.0, 0.0]),
}

SHADE = {
    "north": 0.72,
    "south": 0.84,
    "west": 0.66,
    "east": 0.82,
    "up": 1.00,
    "down": 0.46,
}


def rotate_y(point: np.ndarray, angle: float, origin: np.ndarray) -> np.ndarray:
    theta = math.radians(angle)
    x, y, z = point - origin
    return np.array(
        [
            x * math.cos(theta) + z * math.sin(theta) + origin[0],
            y + origin[1],
            -x * math.sin(theta) + z * math.cos(theta) + origin[2],
        ]
    )


def rotate_direction_y(direction: np.ndarray, angle: float) -> np.ndarray:
    theta = math.radians(angle)
    x, y, z = direction
    return np.array(
        [
            x * math.cos(theta) + z * math.sin(theta),
            y,
            -x * math.sin(theta) + z * math.cos(theta),
        ]
    )


def project(point: np.ndarray) -> tuple[float, float]:
    x, y, z = point
    return (
        CENTER_X + (x - z) * SCALE * 0.92,
        CENTER_Y + (x + z) * SCALE * 0.46 - y * SCALE,
    )


def rotate_model(point: np.ndarray) -> np.ndarray:
    """Rotate around the vertical center axis of the 2x2 grass base."""
    theta = math.radians(MODEL_YAW)
    x, y, z = point
    x -= 16
    z -= 16
    return np.array(
        [
            x * math.cos(theta) + z * math.sin(theta) + 16,
            y,
            -x * math.sin(theta) + z * math.cos(theta) + 16,
        ]
    )


def homography(source: np.ndarray, target: np.ndarray) -> np.ndarray:
    rows = []
    values = []
    for (x, y), (u, v) in zip(source, target):
        rows.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        values.append(u)
        rows.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        values.append(v)
    solution = np.linalg.solve(np.asarray(rows, dtype=float), np.asarray(values, dtype=float))
    return np.append(solution, 1.0).reshape(3, 3)


def map_pixels(matrix: np.ndarray, xs: np.ndarray, ys: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    points = np.stack([xs, ys, np.ones_like(xs)], axis=-1)
    mapped = points @ matrix.T
    return mapped[..., 0] / mapped[..., 2], mapped[..., 1] / mapped[..., 2]


def draw_textured_quad(
    canvas: Image.Image,
    texture: np.ndarray,
    vertices: list[tuple[float, float]],
    uv: list[float],
    shade: float,
    side: str,
) -> None:
    area = abs(
        sum(
            vertices[i][0] * vertices[(i + 1) % 4][1]
            - vertices[(i + 1) % 4][0] * vertices[i][1]
            for i in range(4)
        )
    ) / 2
    if area < 0.5:
        return

    if side in {"north", "south", "east", "west"}:
        source = np.array(
            [
                [uv[0], uv[3]],
                [uv[2], uv[3]],
                [uv[2], uv[1]],
                [uv[0], uv[1]],
            ],
            dtype=float,
        )
    else:
        source = np.array(
            [
                [uv[0], uv[1]],
                [uv[2], uv[1]],
                [uv[2], uv[3]],
                [uv[0], uv[3]],
            ],
            dtype=float,
        )
    target = np.asarray(vertices, dtype=float)
    inverse = np.linalg.inv(homography(source, target))

    x0 = max(0, math.floor(min(v[0] for v in vertices)))
    x1 = min(WIDTH, math.ceil(max(v[0] for v in vertices)))
    y0 = max(0, math.floor(min(v[1] for v in vertices)))
    y1 = min(HEIGHT, math.ceil(max(v[1] for v in vertices)))
    if x0 >= x1 or y0 >= y1:
        return

    yy, xx = np.mgrid[y0:y1, x0:x1]
    u, v = map_pixels(inverse, xx + 0.5, yy + 0.5)
    tx = np.floor(u).astype(int)
    ty = np.floor(v).astype(int)
    valid = (tx >= 0) & (tx < texture.shape[1]) & (ty >= 0) & (ty < texture.shape[0])
    tx = np.clip(tx, 0, texture.shape[1] - 1)
    ty = np.clip(ty, 0, texture.shape[0] - 1)

    mask = Image.new("L", (x1 - x0, y1 - y0), 0)
    ImageDraw.Draw(mask).polygon(
        [(x - x0, y - y0) for x, y in vertices],
        fill=255,
    )
    mask_array = np.asarray(mask) > 0
    valid &= mask_array

    samples = texture[ty, tx].astype(float)
    samples[..., :3] *= shade
    samples = np.clip(samples, 0, 255).astype(np.uint8)

    region = np.asarray(canvas.crop((x0, y0, x1, y1))).copy()
    region[valid] = samples[valid]
    canvas.paste(Image.fromarray(region), (x0, y0))


def render_image(crop: bool = True) -> Image.Image:
    model = json.loads(MODEL_PATH.read_text(encoding="utf-8"))
    texture = np.asarray(Image.open(TEXTURE_PATH).convert("RGBA"))
    canvas = Image.new("RGBA", (WIDTH, HEIGHT), (255, 255, 255, 0))
    view = np.array([1.0, 1.0, 1.0])
    faces_to_draw = []

    for element in model["elements"]:
        low = np.asarray(element["from"], dtype=float)
        high = np.asarray(element["to"], dtype=float)
        origin = np.asarray(element.get("origin", [0, 0, 0]), dtype=float)
        rotation = element.get("rotation", [0, 0, 0])[1]
        for side, corner_indices in FACE_CORNERS.items():
            normal = rotate_direction_y(NORMALS[side], rotation)
            normal = rotate_direction_y(normal, MODEL_YAW)
            if np.dot(normal, view) <= 0:
                continue
            points = []
            for ix, iy, iz in corner_indices:
                point = np.array(
                    [
                        high[0] if ix else low[0],
                        high[1] if iy else low[1],
                        high[2] if iz else low[2],
                    ]
                )
                if rotation:
                    point = rotate_y(point, rotation, origin)
                point = rotate_model(point)
                points.append(point)
            screen = [project(point) for point in points]
            depth = sum(point[0] + point[1] + point[2] for point in points) / 4
            faces_to_draw.append(
                (depth, element["faces"][side]["uv"], screen, SHADE[side], side)
            )

    for _, uv, screen, shade, side in sorted(faces_to_draw, key=lambda item: item[0]):
        draw_textured_quad(canvas, texture, screen, uv, shade, side)
        ImageDraw.Draw(canvas).line(
            [*screen, screen[0]],
            fill=(58, 37, 27, 180),
            width=2,
        )

    bbox = canvas.getbbox() if crop else None
    if bbox:
        pad = 28
        bbox = (
            max(0, bbox[0] - pad),
            max(0, bbox[1] - pad),
            min(WIDTH, bbox[2] + pad),
            min(HEIGHT, bbox[3] + pad),
        )
        canvas = canvas.crop(bbox)
    return canvas


def main() -> None:
    canvas = render_image()
    canvas.save(OUTPUT_PATH)
    print(OUTPUT_PATH)


if __name__ == "__main__":
    main()
