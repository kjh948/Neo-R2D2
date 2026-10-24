# Ultralytics 🚀 AGPL-3.0 License - https://ultralytics.com/license
# Vendored geometric/assignment helpers (pure numpy, torch-free). Source: ultralytics.utils.metrics/ops @ 8.4.182
from __future__ import annotations

import numpy as np


def linear_sum_assignment(cost_matrix):
    """Rectangular linear sum assignment (pure-numpy modified Jonker-Volgenant, Crouse 2016)."""
    return _linear_sum_assignment_numpy(np.asarray(cost_matrix, dtype=np.float64))


def _linear_sum_assignment_numpy(a):
    """Solve the rectangular linear sum assignment problem with NumPy (Jonker-Volgenant SciPy-free fallback).

    Args:
        a (np.ndarray): Float64 cost matrix of shape (N, M); `+inf` forbids assignments.

    Returns:
        row_ind (np.ndarray): Row indices of the optimal assignment, sorted ascending, with length min(N, M).
        col_ind (np.ndarray): Column indices matched to each row in row_ind.
    """
    n, m = a.shape
    if n == 0 or m == 0:
        return np.empty(0, dtype=np.intp), np.empty(0, dtype=np.intp)
    transposed = n > m
    if transposed:
        a, n, m = a.T, m, n  # ensure rows <= columns
    u, v = np.zeros(n + 1), np.zeros(m + 1)  # row and column dual potentials
    p, way = np.zeros(m + 1, np.intp), np.zeros(m + 1, np.intp)  # column->row matches and path pointers
    for i in range(1, n + 1):
        p[0], j0 = i, 0
        minv, used = np.full(m + 1, np.inf), np.zeros(m + 1, bool)
        while True:  # grow a shortest augmenting path from row i
            used[j0] = True
            i0 = p[j0]
            cur = a[i0 - 1] - u[i0] - v[1:]
            improve = (~used[1:]) & (cur < minv[1:])
            minv[1:][improve], way[1:][improve] = cur[improve], j0
            candidates = np.where(used[1:], np.inf, minv[1:])
            j1 = int(np.argmin(candidates)) + 1
            delta = candidates[j1 - 1]
            if delta == np.inf:
                raise ValueError("cost matrix is infeasible")
            u[p[used]] += delta
            v[used] -= delta
            minv[~used] -= delta
            j0 = j1
            if p[j0] == 0:
                break
        while j0:  # augment along the path
            p[j0] = p[way[j0]]
            j0 = way[j0]
    cols = np.nonzero(p[1:])[0]
    rows = p[1:][cols] - 1
    row_ind, col_ind = (cols, rows) if transposed else (rows, cols)
    order = np.argsort(row_ind, kind="stable")  # match scipy's row-sorted output
    return row_ind[order].astype(np.intp), col_ind[order].astype(np.intp)



def bbox_ioa(box1: np.ndarray, box2: np.ndarray, iou: bool = False, eps: float = 1e-7) -> np.ndarray:
    """Calculate the intersection over box2 area given box1 and box2.

    Args:
        box1 (np.ndarray): A numpy array of shape (N, 4) representing N bounding boxes in x1y1x2y2 format.
        box2 (np.ndarray): A numpy array of shape (M, 4) representing M bounding boxes in x1y1x2y2 format.
        iou (bool, optional): Calculate the standard IoU if True else return inter_area/box2_area.
        eps (float, optional): A small value to avoid division by zero.

    Returns:
        (np.ndarray): A numpy array of shape (N, M) representing the intersection over box2 area, or the IoU if
            iou=True.
    """
    # Get the coordinates of bounding boxes
    b1_x1, b1_y1, b1_x2, b1_y2 = box1.T
    b2_x1, b2_y1, b2_x2, b2_y2 = box2.T

    # Intersection area
    inter_area = (np.minimum(b1_x2[:, None], b2_x2) - np.maximum(b1_x1[:, None], b2_x1)).clip(0) * (
        np.minimum(b1_y2[:, None], b2_y2) - np.maximum(b1_y1[:, None], b2_y1)
    ).clip(0)

    # Box2 area
    area = (b2_x2 - b2_x1) * (b2_y2 - b2_y1)
    if iou:
        box1_area = (b1_x2 - b1_x1) * (b1_y2 - b1_y1)
        area = area + box1_area[:, None] - inter_area

    # Intersection over box2 area
    return inter_area / (area + eps)


