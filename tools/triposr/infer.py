"""Isolated single-image TripoSR inference entry point.

Runs inside tools/triposr/.venv only - never imported by the FastAPI backend. The backend
(app/services/model3d/triposr_provider.py) invokes this as a subprocess and parses the single
JSON line it prints to stdout on completion. This script performs exactly one generation attempt
at the settings it is given; retrying at a lower profile or falling back to CPU is the caller's
responsibility (see triposr_provider.py), not this script's.

Uses the official pretrained stabilityai/TripoSR model and the official tsr/ package (vendored
as a git checkout in ./repo, with only the marching-cubes isosurface step swapped for
scikit-image - see repo/tsr/models/isosurface.py for why).
"""
from __future__ import annotations

import argparse
import json
import os
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
    parser.add_argument("--image", required=True, help="Path to the primary input image.")
    parser.add_argument("--output-glb", required=True, help="Path to write the resulting GLB.")
    parser.add_argument("--device", default="cuda", choices=["cuda", "cpu"])
    parser.add_argument("--model-path", default="stabilityai/TripoSR")
    parser.add_argument("--chunk-size", type=int, default=2048)
    parser.add_argument("--mc-resolution", type=int, default=192)
    parser.add_argument("--bake-texture", action="store_true")
    parser.add_argument("--texture-resolution", type=int, default=1024)
    parser.add_argument("--foreground-ratio", type=float, default=0.85)
    parser.add_argument("--no-remove-bg", action="store_true")
    args = parser.parse_args()

    start = time.time()
    try:
        import numpy as np
        import torch
        from PIL import Image

        from tsr.system import TSR
        from tsr.utils import remove_background, resize_foreground

        model = TSR.from_pretrained(args.model_path, config_name="config.yaml", weight_name="model.ckpt")
        model.renderer.set_chunk_size(args.chunk_size)
        model.to(args.device)

        if args.no_remove_bg:
            image = np.array(Image.open(args.image).convert("RGB"))
        else:
            import rembg

            session = rembg.new_session()
            image = remove_background(Image.open(args.image), session)
            image = resize_foreground(image, args.foreground_ratio)
            image_arr = np.array(image).astype(np.float32) / 255.0
            image_arr = image_arr[:, :, :3] * image_arr[:, :, 3:4] + (1 - image_arr[:, :, 3:4]) * 0.5
            image = image_arr

        with torch.no_grad():
            scene_codes = model([image], device=args.device)

        meshes = model.extract_mesh(scene_codes, not args.bake_texture, resolution=args.mc_resolution)
        mesh = meshes[0]

        output_path = Path(args.output_glb)
        output_path.parent.mkdir(parents=True, exist_ok=True)

        has_texture = False
        if args.bake_texture:
            try:
                from tsr.bake_texture import bake_texture
                import trimesh

                bake_output = bake_texture(mesh, model, scene_codes[0], args.texture_resolution)
                texture_image = Image.fromarray(
                    (np.clip(bake_output["colors"], 0.0, 1.0) * 255.0).astype(np.uint8)
                ).transpose(Image.FLIP_TOP_BOTTOM)
                new_vertices = mesh.vertices[bake_output["vmapping"]]
                new_faces = bake_output["indices"]
                visual = trimesh.visual.TextureVisuals(uv=bake_output["uvs"], image=texture_image)
                textured_mesh = trimesh.Trimesh(vertices=new_vertices, faces=new_faces, visual=visual, process=False)
                textured_mesh.export(str(output_path), file_type="glb")
                mesh = textured_mesh
                has_texture = True
            except Exception as bake_exc:  # texture baking is best-effort; fall back to vertex color
                sys.stderr.write(f"texture bake failed, falling back to vertex color: {bake_exc}\n")
                traceback.print_exc(file=sys.stderr)
                mesh.export(str(output_path), file_type="glb")
        else:
            mesh.export(str(output_path), file_type="glb")

        runtime_seconds = round(time.time() - start, 2)
        print(
            _result(
                success=True,
                output=str(output_path),
                device=args.device,
                vertex_count=int(mesh.vertices.shape[0]),
                face_count=int(mesh.faces.shape[0]),
                has_texture=has_texture,
                runtime_seconds=runtime_seconds,
                chunk_size=args.chunk_size,
                mc_resolution=args.mc_resolution,
                texture_resolution=args.texture_resolution if args.bake_texture else None,
            )
        )
        return 0
    except Exception as exc:  # noqa: BLE001 - single subprocess boundary, must always report JSON
        message = str(exc)
        error_type = "error"
        is_oom = "out of memory" in message.lower() or type(exc).__name__ == "OutOfMemoryError"
        if is_oom:
            error_type = "cuda_oom"
        traceback.print_exc(file=sys.stderr)
        print(
            _result(
                success=False,
                error_type=error_type,
                message=message,
                device=args.device,
                runtime_seconds=round(time.time() - start, 2),
            )
        )
        return 1


if __name__ == "__main__":
    sys.exit(main())
