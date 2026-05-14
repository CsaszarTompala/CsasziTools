---
description: "Use when user asks for PR review or asks to review a PR. Enforce git-based PR verification, diff creation folder, optional requirement picture upload flow, and review markdown generation."
applyTo: "**"
---

When user asks for a PR review, follow this exact workflow:

1. Resolve PR number.
- If PR number is missing, ask for it before continuing.
- If provided, validate it with git:
  - `git ls-remote origin "refs/pull/<PR_NUMBER>/merge"`
- If no result, tell user PR is not open or not accessible and ask for correct PR number.

2. Determine source and target branches.
- Try to identify the feature branch associated with the PR from remote refs:
  - `git ls-remote --heads origin`
- If target branch is not clear, ask user which branch the PR is merging into.
- If user does not specify and repository convention is known, use `develop` as default target.

3. Sync refs before diffing.
- Fetch target branch and source branch:
  - `git fetch origin <TARGET_BRANCH> <SOURCE_BRANCH>`

4. Create review folder.
- Create folder: `tmp/pr_review_<PR_NUMBER>/`

5. Generate diff file.
- Create diff between merge target and source branch:
  - `git diff origin/<TARGET_BRANCH>...origin/<SOURCE_BRANCH> > tmp/pr_review_<PR_NUMBER>/diff_<PR_NUMBER>.txt`

6. Requirement pictures gate.
- Ask: "Do you have requirement pictures to upload?"
- If user says no: continue to analysis immediately.
- If user says yes:
  - Create `tmp/pr_review_<PR_NUMBER>/pictures/`
  - Tell user to upload requirement images there.
  - Pause and wait for user confirmation that upload is complete.
  - After confirmation, continue.

7. Produce review markdown.
- Create `tmp/pr_review_<PR_NUMBER>/pr_<PR_NUMBER>_review.md`.
- Follow the same style as prior review files in `tmp/diff_*/pr_*_review.md`:
  - Title with PR number and short topic
  - Review findings ordered by severity
  - Clear bug/risk explanations with concrete code references
  - Suggested fixes for each actionable finding
  - If requirement pictures were provided, add a requirements cross-reference section
  - Conclude with blockers and merge readiness summary

8. For review requests, prioritize findings first.
- Report bugs, regressions, risks, and missing tests before any summary.
- If no findings, say that explicitly and note remaining testing gaps.

9. For Critical, High, and Medium findings: add "By Example" subsections.
- For every C/H/M finding, include a subsection immediately after the suggested fix.
- Provide exactly 2 concrete Python (or relevant language) code examples:
  - **Example 1 — current broken state:** Show what the broken code actually produces at runtime, including actual output values, error messages, or wrong behavior. Use comments to label the broken output.
  - **Example 2 — a second realistic failure scenario:** Pick a different trigger (multi-sensor run, edge input, external consumer, CI environment, etc.) that makes the same defect bite in a different way.
- After the two examples, show the fixed version with a 1-line comment explaining why it is correct.
- Keep examples short (≤15 lines each) and self-contained — no imports beyond what a reader needs to understand the problem.

