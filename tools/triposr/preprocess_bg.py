"""Standalone background-isolation step for the staged TripoSR worker pipeline.

Runs as its own short-lived process so rembg's segmentation model never has to coexist in memory
with TripoSR's own model - that co-residency was the proven cause of a real OOM kill on the VPS
(dmesg, anon-rss ~7.7GB; see backend/app/config.py's triposr_remove_background comment and
backend/tests/test_triposr_worker.py). The worker server
(migration/triposr-worker/worker_server.py) runs this to completion and waits for the process to
fully exit - freeing rembg's memory - before ever starting infer.py.

Two things proved necessary here, not just the process split (measured directly on this VPS with
a real museum artifact photo during the staged-pipeline rollout):
  - rembg.new_session() with no model name resolves (on the installed rembg 2.0.84) to
    "bria-rmbg-2.0", a ~1GB transformer-based segmentation model - not the classic lightweight
    u2net. Running that on a full-resolution photo alone pushed container RSS to ~6.7-7GB (91%
    of this 8GB host) and ~2.5GB into swap, independent of TripoSR entirely. "u2net" is requested
    explicitly below instead: a much smaller, classic conv-net model that rembg has shipped for
    years, adequate for a single centered artifact against a plain surface.
  - The source photos here are real camera resolution (e.g. 4080x3060, ~12MP). TripoSR's own
    ImagePreprocessor downsamples to ~512px before the network ever sees the image (see
    repo/tsr/system.py), so segmenting at full camera resolution buys nothing but memory risk.
    The image is downscaled to --max-dimension before rembg runs at all.

Produces exactly the same pixel data infer.py used to compute in-process for its remove-bg path
(rembg -> resize_foreground -> composite over neutral gray), just written out as a plain RGB PNG
instead of an in-memory array. That means the second stage (infer.py) can consume this output
completely unchanged via its existing --no-remove-bg flag - no new image-handling code needed
there, and no risk of the two implementations drifting apart.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import traceback
from pathlib import Path

THIS_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(THIS_DIR / "repo"))


def _result(**kwargs) -> str:
    return json.dumps(kwargs, ensure_ascii=False)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", required=True, help="Path to the source artifact photo.")
    parser.add_argument("--output", required=True, help="Path to write the isolated RGB PNG.")
    parser.add_argument("--foreground-ratio", type=float, default=0.85)
    parser.add_argument(
        "--model-name",
        default="u2net",
        help="rembg model - default is the classic lightweight model, not rembg's own heavier default (see module docstring).",
    )
    parser.add_argument(
        "--max-dimension",
        type=int,
        default=1024,
        help="Long-edge cap applied before segmentation - see module docstring for why this is safe.",
    )
    args = parser.parse_args()

    start = time.time()
    try:
        import numpy as np
        import rembg
        from PIL import Image

        from tsr.utils import remove_background, resize_foreground

        source_image = Image.open(args.image).convert("RGB")
        source_image.thumbnail((args.max_dimension, args.max_dimension), Image.LANCZOS)

        session = rembg.new_session(args.model_name)
        image = remove_background(source_image, session)
        image = resize_foreground(image, args.foreground_ratio)
        image_arr = np.array(image).astype(np.float32) / 255.0
        # Composite the transparent cutout over neutral gray (not black/white) - same as
        # infer.py's own remove-bg branch - so the model sees a neutral backdrop instead of a
        # hard-edged alpha matte.
        image_arr = image_arr[:, :, :3] * image_arr[:, :, 3:4] + (1 - image_arr[:, :, 3:4]) * 0.5
        output_image = Image.fromarray((np.clip(image_arr, 0.0, 1.0) * 255.0).round().astype(np.uint8))

        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_image.save(output_path)

        print(
            _result(
                success=True,
                output=str(output_path),
                runtime_seconds=round(time.time() - start, 2),
            )
        )
        return 0
    except Exception as exc:  # noqa: BLE001 - single subprocess boundary, must always report JSON
        traceback.print_exc(file=sys.stderr)
        print(
            _result(
                success=False,
                message=str(exc),
                runtime_seconds=round(time.time() - start, 2),
            )
        )
        return 1


if __name__ == "__main__":
    sys.exit(main())
