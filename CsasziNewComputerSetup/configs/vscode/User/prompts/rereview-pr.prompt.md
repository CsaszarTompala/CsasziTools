---
description: "Run PR re-review workflow: pull latest branch updates, regenerate diff vs target branch, mark previous findings as fixed/not fixed/partially fixed, and update markdown."
mode: "agent"
---

Re-review PR {{$input:PR number (e.g. 113)}}.

Do the following strictly:
1. Validate PR exists/open via git:
   - `git ls-remote origin "refs/pull/{{$input:PR number (e.g. 113)}}/merge"`
2. If PR number is missing or invalid, ask for a valid PR number and stop.
3. Determine source and target branches (ask user if target branch is unclear; default to `develop` if user agrees).
4. Fetch latest refs:
   - `git fetch origin <TARGET_BRANCH> <SOURCE_BRANCH>`
5. Ensure folder exists:
   - `tmp/pr_review_{{$input:PR number (e.g. 113)}}/`
6. Regenerate diff:
   - `git diff origin/<TARGET_BRANCH>...origin/<SOURCE_BRANCH> > tmp/pr_review_{{$input:PR number (e.g. 113)}}/diff_{{$input:PR number (e.g. 113)}}_new.txt`
7. Ask user: "Do you have new requirement pictures to upload?"
8. If yes:
   - Ensure `tmp/pr_review_{{$input:PR number (e.g. 113)}}/pictures/` exists
   - Wait for upload confirmation, then continue.
9. Update existing review markdown (or create if absent):
   - `tmp/pr_review_{{$input:PR number (e.g. 113)}}/pr_{{$input:PR number (e.g. 113)}}_review.md`
10. For each existing finding, mark status explicitly:
   - `✅ FIXED`, `❌ NOT FIXED`, or `⚠️ PARTIALLY FIXED`
11. Keep findings-first structure and include a short re-review outcome summary.
