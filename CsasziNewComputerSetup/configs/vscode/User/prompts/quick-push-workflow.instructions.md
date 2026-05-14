---
description: "Quick /push command to commit and push all changes. System-wide workflow trigger."
applyTo: "**"
---

# Quick Push Workflow (`/push` command)

## Activation
When the user types `/push` (with or without additional text), immediately:

1. **Interpret intent:** If unclear what should be committed, ask a clarifying question:
   - "What should be included in this commit? (e.g., specific files, all current changes?)"
   - Accept the answer and proceed without further hesitation.

2. **Stage and commit:**
   - Use `git status` to identify modified/new files
   - Stage relevant files with `git add`
   - Create a concise commit message describing the changes
   - Commit with `git commit -m "..."`

3. **Push:**
   - Use `git push` to push to the current branch
   - Verify push succeeded (check git log)

4. **Report:**
   - Brief summary: commit SHA, branch, files changed
   - No lengthy explanations unless user asks

## Rules
- **No approval gates:** Do not ask "Should I commit?" – just do it (after clarifying what to commit if needed)
- **Ask once if ambiguous:** Only ask once. If user answers, proceed without further questions.
- **All workspaces:** This applies system-wide across all VS Code workspaces
- **Git context:** Use the current git repository automatically detected from the workspace

## Example flows:

**Clear context (no question needed):**
```
User: /push
Agent: [detects 3 modified files in workspace, stages and commits all]
Agent: Pushed commit abc123 to feature/branch (3 files changed)
```

**Ambiguous context (ask once):**
```
User: /push
Agent: What should be included? (e.g., all changes, specific files?)
User: all
Agent: [stages all, commits, pushes]
Agent: Pushed commit def456 to feature/branch (7 files changed)
```

---
**Bypass approval:** This workflow should never prompt for permission to proceed. It is a user-initiated trigger. Once activated by `/push`, execute to completion.
