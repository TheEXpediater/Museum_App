from typing import Optional, Tuple

import numpy as np
import torch
import torch.nn as nn
from skimage.measure import marching_cubes


class IsosurfaceHelper(nn.Module):
    points_range: Tuple[float, float] = (0, 1)

    @property
    def grid_vertices(self) -> torch.FloatTensor:
        raise NotImplementedError


class MarchingCubeHelper(IsosurfaceHelper):
    """Same interface/behavior as the official torchmcubes-backed helper, but implemented with
    scikit-image instead. The official requirements.txt installs torchmcubes by compiling a
    C++/CUDA extension from source (git+https://github.com/tatsy/torchmcubes.git); this
    deployment machine has no MSVC/CUDA build toolchain installed, so that install fails.
    scikit-image ships prebuilt wheels and implements the same standard marching-cubes algorithm.
    This only changes how the isosurface is extracted from the density grid - the pretrained
    TripoSR network and its weights are unchanged."""

    def __init__(self, resolution: int) -> None:
        super().__init__()
        self.resolution = resolution
        self._grid_vertices: Optional[torch.FloatTensor] = None

    @property
    def grid_vertices(self) -> torch.FloatTensor:
        if self._grid_vertices is None:
            # keep the vertices on CPU so that we can support very large resolution
            x, y, z = (
                torch.linspace(*self.points_range, self.resolution),
                torch.linspace(*self.points_range, self.resolution),
                torch.linspace(*self.points_range, self.resolution),
            )
            x, y, z = torch.meshgrid(x, y, z, indexing="ij")
            verts = torch.cat(
                [x.reshape(-1, 1), y.reshape(-1, 1), z.reshape(-1, 1)], dim=-1
            ).reshape(-1, 3)
            self._grid_vertices = verts
        return self._grid_vertices

    def forward(
        self,
        level: torch.FloatTensor,
    ) -> Tuple[torch.FloatTensor, torch.LongTensor]:
        level = -level.view(self.resolution, self.resolution, self.resolution)
        volume = level.detach().to(torch.float32).cpu().numpy().astype(np.float64)
        try:
            v_pos, faces, _normals, _values = marching_cubes(volume, level=0.0)
        except (ValueError, RuntimeError):
            # No isosurface crosses zero at this threshold (e.g. an empty/degenerate density
            # field from a bad input image) - return an empty mesh instead of crashing the job.
            v_pos = np.zeros((0, 3), dtype=np.float64)
            faces = np.zeros((0, 3), dtype=np.int64)
        v_pos_t = torch.from_numpy(v_pos.astype(np.float32))
        t_pos_idx = torch.from_numpy(faces.astype(np.int64))
        v_pos_t = v_pos_t / max(self.resolution - 1.0, 1.0)
        return v_pos_t.to(level.device), t_pos_idx.to(level.device)
