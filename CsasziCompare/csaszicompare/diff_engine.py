"""
Diff / merge engine — line-level and character-level diff computation.

Provides:
- Two-way file comparison with inline character highlighting.
- Three-way merge with automatic resolution and conflict detection.
- Directory comparison (list of differing / added / removed files).
"""

from __future__ import annotations

import difflib
import os
from dataclasses import dataclass, field
from enum import Enum, auto
from pathlib import Path
from typing import List, Optional, Sequence, Tuple


# ═══════════════════════════════════════════════════════════════════════════
# Data types
# ═══════════════════════════════════════════════════════════════════════════

class LineTag(Enum):
    EQUAL   = auto()
    INSERT  = auto()
    DELETE  = auto()
    REPLACE = auto()
    CONFLICT = auto()
    HUNK    = auto()      # @@ hunk separator (visual only)


@dataclass
class CharSpan:
    """A run of characters within a line with a tag for highlighting."""
    start: int
    end: int
    tag: str   # "equal", "insert", "delete", "replace"


@dataclass
class DiffLine:
    """One line in a two-way diff result."""
    tag: LineTag
    left_lineno: Optional[int]     # 1-based, None if insert
    right_lineno: Optional[int]    # 1-based, None if delete
    left_text: str
    right_text: str
    left_char_spans: List[CharSpan] = field(default_factory=list)
    right_char_spans: List[CharSpan] = field(default_factory=list)


class FileState(Enum):
    SAME     = auto()
    MODIFIED = auto()
    ADDED    = auto()
    REMOVED  = auto()


@dataclass
class FileDiff:
    """Comparison result for a single file within a directory diff."""
    rel_path: str
    state: FileState


# ═══════════════════════════════════════════════════════════════════════════
# Character-level diff within a line pair
# ═══════════════════════════════════════════════════════════════════════════

def _char_diff(
    old_line: str, new_line: str,
) -> Tuple[List[CharSpan], List[CharSpan]]:
    """Return per-character highlight spans for *old_line* vs *new_line*."""
    sm = difflib.SequenceMatcher(None, old_line, new_line, autojunk=False)
    left_spans: List[CharSpan] = []
    right_spans: List[CharSpan] = []
    for op, i1, i2, j1, j2 in sm.get_opcodes():
        if op == "equal":
            left_spans.append(CharSpan(i1, i2, "equal"))
            right_spans.append(CharSpan(j1, j2, "equal"))
        elif op == "replace":
            left_spans.append(CharSpan(i1, i2, "delete"))
            right_spans.append(CharSpan(j1, j2, "insert"))
        elif op == "delete":
            left_spans.append(CharSpan(i1, i2, "delete"))
        elif op == "insert":
            right_spans.append(CharSpan(j1, j2, "insert"))
    return left_spans, right_spans


# ═══════════════════════════════════════════════════════════════════════════
# Two-way diff
# ═══════════════════════════════════════════════════════════════════════════

def diff_lines(
    left_lines: Sequence[str],
    right_lines: Sequence[str],
    context: int = 3,
) -> List[DiffLine]:
    """Produce a two-way diff with optional context folding.

    If *context* is ``None`` show all lines; otherwise collapse equal
    runs longer than 2 × context into hunk separators.
    """
    sm = difflib.SequenceMatcher(None, left_lines, right_lines, autojunk=False)
    raw: List[DiffLine] = []

    for op, i1, i2, j1, j2 in sm.get_opcodes():
        if op == "equal":
            for k in range(i2 - i1):
                raw.append(DiffLine(
                    tag=LineTag.EQUAL,
                    left_lineno=i1 + k + 1,
                    right_lineno=j1 + k + 1,
                    left_text=left_lines[i1 + k],
                    right_text=right_lines[j1 + k],
                ))
        elif op == "replace":
            n_old = i2 - i1
            n_new = j2 - j1
            n_common = min(n_old, n_new)
            for k in range(n_common):
                lt = left_lines[i1 + k]
                rt = right_lines[j1 + k]
                ls, rs = _char_diff(lt, rt)
                raw.append(DiffLine(
                    tag=LineTag.REPLACE,
                    left_lineno=i1 + k + 1,
                    right_lineno=j1 + k + 1,
                    left_text=lt,
                    right_text=rt,
                    left_char_spans=ls,
                    right_char_spans=rs,
                ))
            # If left has more lines
            for k in range(n_common, n_old):
                raw.append(DiffLine(
                    tag=LineTag.DELETE,
                    left_lineno=i1 + k + 1,
                    right_lineno=None,
                    left_text=left_lines[i1 + k],
                    right_text="",
                ))
            # If right has more lines
            for k in range(n_common, n_new):
                raw.append(DiffLine(
                    tag=LineTag.INSERT,
                    left_lineno=None,
                    right_lineno=j1 + k + 1,
                    left_text="",
                    right_text=right_lines[j1 + k],
                ))
        elif op == "delete":
            for k in range(i2 - i1):
                raw.append(DiffLine(
                    tag=LineTag.DELETE,
                    left_lineno=i1 + k + 1,
                    right_lineno=None,
                    left_text=left_lines[i1 + k],
                    right_text="",
                ))
        elif op == "insert":
            for k in range(j2 - j1):
                raw.append(DiffLine(
                    tag=LineTag.INSERT,
                    left_lineno=None,
                    right_lineno=j1 + k + 1,
                    left_text="",
                    right_text=right_lines[j1 + k],
                ))

    if context is None:
        return raw

    # Context folding
    result: List[DiffLine] = []
    last_interesting = -1
    interesting_indices = [
        i for i, d in enumerate(raw) if d.tag != LineTag.EQUAL
    ]
    if not interesting_indices:
        # All equal — show a summary
        if len(raw) > context * 2:
            result.extend(raw[:context])
            result.append(DiffLine(
                tag=LineTag.HUNK, left_lineno=None, right_lineno=None,
                left_text="", right_text="",
            ))
            result.extend(raw[-context:])
        else:
            result = raw
        return result

    for idx in interesting_indices:
        start = max(last_interesting + 1, idx - context)
        if result and start > last_interesting + 1:
            result.append(DiffLine(
                tag=LineTag.HUNK, left_lineno=None, right_lineno=None,
                left_text="", right_text="",
            ))
        for i in range(start, min(idx + context + 1, len(raw))):
            if i > last_interesting:
                result.append(raw[i])
        last_interesting = max(last_interesting, idx + context)

    # Trailing context
    remaining_start = last_interesting + 1
    if remaining_start < len(raw):
        tail = raw[remaining_start: remaining_start + context]
        if tail:
            result.extend(tail)
        if remaining_start + context < len(raw):
            result.append(DiffLine(
                tag=LineTag.HUNK, left_lineno=None, right_lineno=None,
                left_text="", right_text="",
            ))

    return result


