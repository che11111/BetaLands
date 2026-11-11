#!/usr/bin/env python3
"""Render a seamless 360-degree turn around the 2x2 base center."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

import render_preview


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_PATH = ROOT / "grass_fence_logo_rotation.gif"
FRAME_COUNT = 48
FRAME_DURATION_MS = 90


def main() -> None:
    frames = []
    for index in range(FRAME_COUNT):
        render_preview.MODEL_YAW = index * 360 / FRAME_COUNT
        # Keep one fixed canvas while the model turns so the pivot stays fixed.
        fixed = render_preview.render_image(crop=False).convert("RGBA")
        frame = fixed.resize((640, 640), Image.Resampling.LANCZOS)

        # GIF supports one fully transparent palette entry. Quantize the render
        # to 255 RGB entries and reserve index 255 for alpha under 50%.
        alpha = frame.getchannel("A")
        indexed = frame.convert("RGB").quantize(
            colors=255,
            method=Image.Quantize.MEDIANCUT,
        )
        palette = indexed.getpalette()
        palette.extend([0, 0, 0] * (256 - len(palette) // 3))
        indexed.putpalette(palette)
        indexed.paste(255, mask=alpha.point(lambda value: 255 if value < 128 else 0))
        indexed.info["transparency"] = 255
        indexed.info["disposal"] = 2
        frames.append(indexed)

    frames[0].save(
        OUTPUT_PATH,
        save_all=True,
        append_images=frames[1:],
        duration=FRAME_DURATION_MS,
        loop=0,
        transparency=255,
        disposal=2,
        optimize=True,
    )
    print(OUTPUT_PATH)


if __name__ == "__main__":
    main()
