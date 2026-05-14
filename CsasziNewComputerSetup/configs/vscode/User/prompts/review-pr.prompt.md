---
description: "Run full PR review workflow from PR number: validate PR with git refs, create pr_review folder and diff, and generate comprehensive review markdown with severity-colored findings."
mode: "agent"
---

Review PR {{$input:PR number (e.g. 113)}}.

Do the following strictly:
1. Validate PR exists/open via git:
   - `git ls-remote origin "refs/pull/{{$input:PR number (e.g. 113)}}/merge"`
2. If PR number is missing or invalid, ask for a valid PR number and stop.
3. Determine source and target branches (ask user if target branch is unclear; default to `develop` if user agrees).
4. Run fetch:
   - `git fetch origin <TARGET_BRANCH> <SOURCE_BRANCH>`
5. Create folder:
   - `tmp/pr_review_{{$input:PR number (e.g. 113)}}/`
6. Create diff file:
   - `git diff origin/<TARGET_BRANCH>...origin/<SOURCE_BRANCH> > tmp/pr_review_{{$input:PR number (e.g. 113)}}/diff_{{$input:PR number (e.g. 113)}}.txt`
7. Read the diff carefully. Also read the full content of any files that were changed to understand their broader context.
8. Generate the review markdown at:
   - `tmp/pr_review_{{$input:PR number (e.g. 113)}}/pr_{{$input:PR number (e.g. 113)}}_review.md`

---

## Review Markdown Structure

The review file must follow this exact structure:

### Section 1 — PR Intent & Summary
Before any findings, write a section titled `## PR Intent & Summary`.
- Describe what this PR was trying to achieve, based primarily on what you observe in the diff (code changes, new files, removed code, renamed symbols, etc.).
- If a PR description or commit messages are available, use them as supplementary context, but the diff is the primary source of truth.
- Describe the functionality implemented or modified, and reason about the purpose of those changes.
- Keep this section factual and concise — no opinions, no findings here.

### Section 2 — Table of Contents
After the summary, write a `## Findings` section header followed immediately by a Markdown table of contents listing every finding.
- Each entry must be a clickable anchor link to the corresponding finding section below.
- Each entry must show the finding number, severity badge (colored emoji or label), and a short title.
- Severity levels and their visual markers:
  - 🔴 **CRITICAL** — bugs, crashes, data corruption, security issues
  - 🟠 **HIGH** — logic errors, wrong behavior, significant risks
  - 🟡 **MEDIUM** — code quality issues, missing edge-case handling, unclear intent
  - 🔵 **LOW** — style, naming, minor inefficiencies
  - ⚪ **INFO** — observations, suggestions without severity
- Order entries from highest to lowest severity.

### Section 3 — Individual Findings
After the table of contents, write each finding as its own `### Finding N — <Short Title>` section (using the same anchor targets referenced in the TOC).

Each finding must include all of the following:

**Severity:**
- End the finding with a severity line: `**Severity:** 🔴 CRITICAL` (or the appropriate level).

**Location:**
- File path and exact line number(s) where the issue occurs (e.g., `ARS620DP28/support_function/custom_functions.py`, lines 42–47).
- If the issue is in code that was NOT part of the changed lines but is affected by or exposed by the PR changes, mark it explicitly with a bold warning: **⚠️ Issue outside changed lines — existing code affected by this PR**.

**Explanation:**
- A detailed explanation of what the problem is, why it is a problem, and what behavior it causes or could cause.
- Include a concrete example or scenario that demonstrates the problem.

**Problematic Code:**
- A fenced code block showing the problematic code exactly as it appears.
- If the problem is localized to a specific expression or token within a line, highlight it using a comment or inline marker (e.g., `# <-- problem here`).

**Suggested Fix:**
- If there is one clear best fix, show it as a single fenced code block.
- If multiple valid solutions exist, show them as separate fenced code blocks, ordered from best to least preferred, each labeled (e.g., `Option 1 (preferred)`, `Option 2`).
- Explain why the preferred option is better.

---

## Review Rules

- Only list **negative findings**. Do not include praise, compliments, or neutral observations (except INFO-level if they are genuinely useful).
- Go through all changed files and the broader context of affected code.
- If changed code interacts with or depends on existing code in a way that introduces a problem in the existing code, report it and mark it as outside the changed lines.
- Be precise: always reference exact file paths and line numbers. Never say "somewhere in the file" or "around line X".
- Do not fabricate line numbers — if uncertain, read the file to confirm.
- If no findings exist, state that explicitly and note any remaining testing gaps or areas that could not be verified.
