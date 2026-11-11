#!/usr/bin/env python3
"""Build the editable Blockbench logo model from Minecraft b1.7.3 textures."""

from __future__ import annotations

import base64
import io
import json
import math
import uuid
import zipfile
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
B173_JAR = Path(
    "/Users/yuan/Library/Application Support/betacraft/versions/b1.7.3.jar"
)
TEXTURE_PATH = ROOT / "grass_fence_logo.png"
BBMODEL_PATH = ROOT / "grass_fence_logo.bbmodel"
JAVA_MODEL_PATH = ROOT / "grass_fence_logo.json"


def texture_uuid() -> str:
    return str(uuid.uuid4())


def make_atlas() -> None:
    with zipfile.ZipFile(B173_JAR) as archive:
        terrain = archive.read("terrain.png")
    source = Image.open(io.BytesIO(terrain)).convert("RGB")
    grass_top = source.crop((0, 0, 16, 16)).convert("RGB")
    dirt = source.crop((32, 0, 48, 16)).convert("RGB")
    grass_side = source.crop((48, 0, 64, 16)).convert("RGBA")
    planks = source.crop((64, 0, 80, 16)).convert("RGB")

    # In b1.7.3 the grass-top tile is luminance-only and receives its biome
    # colour in game. This tint matches the green used by the supplied logo.
    tint = (124, 250, 83)
    tinted_top = Image.new("RGBA", grass_top.size)
    for y in range(16):
        for x in range(16):
            value = grass_top.getpixel((x, y))[0]
            tinted_top.putpixel(
                (x, y),
                (
                    value * tint[0] // 255,
                    value * tint[1] // 255,
                    value * tint[2] // 255,
                    255,
                ),
            )

    atlas = Image.new("RGBA", (64, 16), (0, 0, 0, 0))
    atlas.alpha_composite(tinted_top, (0, 0))
    atlas.alpha_composite(grass_side, (16, 0))
    atlas.alpha_composite(dirt.convert("RGBA"), (32, 0))
    atlas.alpha_composite(planks.convert("RGBA"), (48, 0))
    atlas.save(TEXTURE_PATH)


def face(uv: tuple[int, int, int, int]) -> dict:
    return {"uv": list(uv), "texture": 0}


def all_faces(uv: tuple[int, int, int, int]) -> dict:
    return {side: face(uv) for side in ("north", "east", "south", "west", "up", "down")}


def wood_faces(x_size: float, y_size: float, z_size: float) -> dict:
    """Use b1.7.3's texture index 4 with bounding-box UVs.

    RenderBlocks maps the size of each fence component onto the selected
    terrain.png tile rather than stretching the whole tile over every face.
    """

    u0, v0 = 48, 0
    return {
        "north": face((u0, v0, u0 + x_size, v0 + y_size)),
        "south": face((u0, v0, u0 + x_size, v0 + y_size)),
        "east": face((u0, v0, u0 + z_size, v0 + y_size)),
        "west": face((u0, v0, u0 + z_size, v0 + y_size)),
        "up": face((u0, v0, u0 + x_size, v0 + z_size)),
        "down": face((u0, v0, u0 + x_size, v0 + z_size)),
    }


def cube(
    name: str,
    start: tuple[float, float, float],
    end: tuple[float, float, float],
    faces: dict,
    rotation: tuple[float, float, float] = (0, 0, 0),
) -> dict:
    result = {
        "name": name,
        "box_uv": False,
        "from": list(start),
        "to": list(end),
        "autouv": 0,
        "color": 0,
        "origin": [
            (start[0] + end[0]) / 2,
            (start[1] + end[1]) / 2,
            (start[2] + end[2]) / 2,
        ],
        "faces": faces,
        "type": "cube",
        "uuid": texture_uuid(),
    }
    if any(rotation):
        result["rotation"] = list(rotation)
    return result


def rail_between(
    name: str,
    start: tuple[float, float],
    end: tuple[float, float],
    y1: float,
    y2: float,
) -> dict:
    x1, z1 = start
    x2, z2 = end
    length = math.hypot(x2 - x1, z2 - z1)
    center_x = (x1 + x2) / 2
    center_z = (z1 + z2) / 2
    half = length / 2 + 0.35
    thickness = 1.0
    angle = -math.degrees(math.atan2(z2 - z1, x2 - x1))
    cross_size = thickness * 2
    return cube(
        name,
        (center_x - half, y1, center_z - thickness),
        (center_x + half, y2, center_z + thickness),
        wood_faces(half * 2, y2 - y1, cross_size),
        (0, angle, 0),
    )


