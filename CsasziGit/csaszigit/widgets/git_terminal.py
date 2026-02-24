"""
Git Terminal panel — interactive command line + searchable git reference.

Provides two sections stacked vertically:
1. A terminal-like shell where the user can type and execute arbitrary
   ``git`` commands.  Output is displayed inline, scrolling upward.
2. A searchable list of common git commands with concise explanations.
"""

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPlainTextEdit, QLineEdit,
    QTreeWidget, QTreeWidgetItem, QLabel, QAbstractItemView,
    QHeaderView, QSplitter,
)
from PyQt6.QtGui import QFont, QColor, QTextCursor, QFontMetrics
from PyQt6.QtCore import Qt, pyqtSignal, QTimer, QEvent

from csaszigit import git_ops
from csaszigit.themes import palette

# ── Git command reference ────────────────────────────────────────────────────
# Each entry: (command, short description)
GIT_COMMANDS: list[tuple[str, str]] = [
    # ── Setup & Config ──
    ("git config --global user.name \"Name\"", "Set your global username"),
    ("git config --global user.email \"email\"", "Set your global email"),
    ("git config --list", "Show all configuration settings"),
    ("git config --global core.editor <editor>", "Set default editor"),
    ("git config --global core.autocrlf <value>", "Set line-ending conversion (true/false/input)"),
    ("git config --global alias.<name> \"<cmd>\"", "Create a git alias"),
    ("git config --unset <key>", "Remove a configuration entry"),
    ("git config --global credential.helper cache", "Cache credentials in memory"),
    ("git config --global credential.helper store", "Store credentials on disk (plaintext)"),
    ("git config --global init.defaultBranch main", "Set default branch name for new repos"),
    ("git config --global pull.rebase true", "Default pull to rebase instead of merge"),
    ("git config --global merge.conflictstyle diff3", "Show base in merge conflicts"),
    ("git config --global rerere.enabled true", "Enable reuse of recorded conflict resolutions"),
    # ── Creating Repos ──
    ("git init", "Initialize a new Git repository in current directory"),
    ("git init --bare", "Create a bare repository (no working tree)"),
    ("git clone <url>", "Clone a remote repository"),
    ("git clone --depth 1 <url>", "Shallow clone (only latest commit)"),
    ("git clone --branch <branch> <url>", "Clone a specific branch"),
    ("git clone --recurse-submodules <url>", "Clone including submodules"),
    ("git clone --mirror <url>", "Mirror-clone a repository"),
    # ── Staging & Snapshots ──
    ("git status", "Show working tree status"),
    ("git status -s", "Short-format status output"),
    ("git status -sb", "Short status with branch info"),
    ("git add <file>", "Stage a specific file"),
    ("git add .", "Stage all changed files in current directory"),
    ("git add -A", "Stage all changes (including deletions)"),
    ("git add -p", "Interactively stage hunks"),
    ("git add -u", "Stage only modified and deleted tracked files"),
    ("git add -N <file>", "Record intent to add a file (no content yet)"),
    ("git reset HEAD <file>", "Unstage a file (keep changes)"),
    ("git reset HEAD", "Unstage all files"),
    ("git checkout -- <file>", "Discard working-tree changes to a file"),
    ("git restore <file>", "Discard changes in working directory"),
    ("git restore --staged <file>", "Unstage a file (modern syntax)"),
    ("git restore --source=<commit> <file>", "Restore file from a specific commit"),
    ("git rm <file>", "Remove file from working tree and index"),
    ("git rm --cached <file>", "Remove file from index only (keep on disk)"),
    ("git rm -r --cached .", "Untrack all files (useful after .gitignore change)"),
    ("git mv <old> <new>", "Rename/move a file"),
    ("git update-index --assume-unchanged <file>", "Ignore local changes to a tracked file"),
    ("git update-index --no-assume-unchanged <file>", "Undo assume-unchanged"),
    ("git update-index --skip-worktree <file>", "Skip worktree for a file"),
    # ── Committing ──
    ("git commit -m \"message\"", "Commit staged changes with message"),
    ("git commit -a -m \"message\"", "Stage tracked files and commit"),
    ("git commit --amend", "Amend the last commit (edit message / add files)"),
    ("git commit --amend --no-edit", "Amend last commit keeping the same message"),
    ("git commit --allow-empty -m \"msg\"", "Create an empty commit"),
    ("git commit --fixup <commit>", "Create a fixup commit for interactive rebase"),
    ("git commit --squash <commit>", "Create a squash commit for interactive rebase"),
    ("git commit -v", "Commit showing diff in editor"),
    ("git commit --no-verify -m \"msg\"", "Commit skipping pre-commit hooks"),
    ("git commit --signoff -m \"msg\"", "Commit with Signed-off-by trailer"),
    ("git commit --date=\"<date>\"", "Commit with a custom author date"),
    # ── Branching ──
    ("git branch", "List local branches"),
    ("git branch -a", "List all branches (local + remote)"),
    ("git branch -r", "List remote-tracking branches"),
    ("git branch <name>", "Create a new branch"),
    ("git branch -d <name>", "Delete a branch (safe)"),
    ("git branch -D <name>", "Force-delete a branch"),
    ("git branch -m <old> <new>", "Rename a branch"),
    ("git branch -m <new>", "Rename the current branch"),
    ("git branch --set-upstream-to=origin/<branch>", "Set upstream tracking branch"),
    ("git branch --unset-upstream", "Remove upstream tracking"),
    ("git branch -vv", "List branches with tracking info and last commit"),
    ("git branch --merged", "List branches merged into current"),
    ("git branch --no-merged", "List branches NOT merged into current"),
    ("git branch --contains <commit>", "List branches containing a commit"),
    ("git branch --sort=-committerdate", "List branches sorted by last commit date"),
    ("git checkout <branch>", "Switch to a branch"),
    ("git checkout -b <branch>", "Create and switch to a new branch"),
    ("git checkout -b <branch> origin/<branch>", "Create tracking branch from remote"),
    ("git checkout --track origin/<branch>", "Track a remote branch"),
    ("git checkout -", "Switch to the previous branch"),
    ("git checkout <commit> -- <file>", "Restore a file from a specific commit"),
    ("git switch <branch>", "Switch branches (modern syntax)"),
    ("git switch -c <branch>", "Create and switch (modern syntax)"),
    ("git switch -", "Switch to the previous branch (modern syntax)"),
    # ── Merging ──
    ("git merge <branch>", "Merge a branch into current branch"),
    ("git merge --no-ff <branch>", "Merge with a merge commit (no fast-forward)"),
    ("git merge --ff-only <branch>", "Merge only if fast-forward is possible"),
    ("git merge --abort", "Abort a merge in progress"),
    ("git merge --continue", "Continue merge after resolving conflicts"),
    ("git merge --squash <branch>", "Squash-merge a branch"),
    ("git merge --strategy=ours <branch>", "Merge keeping our side for conflicts"),
    ("git mergetool", "Launch configured merge conflict tool"),
    # ── Rebasing ──
    ("git rebase <branch>", "Rebase current branch onto another"),
    ("git rebase -i HEAD~<n>", "Interactive rebase last n commits"),
    ("git rebase --continue", "Continue rebase after resolving conflicts"),
    ("git rebase --abort", "Abort a rebase in progress"),
    ("git rebase --skip", "Skip a conflicting commit during rebase"),
    ("git rebase --onto <new> <old> <branch>", "Rebase a range of commits onto new base"),
    ("git rebase -i --autosquash HEAD~<n>", "Autosquash fixup/squash commits"),
    ("git rebase --autostash <branch>", "Auto-stash before rebase, apply after"),
    # ── Remote ──
    ("git remote -v", "List remotes with URLs"),
    ("git remote add <name> <url>", "Add a new remote"),
    ("git remote remove <name>", "Remove a remote"),
    ("git remote rename <old> <new>", "Rename a remote"),
    ("git remote set-url <name> <url>", "Change remote URL"),
    ("git remote show <name>", "Show detailed info about a remote"),
    ("git remote prune <name>", "Delete stale remote-tracking branches"),
    ("git remote get-url <name>", "Print URL of a remote"),
    ("git remote update", "Fetch updates from all remotes"),
    # ── Fetch / Pull / Push ──
    ("git fetch", "Download objects and refs from default remote"),
    ("git fetch <remote>", "Fetch from a specific remote"),
    ("git fetch --all", "Fetch from all remotes"),
    ("git fetch --prune", "Fetch and delete stale remote-tracking branches"),
    ("git fetch --tags", "Fetch all tags from remote"),
    ("git fetch --depth <n>", "Deepen a shallow clone by n commits"),
    ("git pull", "Fetch and merge from upstream"),
    ("git pull --rebase", "Fetch and rebase instead of merging"),
    ("git pull --ff-only", "Pull only if fast-forward is possible"),
    ("git pull --autostash", "Stash changes before pull, apply after"),
    ("git push", "Push commits to default remote"),
    ("git push <remote> <branch>", "Push a branch to a specific remote"),
    ("git push -u origin <branch>", "Push and set upstream tracking"),
    ("git push origin HEAD", "Push current branch to remote"),
    ("git push --force", "Force-push (overwrites remote history)"),
    ("git push --force-with-lease", "Safer force-push (checks remote first)"),
    ("git push origin --delete <branch>", "Delete a remote branch"),
    ("git push --tags", "Push all tags to remote"),
    ("git push --all", "Push all branches to remote"),
    ("git push --set-upstream origin <branch>", "Set upstream and push"),
    # ── Stash ──
    ("git stash", "Stash working-tree changes"),
    ("git stash push -m \"message\"", "Stash with a descriptive message"),
    ("git stash push -p", "Interactively select hunks to stash"),
    ("git stash push --include-untracked", "Stash including untracked files"),
    ("git stash push --keep-index", "Stash but keep staged changes"),
    ("git stash list", "List all stashes"),
    ("git stash pop", "Apply and remove the latest stash"),
    ("git stash pop stash@{<n>}", "Pop a specific stash"),
    ("git stash apply", "Apply the latest stash (keep it in list)"),
    ("git stash apply stash@{<n>}", "Apply a specific stash"),
    ("git stash drop", "Delete the latest stash"),
    ("git stash drop stash@{<n>}", "Delete a specific stash"),
    ("git stash clear", "Delete all stashes"),
    ("git stash show -p", "Show diff of latest stash"),
    ("git stash show -p stash@{<n>}", "Show diff of a specific stash"),
    ("git stash branch <branch>", "Create a branch from a stash entry"),
    # ── Log & History ──
    ("git log", "Show commit history"),
    ("git log --oneline", "Compact one-line log"),
    ("git log --oneline --graph --all", "Visualise branch graph"),
    ("git log --oneline --graph --decorate --all", "Graph with branch/tag labels"),
    ("git log -n <number>", "Show last n commits"),
    ("git log --author=\"name\"", "Filter commits by author"),
    ("git log --since=\"2 weeks ago\"", "Commits since a date"),
    ("git log --until=\"2026-01-01\"", "Commits before a date"),
    ("git log --after=\"<date>\" --before=\"<date>\"", "Commits within a date range"),
    ("git log --grep=\"keyword\"", "Search commit messages"),
    ("git log -S \"string\"", "Find commits that add/remove a string (pickaxe)"),
    ("git log -G \"regex\"", "Find commits matching a regex in diffs"),
    ("git log -p <file>", "Show patches for a file"),
    ("git log --follow <file>", "Follow a file across renames"),
    ("git log --stat", "Show changed files per commit"),
    ("git log --name-only", "Show only names of changed files"),
    ("git log --name-status", "Show names and status (A/M/D) of changed files"),
    ("git log --diff-filter=D", "Show commits where files were deleted"),
    ("git log --merges", "Show only merge commits"),
    ("git log --no-merges", "Exclude merge commits"),
    ("git log --first-parent", "Follow only first parent (no merge branches)"),
    ("git log --pretty=format:\"%h %an %s\"", "Custom log format"),
    ("git log --all --source --remotes", "Show all commits from all refs"),
    ("git log <branch1>..<branch2>", "Commits in branch2 not in branch1"),
    ("git log <branch1>...<branch2>", "Commits unique to either branch"),
    ("git shortlog -s -n", "Summarise commits per author"),
    ("git shortlog -s -n --all", "Summarise commits per author across all branches"),
    ("git reflog", "Show reference log (all HEAD movements)"),
    ("git reflog show <branch>", "Show reflog for a specific branch"),
    ("git reflog expire --expire=now --all", "Expire all reflog entries"),
    ("git whatchanged", "Show what changed per commit (legacy)"),
    # ── Diff ──
    ("git diff", "Show unstaged changes"),
    ("git diff --staged", "Show staged changes"),
    ("git diff --cached", "Show staged changes (alias for --staged)"),
    ("git diff <branch1>..<branch2>", "Diff between two branches"),
    ("git diff <commit1>..<commit2>", "Diff between two commits"),
    ("git diff HEAD~1", "Diff against previous commit"),
    ("git diff HEAD", "Show all changes (staged + unstaged) vs HEAD"),
    ("git diff --name-only", "List changed file names only"),
    ("git diff --name-status", "List changed files with status (M/A/D)"),
    ("git diff --stat", "Summary of changes"),
    ("git diff --word-diff", "Show word-level diff"),
    ("git diff --color-words", "Coloured word-level diff"),
    ("git diff --diff-filter=M", "Show only modified files in diff"),
    ("git diff --check", "Check for whitespace errors"),
    ("git diff --no-index <path1> <path2>", "Diff two files outside Git"),
    ("git range-diff <base>..<rev1> <base>..<rev2>", "Compare two commit ranges"),
    # ── Tags ──
    ("git tag", "List tags"),
    ("git tag -l \"v1.*\"", "List tags matching a pattern"),
    ("git tag <name>", "Create a lightweight tag"),
    ("git tag -a <name> -m \"msg\"", "Create an annotated tag"),
    ("git tag -a <name> <commit>", "Tag a specific commit"),
    ("git tag -d <name>", "Delete a local tag"),
    ("git push origin <tag>", "Push a specific tag to remote"),
    ("git push origin --tags", "Push all tags to remote"),
    ("git push origin --delete tag <name>", "Delete a remote tag"),
    ("git tag -v <name>", "Verify a signed tag"),
    ("git describe", "Find the most recent tag reachable from HEAD"),
    ("git describe --tags", "Describe including lightweight tags"),
    ("git describe --always", "Describe with fallback to short hash"),
    # ── Undo / Reset ──
    ("git reset --soft HEAD~1", "Undo last commit, keep changes staged"),
    ("git reset --mixed HEAD~1", "Undo last commit, keep changes unstaged"),
    ("git reset --hard HEAD~1", "Undo last commit AND discard changes"),
    ("git reset --hard origin/<branch>", "Reset branch to match remote"),
    ("git reset --hard HEAD", "Discard all uncommitted changes"),
    ("git reset <commit>", "Move HEAD to commit, keep changes unstaged"),
    ("git reset --soft <commit>", "Move HEAD to commit, keep changes staged"),
    ("git revert <commit>", "Create a new commit that undoes a commit"),
    ("git revert --no-commit <commit>", "Revert without auto-committing"),
    ("git revert HEAD", "Revert the most recent commit"),
    ("git revert <commit1>..<commit2>", "Revert a range of commits"),
    ("git clean -f", "Remove untracked files"),
    ("git clean -fd", "Remove untracked files and directories"),
    ("git clean -fX", "Remove only ignored files"),
    ("git clean -fdx", "Remove all untracked and ignored files/dirs"),
    ("git clean -n", "Dry-run: list files that would be removed"),
    ("git clean -i", "Interactive clean"),
    ("git checkout HEAD -- <file>", "Restore file to last committed version"),
    # ── Cherry-pick ──
    ("git cherry-pick <commit>", "Apply a specific commit to current branch"),
    ("git cherry-pick <c1> <c2> <c3>", "Cherry-pick multiple commits"),
    ("git cherry-pick <start>..<end>", "Cherry-pick a range of commits"),
    ("git cherry-pick --no-commit <commit>", "Apply without committing"),
    ("git cherry-pick --abort", "Abort a cherry-pick in progress"),
    ("git cherry-pick --continue", "Continue after resolving conflicts"),
    ("git cherry -v <upstream>", "Show commits not yet pushed upstream"),
    # ── Bisect ──
    ("git bisect start", "Start binary search for a bad commit"),
    ("git bisect bad", "Mark current commit as bad"),
    ("git bisect good <commit>", "Mark a commit as good"),
    ("git bisect reset", "End bisect session"),
    ("git bisect skip", "Skip current commit in bisect"),
    ("git bisect log", "Show bisect log so far"),
    ("git bisect run <script>", "Automate bisect with a test script"),
    # ── Submodules ──
    ("git submodule add <url>", "Add a submodule"),
    ("git submodule add <url> <path>", "Add a submodule at a specific path"),
    ("git submodule init", "Initialize submodule configuration"),
    ("git submodule update", "Fetch and checkout submodule commits"),
    ("git submodule update --init", "Init and update submodules"),
    ("git submodule update --init --recursive", "Init and update submodules recursively"),
    ("git submodule update --remote", "Update submodule to latest remote commit"),
    ("git submodule status", "Show submodule status"),
    ("git submodule foreach <cmd>", "Run a command in each submodule"),
    ("git submodule deinit <path>", "Unregister a submodule"),
    ("git submodule sync", "Sync submodule URLs from .gitmodules"),
    # ── Subtree ──
    ("git subtree add --prefix=<dir> <url> <branch>", "Add a subtree"),
    ("git subtree pull --prefix=<dir> <url> <branch>", "Pull subtree updates"),
    ("git subtree push --prefix=<dir> <url> <branch>", "Push subtree to remote"),
    ("git subtree merge --prefix=<dir> <branch>", "Merge into subtree"),
    ("git subtree split --prefix=<dir>", "Extract subtree history"),
    # ── Worktrees ──
    ("git worktree add <path> <branch>", "Add a linked working tree"),
    ("git worktree add -b <branch> <path>", "Add worktree with a new branch"),
    ("git worktree list", "List working trees"),
    ("git worktree remove <path>", "Remove a working tree"),
    ("git worktree prune", "Clean up stale worktree references"),
    # ── Blame & Search ──
    ("git blame <file>", "Show who last modified each line"),
    ("git blame -L <start>,<end> <file>", "Blame a specific line range"),
    ("git blame -w <file>", "Blame ignoring whitespace changes"),
    ("git blame -M <file>", "Blame detecting moved lines within file"),
    ("git blame -C <file>", "Blame detecting code moved from other files"),
    ("git grep \"pattern\"", "Search tracked files for a pattern"),
    ("git grep -n \"pattern\"", "Search with line numbers"),
    ("git grep -c \"pattern\"", "Count matches per file"),
    ("git grep \"pattern\" <branch>", "Search in a specific branch"),
    ("git grep -l \"pattern\"", "List only filenames with matches"),
    # ── Patches ──
    ("git format-patch -<n>", "Create patch files for last n commits"),
    ("git format-patch <branch>", "Create patches for commits not in branch"),
    ("git format-patch --stdout HEAD~1 > fix.patch", "Create a single patch to stdout"),
    ("git apply <patch>", "Apply a patch file"),
    ("git apply --check <patch>", "Check if a patch applies cleanly"),
    ("git apply --stat <patch>", "Show statistics for a patch"),
    ("git am <mbox>", "Apply a mailbox-format patch"),
    ("git am --abort", "Abort an am session"),
    ("git diff > changes.patch", "Export current diff as a patch file"),
    # ── Inspection ──
    ("git show <commit>", "Show details of a commit"),
    ("git show <commit>:<file>", "Show a file at a specific commit"),
    ("git show --stat <commit>", "Show files changed in a commit"),
    ("git show <tag>", "Show annotated tag details"),
    ("git cat-file -t <hash>", "Show the type of a git object"),
    ("git cat-file -p <hash>", "Pretty-print a git object"),
    ("git cat-file -s <hash>", "Show size of a git object"),
    ("git ls-files", "List all tracked files"),
    ("git ls-files -m", "List modified tracked files"),
    ("git ls-files -o --exclude-standard", "List untracked files (respecting .gitignore)"),
    ("git ls-files -d", "List deleted tracked files"),
    ("git ls-files --stage", "List staged files with mode and hash"),
    ("git ls-tree HEAD", "List tree contents of HEAD"),
    ("git ls-tree -r HEAD", "Recursively list all files in HEAD"),
    ("git ls-tree -r --name-only HEAD", "List all file paths in HEAD"),
    ("git ls-remote <remote>", "List refs in a remote repository"),
    ("git ls-remote --tags <remote>", "List remote tags"),
    ("git for-each-ref --sort=-committerdate", "List all refs sorted by date"),
    ("git for-each-ref --format='%(refname)' refs/heads", "List local branch ref names"),
    # ── Rev-parse & Refs ──
    ("git rev-parse HEAD", "Print current commit hash"),
    ("git rev-parse --short HEAD", "Print short commit hash"),
    ("git rev-parse --abbrev-ref HEAD", "Print current branch name"),
    ("git rev-parse --show-toplevel", "Print repo root directory"),
    ("git rev-parse --git-dir", "Print path to .git directory"),
    ("git rev-list --count HEAD", "Count total commits on current branch"),
    ("git rev-list --count <branch>", "Count total commits on a branch"),
    ("git rev-list --all --count", "Count total commits in repo"),
    ("git symbolic-ref HEAD", "Show what HEAD points to"),
    ("git name-rev <commit>", "Find symbolic name for a commit"),
    ("git hash-object <file>", "Compute git hash of a file"),
    # ── Ignoring ──
    ("git check-ignore -v <file>", "Check which .gitignore rule ignores a file"),
    ("git check-ignore *", "Check ignore rules for all files"),
    ("git check-attr -a <file>", "Show all attributes for a file"),
    # ── Sparse Checkout ──
    ("git sparse-checkout init", "Enable sparse checkout"),
    ("git sparse-checkout set <dir>", "Set sparse checkout paths"),
    ("git sparse-checkout add <dir>", "Add paths to sparse checkout"),
    ("git sparse-checkout list", "List sparse checkout paths"),
    ("git sparse-checkout disable", "Disable sparse checkout"),
    # ── Notes ──
    ("git notes add -m \"note\" <commit>", "Add a note to a commit"),
    ("git notes show <commit>", "Show note for a commit"),
    ("git notes list", "List all notes"),
    ("git notes remove <commit>", "Remove a note from a commit"),
    # ── Archive / Bundle ──
    ("git archive --format=zip HEAD -o out.zip", "Create a zip archive of HEAD"),
    ("git archive --format=tar.gz HEAD -o out.tar.gz", "Create a tar.gz archive of HEAD"),
    ("git archive --format=zip <branch> -o out.zip", "Archive a specific branch"),
    ("git bundle create repo.bundle --all", "Bundle entire repo into a file"),
    ("git bundle create repo.bundle <branch>", "Bundle a specific branch"),
    ("git bundle verify repo.bundle", "Verify a bundle file"),
    ("git bundle unbundle repo.bundle", "Unbundle into current repo"),
    # ── Rerere ──
    ("git rerere status", "Show files with recorded conflict resolutions"),
    ("git rerere diff", "Show what rerere would resolve"),
    ("git rerere forget <file>", "Forget recorded resolution for a file"),
    # ── Maintenance & Internals ──
    ("git gc", "Run garbage collection"),
    ("git gc --aggressive", "Aggressive garbage collection"),
    ("git gc --prune=now", "Garbage collect and prune immediately"),
    ("git prune", "Remove unreachable objects"),
    ("git fsck", "Verify connectivity and validity of objects"),
    ("git fsck --full", "Full integrity check of all objects"),
    ("git count-objects -vH", "Show repo object storage stats"),
    ("git maintenance start", "Enable background maintenance"),
    ("git maintenance run", "Run maintenance tasks now"),
    ("git maintenance stop", "Disable background maintenance"),
    ("git pack-refs --all", "Pack all refs into .git/packed-refs"),
    ("git repack -a -d", "Repack all objects into a single pack"),
    ("git verify-pack -v <packfile>", "Verify and show pack file contents"),
    # ── Request / Email (collaboration) ──
    ("git request-pull <start> <url>", "Generate a pull request summary"),
    ("git send-email --to=<addr> <patches>", "Send patches via email"),
    # ── Misc ──
    ("git help <command>", "Show help for a git command"),
    ("git help -a", "List all available git commands"),
    ("git version", "Show installed git version"),
    ("git var -l", "List all git variables"),
    ("git diff-tree --no-commit-id --name-only -r <commit>", "List files changed in a commit"),
    ("git log --all --full-history -- <file>", "Find all commits that touched a deleted file"),
    ("git stash && git pull && git stash pop", "Quick: stash, pull, unstash"),
    ("git log --left-right --cherry-pick <b1>...<b2>", "Compare two branches for unique commits"),
    ("git rev-list --objects --all | git cat-file --batch-check", "List all objects with sizes"),
]


