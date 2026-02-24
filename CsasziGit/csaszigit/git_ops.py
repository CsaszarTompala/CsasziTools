"""
Git operations wrapper — every Git command goes through this module.

Uses subprocess to call the ``git`` CLI and parse its output into
dataclasses that the rest of the application can consume.
"""

import os
import subprocess
from dataclasses import dataclass, field
from typing import List, Optional, Tuple


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class FileStatus:
    """Status of a single working-tree or index file."""
    path: str
    status: str          # M, A, D, R, ??, etc.
    staged: bool = False


@dataclass
class CommitInfo:
    """One commit returned by ``git log``."""
    hash: str
    short_hash: str
    author: str
    date: str
    message: str
    refs: str = ""
    parents: List[str] = field(default_factory=list)


@dataclass
class BranchInfo:
    """A local or remote branch."""
    name: str
    is_current: bool = False
    is_remote: bool = False
    tracking: str = ""
    last_commit: str = ""


# ---------------------------------------------------------------------------
# Low-level helper
# ---------------------------------------------------------------------------

class GitError(Exception):
    """Raised when a git command exits with an error."""


def run_git(repo_path: str, *args: str, check: bool = True) -> subprocess.CompletedProcess:
    """Run ``git <args>`` inside *repo_path* and return the result."""
    cmd = ["git"] + list(args)
    result = subprocess.run(
        cmd,
        cwd=repo_path,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and result.returncode != 0:
        msg = result.stderr.strip() or result.stdout.strip()
        raise GitError(msg)
    return result


# ---------------------------------------------------------------------------
# Repository queries
# ---------------------------------------------------------------------------

def is_git_repo(path: str) -> bool:
    if not path or not os.path.isdir(path):
        return False
    try:
        r = run_git(path, "rev-parse", "--is-inside-work-tree", check=False)
        return r.returncode == 0 and r.stdout.strip() == "true"
    except Exception:
        return False


def get_repo_root(path: str) -> str:
    return run_git(path, "rev-parse", "--show-toplevel").stdout.strip()


def get_current_branch(repo: str) -> str:
    r = run_git(repo, "branch", "--show-current", check=False)
    branch = r.stdout.strip()
    if not branch:
        r2 = run_git(repo, "rev-parse", "--short", "HEAD", check=False)
        return f"(HEAD detached at {r2.stdout.strip()})"
    return branch


def get_status(repo: str) -> Tuple[List[FileStatus], List[FileStatus], List[FileStatus]]:
    """Return ``(staged, unstaged, untracked)`` file lists."""
    r = run_git(repo, "status", "--porcelain=v1", "-uall", check=False)

    staged: List[FileStatus] = []
    unstaged: List[FileStatus] = []
    untracked: List[FileStatus] = []

    for line in r.stdout.splitlines():
        if len(line) < 4:
            continue
        idx = line[0]
        wt = line[1]
        fp = line[3:]
        if " -> " in fp:
            fp = fp.split(" -> ", 1)[1]

        if idx == "?" and wt == "?":
            untracked.append(FileStatus(fp, "??"))
        else:
            if idx not in (" ", "?"):
                staged.append(FileStatus(fp, idx, staged=True))
            if wt not in (" ", "?"):
                unstaged.append(FileStatus(fp, wt, staged=False))

    return staged, unstaged, untracked


def get_log(repo: str, count: int = 500) -> List[CommitInfo]:
    fmt = "%H%n%h%n%an%n%ai%n%s%n%D%n%P%n---END---"
    r = run_git(
        repo, "log", f"--max-count={count}",
        f"--pretty=format:{fmt}",
        "--all",
        "--topo-order",
        check=False,
    )
    commits: List[CommitInfo] = []
    lines = r.stdout.split("\n---END---")
    for block in lines:
        bl = block.strip().splitlines()
        if len(bl) < 5:
            continue
        # pad to 7 lines
        while len(bl) < 7:
            bl.append("")
        parents = bl[6].split() if bl[6].strip() else []
        commits.append(CommitInfo(
            hash=bl[0], short_hash=bl[1], author=bl[2],
            date=bl[3], message=bl[4], refs=bl[5], parents=parents,
        ))
    return commits


def get_branches(repo: str) -> List[BranchInfo]:
    r = run_git(
        repo, "branch", "-a",
        "--format=%(HEAD)|%(refname:short)|%(upstream:short)|%(subject)",
        check=False,
    )
    branches: List[BranchInfo] = []
    for line in r.stdout.splitlines():
        if not line.strip():
            continue
        parts = line.split("|", 3)
        while len(parts) < 4:
            parts.append("")
        is_cur = parts[0].strip() == "*"
        name = parts[1].strip()
        tracking = parts[2].strip()
        last = parts[3].strip()
        is_remote = "/" in name and not name.startswith("(")
        branches.append(BranchInfo(name, is_cur, is_remote, tracking, last))
    return branches


def get_diff(repo: str, filepath: str, staged: bool = False) -> str:
    args = ["diff", "--no-color"]
    if staged:
        args.append("--cached")
    args += ["--", filepath]
    return run_git(repo, *args, check=False).stdout


def get_diff_for_untracked(repo: str, filepath: str) -> str:
    full = os.path.join(repo, filepath)
    try:
        with open(full, "r", encoding="utf-8", errors="replace") as f:
            content = f.read()
        lines = content.splitlines()
        parts = [
            f"--- /dev/null",
            f"+++ b/{filepath}",
            f"@@ -0,0 +1,{len(lines)} @@",
        ]
        for ln in lines:
            parts.append(f"+{ln}")
        return "\n".join(parts)
    except Exception:
        return "(binary or unreadable file)"


def get_commit_diff(repo: str, commit_hash: str) -> str:
    r = run_git(repo, "show", "--stat", "--patch", "--format=fuller",
                "--no-color", commit_hash, check=False)
    return r.stdout


def get_remotes(repo: str) -> List[str]:
    r = run_git(repo, "remote", check=False)
    return [x.strip() for x in r.stdout.splitlines() if x.strip()]


def get_tags(repo: str) -> List[str]:
    r = run_git(repo, "tag", "--list", check=False)
    return [x.strip() for x in r.stdout.splitlines() if x.strip()]


# ---------------------------------------------------------------------------
# Staging operations
# ---------------------------------------------------------------------------

def stage_file(repo: str, filepath: str):
    run_git(repo, "add", "--", filepath)


def stage_all(repo: str):
    run_git(repo, "add", "-A")


def unstage_file(repo: str, filepath: str):
    run_git(repo, "reset", "HEAD", "--", filepath, check=False)


def unstage_all(repo: str):
    run_git(repo, "reset", "HEAD", check=False)


# ---------------------------------------------------------------------------
# Write operations
# ---------------------------------------------------------------------------

def commit(repo: str, message: str) -> str:
    return run_git(repo, "commit", "-m", message).stdout.strip()


def push(repo: str, remote: str = "origin", branch: str = "") -> str:
    args = ["push", remote]
    if branch:
        args.append(branch)
    r = run_git(repo, *args, check=False)
    out = (r.stdout + "\n" + r.stderr).strip()
    if r.returncode != 0:
        raise GitError(out)
    return out


def pull(repo: str, remote: str = "origin", branch: str = "") -> str:
    args = ["pull", remote]
    if branch:
        args.append(branch)
    r = run_git(repo, *args, check=False)
    out = (r.stdout + "\n" + r.stderr).strip()
    if r.returncode != 0:
        raise GitError(out)
    return out


def fetch(repo: str, remote: str = "origin") -> str:
    r = run_git(repo, "fetch", remote, "--prune", check=False)
    out = (r.stdout + "\n" + r.stderr).strip()
    if r.returncode != 0:
        raise GitError(out)
    return out


def fetch_all(repo: str) -> str:
    r = run_git(repo, "fetch", "--all", "--prune", check=False)
    out = (r.stdout + "\n" + r.stderr).strip()
    if r.returncode != 0:
        raise GitError(out)
    return out


def create_branch(repo: str, name: str, checkout: bool = True) -> str:
    if checkout:
        r = run_git(repo, "checkout", "-b", name)
    else:
        r = run_git(repo, "branch", name)
    return r.stdout.strip()


def checkout_branch(repo: str, name: str) -> str:
    r = run_git(repo, "checkout", name)
    return (r.stdout + "\n" + r.stderr).strip()


def merge_branch(repo: str, name: str) -> str:
    r = run_git(repo, "merge", name)
    return (r.stdout + "\n" + r.stderr).strip()


def delete_branch(repo: str, name: str, force: bool = False) -> str:
    flag = "-D" if force else "-d"
    return run_git(repo, "branch", flag, name).stdout.strip()


def stash_save(repo: str, message: str = "") -> str:
    args = ["stash", "push"]
    if message:
        args += ["-m", message]
    return run_git(repo, *args).stdout.strip()


def stash_pop(repo: str) -> str:
    return run_git(repo, "stash", "pop").stdout.strip()


def run_arbitrary(repo: str, cmd_string: str) -> str:
    """Run an arbitrary git command string (used by AI assistant).

    Only the part after ``git `` should be supplied, e.g. ``"status -s"``.
    """
    parts = cmd_string.strip().split()
    r = run_git(repo, *parts, check=False)
    out = (r.stdout + "\n" + r.stderr).strip()
    return out
