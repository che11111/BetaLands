#!/usr/bin/env python3
"""Export grass_fence_logo.bbmodel as a transparent PNG."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

import render_preview


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_PATH = ROOT / "exports" / "grass_fence_logo.png"
OUTPUT_SIZE = 1024


def main() -> None:
    # Match the first frame of grass_fence_logo_rotation.gif: the pentagon's
    # front vertex points along the diagonal toward the square's front corner.
    render_preview.MODEL_YAW = 0.0
    image = render_preview.render_image(crop=False).convert("RGBA")

    # Preserve the same fixed composition used by the GIF, then export at a
    # convenient square size without adding a background color.
    image = image.resize(
        (OUTPUT_SIZE, OUTPUT_SIZE),
        Image.Resampling.LANCZOS,
    )
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT_PATH)
    print(OUTPUT_PATH)


if __name__ == "__main__":
    main()