def diff_files(left_path: str, right_path: str, context: int = 3) -> List[DiffLine]:
    """Diff two files by path."""
    def _read(p):
        try:
            with open(p, encoding="utf-8", errors="replace") as f:
                return f.read().splitlines()
        except FileNotFoundError:
            return []
    return diff_lines(_read(left_path), _read(right_path), context)


# ═══════════════════════════════════════════════════════════════════════════
# Three-way merge
# ═══════════════════════════════════════════════════════════════════════════

class MergeTag(Enum):
    RESOLVED = auto()
    CONFLICT = auto()


@dataclass
class MergeChunk:
    """A chunk in a three-way merge result."""
    tag: MergeTag
    base_lines: List[str]
    left_lines: List[str]      # "ours" / "what I am rebasing/merging"
    right_lines: List[str]     # "theirs" / "what I am rebasing on / merging into"
    result_lines: List[str]    # auto-resolved or empty if conflict


def merge3(
    base_lines: Sequence[str],
    left_lines: Sequence[str],
    right_lines: Sequence[str],
) -> List[MergeChunk]:
    """Simple three-way merge.

    Uses diff3 algorithm: diff(base, left) × diff(base, right).
    Auto-resolves when only one side changed a region or both sides
    made the same change.  Otherwise marks a conflict.
    """
    sm_left = difflib.SequenceMatcher(None, base_lines, left_lines, autojunk=False)
    sm_right = difflib.SequenceMatcher(None, base_lines, right_lines, autojunk=False)

    left_ops = sm_left.get_opcodes()
    right_ops = sm_right.get_opcodes()

    # Build change maps: for each base line, record if left/right changed it
    n_base = len(base_lines)

    # Expand opcodes into per-line flags
    left_changed = [False] * n_base
    right_changed = [False] * n_base
    for op, i1, i2, j1, j2 in left_ops:
        if op != "equal":
            for i in range(i1, i2):
                left_changed[i] = True
    for op, i1, i2, j1, j2 in right_ops:
        if op != "equal":
            for i in range(i1, i2):
                right_changed[i] = True

    # Process base line by line, grouping into chunks
    chunks: List[MergeChunk] = []

    # Get replacement mappings keyed by base range
    left_map: dict[Tuple[int, int], List[str]] = {}
    for op, i1, i2, j1, j2 in left_ops:
        if op != "equal":
            left_map[(i1, i2)] = list(left_lines[j1:j2])

    right_map: dict[Tuple[int, int], List[str]] = {}
    for op, i1, i2, j1, j2 in right_ops:
        if op != "equal":
            right_map[(i1, i2)] = list(right_lines[j1:j2])

    # Simple approach: walk through base linearly
    i = 0
    while i < n_base:
        if not left_changed[i] and not right_changed[i]:
            # Both equal — accumulate
            eq_start = i
            while i < n_base and not left_changed[i] and not right_changed[i]:
                i += 1
            chunks.append(MergeChunk(
                tag=MergeTag.RESOLVED,
                base_lines=list(base_lines[eq_start:i]),
                left_lines=list(base_lines[eq_start:i]),
                right_lines=list(base_lines[eq_start:i]),
                result_lines=list(base_lines[eq_start:i]),
            ))
        else:
            # Find the extent of the changed region
            ch_start = i
            while i < n_base and (left_changed[i] or right_changed[i]):
                i += 1
            b = list(base_lines[ch_start:i])

            # Find left/right replacements for this region
            l_repl = _find_replacement(left_map, ch_start, i, left_changed, base_lines, left_lines, sm_left)
            r_repl = _find_replacement(right_map, ch_start, i, right_changed, base_lines, right_lines, sm_right)

            if l_repl == r_repl:
                # Same change — resolved
                chunks.append(MergeChunk(
                    tag=MergeTag.RESOLVED, base_lines=b,
                    left_lines=l_repl, right_lines=r_repl,
                    result_lines=l_repl,
                ))
            elif l_repl == b:
                # Only right changed
                chunks.append(MergeChunk(
                    tag=MergeTag.RESOLVED, base_lines=b,
                    left_lines=l_repl, right_lines=r_repl,
                    result_lines=r_repl,
                ))
            elif r_repl == b:
                # Only left changed
                chunks.append(MergeChunk(
                    tag=MergeTag.RESOLVED, base_lines=b,
                    left_lines=l_repl, right_lines=r_repl,
                    result_lines=l_repl,
                ))
            else:
                # Conflict
                chunks.append(MergeChunk(
                    tag=MergeTag.CONFLICT, base_lines=b,
                    left_lines=l_repl, right_lines=r_repl,
                    result_lines=[],  # user must resolve
                ))

    # Handle lines added past the end of base
    # (These are captured in opcodes where i1==i2==n_base)
    for (i1, i2), repl in left_map.items():
        if i1 >= n_base and repl:
            chunks.append(MergeChunk(
                tag=MergeTag.RESOLVED, base_lines=[],
                left_lines=repl, right_lines=[],
                result_lines=repl,
            ))
    for (i1, i2), repl in right_map.items():
        if i1 >= n_base and repl:
            chunks.append(MergeChunk(
                tag=MergeTag.RESOLVED, base_lines=[],
                left_lines=[], right_lines=repl,
                result_lines=repl,
            ))

    return chunks