# ── Widget ───────────────────────────────────────────────────────────────────

class GitTerminalPanel(QWidget):
    """Interactive git terminal + searchable command reference."""

    commands_executed = pyqtSignal()     # emitted after a command runs

    def __init__(self, parent=None):
        super().__init__(parent)
        self._repo = ""

        lay = QVBoxLayout(self)
        lay.setContentsMargins(6, 6, 6, 6)
        lay.setSpacing(4)

        vsplit = QSplitter(Qt.Orientation.Vertical)
        vsplit.setOpaqueResize(False)

        # ── Top: Terminal ────────────────────────────────────────────
        term_widget = QWidget()
        term_lay = QVBoxLayout(term_widget)
        term_lay.setContentsMargins(0, 0, 0, 0)
        term_lay.setSpacing(2)

        term_title = QLabel("⌨  Git Terminal")
        term_title.setProperty("accent", True)
        term_title.setFont(QFont("Segoe UI", 12, QFont.Weight.Bold))
        term_lay.addWidget(term_title)

        self._output = QPlainTextEdit()
        self._output.setReadOnly(True)
        self._output.setFont(QFont("Consolas", 10))
        self._output.setPlaceholderText("Command output will appear here…")
        term_lay.addWidget(self._output, 1)

        # Input row
        input_row = QHBoxLayout()
        input_row.setSpacing(4)

        prompt = QLabel("git")
        prompt.setFont(QFont("Consolas", 11, QFont.Weight.Bold))
        input_row.addWidget(prompt)

        self._cmd_input = QLineEdit()
        self._cmd_input.setFont(QFont("Consolas", 11))
        self._cmd_input.setPlaceholderText("type a git command (without 'git' prefix) …")
        self._cmd_input.returnPressed.connect(self._run_command)
        input_row.addWidget(self._cmd_input)
        term_lay.addLayout(input_row)

        vsplit.addWidget(term_widget)

        # ── Bottom: Git Reference ───────────────────────────────────
        ref_widget = QWidget()
        ref_lay = QVBoxLayout(ref_widget)
        ref_lay.setContentsMargins(0, 0, 0, 0)
        ref_lay.setSpacing(2)

        ref_title = QLabel("📖  Git Command Reference")
        ref_title.setProperty("accent", True)
        ref_title.setFont(QFont("Segoe UI", 12, QFont.Weight.Bold))
        ref_lay.addWidget(ref_title)

        self._ref_filter = QLineEdit()
        self._ref_filter.setPlaceholderText("Search commands…")
        self._ref_filter.setClearButtonEnabled(True)
        self._ref_filter.setFont(QFont("Consolas", 10))
        self._ref_filter.textChanged.connect(self._filter_ref)
        ref_lay.addWidget(self._ref_filter)

        self._ref_tree = QTreeWidget()
        self._ref_tree.setHeaderLabels(["Command", "Description"])
        self._ref_tree.setRootIsDecorated(False)
        self._ref_tree.setAlternatingRowColors(True)
        self._ref_tree.setSelectionMode(
            QAbstractItemView.SelectionMode.SingleSelection
        )
        self._ref_tree.setFont(QFont("Consolas", 10))
        self._ref_tree.itemDoubleClicked.connect(self._insert_command)

        h = self._ref_tree.header()
        h.setStretchLastSection(True)
        h.setSectionResizeMode(0, QHeaderView.ResizeMode.Fixed)
        self._ref_tree.setColumnWidth(0, 320)

        self._ref_tree.verticalScrollBar().valueChanged.connect(
            self._update_ref_column_width
        )
        self._ref_tree.verticalScrollBar().rangeChanged.connect(
            lambda *_: self._update_ref_column_width()
        )
        self._ref_tree.viewport().installEventFilter(self)

        self._populate_ref()
        ref_lay.addWidget(self._ref_tree, 1)

        vsplit.addWidget(ref_widget)

        vsplit.setStretchFactor(0, 1)
        vsplit.setStretchFactor(1, 2)
        vsplit.setSizes([250, 400])

        lay.addWidget(vsplit)

        # History for up-arrow recall
        self._history: list[str] = []
        self._hist_idx = -1
        self._cmd_input.installEventFilter(self)

    # ── public ───────────────────────────────────────────────────────
    def set_repo(self, repo: str):
        self._repo = repo
        p = palette()
        self._output.appendHtml(
            f'<span style="color:{p["green"]}">Repository: {repo}</span>'
        )

    # ── command execution ────────────────────────────────────────────
    def _run_command(self):
        raw = self._cmd_input.text().strip()
        if not raw:
            return

        self._history.append(raw)
        self._hist_idx = -1
        self._cmd_input.clear()

        p = palette()

        # Show the typed command
        self._output.appendHtml(
            f'<span style="color:{p["cyan"]}; font-weight:bold;">$ git {raw}</span>'
        )

        if not self._repo:
            self._output.appendHtml(
                f'<span style="color:{p["red"]}">No repository open.</span>'
            )
            return

        try:
            result = git_ops.run_arbitrary(self._repo, raw)
            if result:
                # Escape HTML and preserve newlines
                escaped = (
                    result
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "<br>")
                )
                self._output.appendHtml(
                    f'<span style="color:{p["fg"]}">{escaped}</span>'
                )
            else:
                self._output.appendHtml(
                    f'<span style="color:{p["comment"]}">(no output)</span>'
                )
        except Exception as e:
            self._output.appendHtml(
                f'<span style="color:{p["red"]}">Error: {e}</span>'
            )

        self._output.appendPlainText("")  # blank spacer line
        self._scroll_to_bottom()
        self.commands_executed.emit()

    def _scroll_to_bottom(self):
        cursor = self._output.textCursor()
        cursor.movePosition(QTextCursor.MoveOperation.End)
        self._output.setTextCursor(cursor)

    # ── reference list ───────────────────────────────────────────────
    def _populate_ref(self, filter_text: str = ""):
        self._ref_tree.clear()
        flt = filter_text.lower()
        for cmd, desc in GIT_COMMANDS:
            if flt and flt not in cmd.lower() and flt not in desc.lower():
                continue
            item = QTreeWidgetItem([cmd, desc])
            item.setToolTip(0, cmd)
            item.setToolTip(1, desc)
            self._ref_tree.addTopLevelItem(item)
        self._update_ref_column_width()

    def _filter_ref(self, text: str):
        self._populate_ref(text.strip())

    def _insert_command(self, item: QTreeWidgetItem, _col: int):
        """Double-click a reference row → paste into the input line."""
        cmd = item.text(0).strip()
        if cmd.lower().startswith("git "):
            cmd = cmd[4:]
        self._cmd_input.setText(cmd)
        self._cmd_input.setFocus()

    # ── keyboard history ─────────────────────────────────────────────
    def eventFilter(self, obj, event):
        from PyQt6.QtCore import QEvent

        # Dynamic column resize on viewport events
        if obj is self._ref_tree.viewport() and event.type() in (
            QEvent.Type.Resize,
            QEvent.Type.Show,
        ):
            QTimer.singleShot(0, self._update_ref_column_width)

        # Command-line history
        if obj is self._cmd_input and event.type() == QEvent.Type.KeyPress:
            key = event.key()
            if key == Qt.Key.Key_Up and self._history:
                if self._hist_idx == -1:
                    self._hist_idx = len(self._history) - 1
                elif self._hist_idx > 0:
                    self._hist_idx -= 1
                self._cmd_input.setText(self._history[self._hist_idx])
                return True
            elif key == Qt.Key.Key_Down and self._history:
                if self._hist_idx != -1 and self._hist_idx < len(self._history) - 1:
                    self._hist_idx += 1
                    self._cmd_input.setText(self._history[self._hist_idx])
                else:
                    self._hist_idx = -1
                    self._cmd_input.clear()
                return True
        return super().eventFilter(obj, event)

    # ── dynamic column width ─────────────────────────────────────────
    def _update_ref_column_width(self):
        """Resize Command column to fit the widest visible item, with padding."""
        count = self._ref_tree.topLevelItemCount()
        if count == 0:
            return

        viewport = self._ref_tree.viewport()
        top_row = self._ref_tree.indexAt(viewport.rect().topLeft()).row()
        bottom_row = self._ref_tree.indexAt(viewport.rect().bottomLeft()).row()

        if top_row < 0:
            top_row = 0
        if bottom_row < 0:
            bottom_row = count - 1

        fm = QFontMetrics(self._ref_tree.font())
        max_width = 100  # minimum
        for row in range(top_row, min(bottom_row, count - 1) + 1):
            item = self._ref_tree.topLevelItem(row)
            if item:
                w = fm.horizontalAdvance(item.text(0))
                if w > max_width:
                    max_width = w

        target = max_width + 24  # padding for margins
        if abs(self._ref_tree.columnWidth(0) - target) > 4:
            self._ref_tree.setColumnWidth(0, target)