def build_bbmodel(encoded_texture: str) -> dict:
    grass_faces = {
        "north": face((16, 0, 32, 16)),
        "east": face((16, 0, 32, 16)),
        "south": face((16, 0, 32, 16)),
        "west": face((16, 0, 32, 16)),
        "up": face((0, 0, 16, 16)),
        "down": face((32, 0, 48, 16)),
    }
    bases = [
        cube("Grass Block 1", (0, 0, 0), (16, 16, 16), grass_faces),
        cube("Grass Block 2", (16, 0, 0), (32, 16, 16), grass_faces),
        cube("Grass Block 3", (0, 0, 16), (16, 16, 32), grass_faces),
        cube("Grass Block 4", (16, 0, 16), (32, 16, 32), grass_faces),
    ]

    # b1.7.3 registers fence with block ID 85 and texture index 4 (oak
    # planks). RenderBlocks uses a 4x16x4 post and two rails that are two
    # pixels thick and three pixels high, at y=6..9 and y=12..15.
    # The five fence blocks form a regular pentagon on the 32x32 top surface.
    # Its forward vertex is on the same diagonal as the front corner of the
    # 2x2 grass base; all neighboring vertices are spaced 72 degrees apart.
    pentagon_radius = 13.0
    pentagon_rotation = math.radians(45)
    centers = [
        (
            16 + pentagon_radius * math.cos(pentagon_rotation + i * math.tau / 5),
            16 + pentagon_radius * math.sin(pentagon_rotation + i * math.tau / 5),
        )
        for i in range(5)
    ]
    posts = []
    rails = []
    fence_groups = []

    for idx, (cx, cz) in enumerate(centers, 1):
        post = cube(
            f"Fence {idx} Post",
            (cx - 2, 16, cz - 2),
            (cx + 2, 32, cz + 2),
            wood_faces(4, 16, 4),
        )
        posts.append(post)

        fence_children = [post["uuid"]]
        for direction_index, neighbor_index in enumerate(((idx - 2) % 5, idx % 5), 1):
            neighbor = centers[neighbor_index]
            midpoint = ((cx + neighbor[0]) / 2, (cz + neighbor[1]) / 2)
            for height_name, y1, y2 in (("Lower", 22, 25), ("Upper", 28, 31)):
                rail = rail_between(
                    f"Fence {idx} Arm {direction_index} {height_name}",
                    (cx, cz),
                    midpoint,
                    y1,
                    y2,
                )
                rails.append(rail)
                fence_children.append(rail["uuid"])

        group_uuid = str(uuid.uuid4())
        fence_groups.append(
            {
                "name": f"Fence {idx}",
                "origin": [cx, 16, cz],
                "uuid": group_uuid,
                "export": True,
                "isOpen": True,
                "children": fence_children,
            }
        )

    base_group = str(uuid.uuid4())
    fence_group = str(uuid.uuid4())

    return {
        "meta": {
            "format_version": "4.10",
            "model_format": "java_block",
            "box_uv": False,
        },
        "name": "grass_fence_logo",
        "model_identifier": "grass_fence_logo",
        "visible_box": [1, 1, 0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": {},
        "unhandled_root_fields": {},
        "resolution": {"width": 64, "height": 16},
        "elements": [*bases, *posts, *rails],
        "groups": [
            {
                "name": "2x2 Grass Base",
                "origin": [16, 8, 16],
                "uuid": base_group,
                "export": True,
                "isOpen": True,
                "children": [item["uuid"] for item in bases],
            },
            {
                "name": "Pentagon Fence",
                "origin": [16, 16, 16],
                "uuid": fence_group,
                "export": True,
                "isOpen": True,
                "children": [item["uuid"] for item in fence_groups],
            },
            *fence_groups,
        ],
        "outliner": [base_group, fence_group],
        "textures": [
            {
                "id": "0",
                "name": "beta1.7.3_grass_fence",
                "folder": "block",
                "namespace": "minecraft",
                "particle": True,
                "render_mode": "default",
                "visible": True,
                "mode": "bitmap",
                "saved": False,
                "uuid": str(uuid.uuid4()),
                "relative_path": "",
                "source": f"data:image/png;base64,{encoded_texture}",
            }
        ],
        "animations": [],
    }


def java_faces(cube_faces: dict) -> dict:
    # Equivalently-coloured plank texture on every side.
    return {
        side: {"uv": uv["uv"], "texture": "#0"}
        for side, uv in cube_faces.items()
    }


def build_java_model() -> dict:
    elements = []
    for item in build_bbmodel("")["elements"]:
        converted = {
            "from": item["from"],
            "to": item["to"],
            "faces": java_faces(item["faces"]),
        }
        if "rotation" in item:
            converted["rotation"] = {"origin": item["origin"], "axis": "y", "angle": item["rotation"][1]}
        elements.append(converted)
    return {
        "credit": "Built in Blockbench from Minecraft b1.7.3 terrain.png",
        "texture_size": [64, 16],
        "textures": {"0": "grass_fence_logo"},
        "elements": elements,
    }


def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    make_atlas()
    encoded = base64.b64encode(TEXTURE_PATH.read_bytes()).decode("ascii")
    model = build_bbmodel(encoded)
    BBMODEL_PATH.write_text(json.dumps(model, ensure_ascii=False, indent=2), encoding="utf-8")
    JAVA_MODEL_PATH.write_text(
        json.dumps(build_java_model(), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(BBMODEL_PATH)
    print(JAVA_MODEL_PATH)
    print(TEXTURE_PATH)


if __name__ == "__main__":
    main()