def _find_replacement(
    change_map, start, end, changed_flags, base_lines, target_lines, sm,
) -> List[str]:
    """Find what lines replace base[start:end] in the target."""
    # Check if any base lines in this region were actually changed by this side
    any_changed = any(changed_flags[k] for k in range(start, end))
    if not any_changed:
        return list(base_lines[start:end])

    # Find the opcode(s) covering this region
    result = []
    covered = set()
    for op, i1, i2, j1, j2 in sm.get_opcodes():
        if i2 <= start or i1 >= end:
            continue
        # Overlap
        if op == "equal":
            # Only include the overlapping equal part
            ov_start = max(i1, start)
            ov_end = min(i2, end)
            offset = ov_start - i1
            for k in range(ov_end - ov_start):
                result.append(target_lines[j1 + offset + k])
                covered.add(ov_start + k)
        else:
            # Changed region — include target lines (only once)
            key = (i1, i2)
            if key not in covered:
                result.extend(target_lines[j1:j2])
                covered.add(key)
    return result


# ═══════════════════════════════════════════════════════════════════════════
# Directory comparison
# ═══════════════════════════════════════════════════════════════════════════

def _collect_files(root: str) -> set[str]:
    """Return a set of relative posix paths of all files under *root*."""
    result = set()
    root_path = Path(root)
    if not root_path.is_dir():
        return result
    for p in root_path.rglob("*"):
        if p.is_file():
            result.add(p.relative_to(root_path).as_posix())
    return result


def diff_directories(left_dir: str, right_dir: str) -> List[FileDiff]:
    """Compare two directory trees and return per-file status."""
    left_files = _collect_files(left_dir)
    right_files = _collect_files(right_dir)
    all_files = sorted(left_files | right_files)

    result: List[FileDiff] = []
    for rel in all_files:
        if rel not in left_files:
            result.append(FileDiff(rel, FileState.ADDED))
        elif rel not in right_files:
            result.append(FileDiff(rel, FileState.REMOVED))
        else:
            lp = os.path.join(left_dir, rel)
            rp = os.path.join(right_dir, rel)
            try:
                with open(lp, "rb") as fl, open(rp, "rb") as fr:
                    if fl.read() == fr.read():
                        result.append(FileDiff(rel, FileState.SAME))
                    else:
                        result.append(FileDiff(rel, FileState.MODIFIED))
            except Exception:
                result.append(FileDiff(rel, FileState.MODIFIED))
    return result


def diff_directories_changed_only(left_dir: str, right_dir: str) -> List[FileDiff]:
    """Like diff_directories but excludes SAME files."""
    return [f for f in diff_directories(left_dir, right_dir) if f.state != FileState.SAME]
