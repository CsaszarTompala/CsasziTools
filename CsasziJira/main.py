"""
CsasziJira — Bulk Jira Story Closer.

Reads a JSON input file with project key and story numbers, then for each story:
1. Fetches the issue and checks for subtasks.
2. Sets fix version on subtasks and transitions them to Done.
3. Sets fix version on the parent story and transitions it to Done.

Usage:
    python main.py                          # uses default stories.json
    python main.py --stories my_stories.json
    python main.py --dry-run                # preview without making changes
"""

import argparse
import json
import os
import sys
import time

import requests
from requests.auth import HTTPBasicAuth


# ──────────────────────────────────────────────────────────────────────────────
# Configuration
# ──────────────────────────────────────────────────────────────────────────────

def load_json(path):
    """Load and return parsed JSON from *path*."""
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


def load_config():
    """Load config.json from the script directory."""
    config_path = os.path.join(os.path.dirname(__file__), "config.json")
    try:
        cfg = load_json(config_path)
    except FileNotFoundError:
        print(f"ERROR: config.json not found at {config_path}")
        sys.exit(1)
    except json.JSONDecodeError:
        print(f"ERROR: config.json is not valid JSON")
        sys.exit(1)

    required = ["jira_url", "email", "api_token"]
    for key in required:
        if not cfg.get(key) or cfg[key].startswith("YOUR_"):
            print(f"ERROR: '{key}' is not configured in config.json")
            sys.exit(1)
    return cfg


# ──────────────────────────────────────────────────────────────────────────────
# Jira REST helpers
# ──────────────────────────────────────────────────────────────────────────────

class JiraClient:
    """Thin wrapper around the Jira REST API v2."""

    def __init__(self, base_url, email, api_token):
        self.base_url = base_url.rstrip("/")
        self.headers = {
            "Accept": "application/json",
            "Content-Type": "application/json",
        }
        # Try Bearer (PAT for Jira Server/DC) first; fall back to Basic
        # (email + API token for Jira Cloud).
        self.auth = None
        if email and api_token:
            self.auth = HTTPBasicAuth(email, api_token)
        self._bearer_token = api_token  # kept for PAT fallback

    def _try_bearer(self):
        """Switch to Bearer token auth (Jira Server/DC PAT)."""
        self.auth = None
        self.headers["Authorization"] = f"Bearer {self._bearer_token}"

    # --- low-level helpers ---------------------------------------------------

    def _get(self, path, params=None):
        url = f"{self.base_url}/rest/api/2{path}"
        resp = requests.get(url, headers=self.headers, auth=self.auth,
                            params=params, timeout=30)
        resp.raise_for_status()
        return resp.json()

    def _put(self, path, payload):
        url = f"{self.base_url}/rest/api/2{path}"
        resp = requests.put(url, headers=self.headers, auth=self.auth,
                            json=payload, timeout=30)
        resp.raise_for_status()
        return resp

    def _post(self, path, payload):
        url = f"{self.base_url}/rest/api/2{path}"
        resp = requests.post(url, headers=self.headers, auth=self.auth,
                             json=payload, timeout=30)
        resp.raise_for_status()
        return resp

    # --- public API ----------------------------------------------------------

    def get_issue(self, issue_key):
        """Return full issue JSON including subtasks and fix versions."""
        return self._get(f"/issue/{issue_key}",
                         params={"fields": "summary,status,subtasks,fixVersions,issuetype"})

    def get_issue_all_fields(self, issue_key):
        """Return full issue JSON with all fields (needed for DoD discovery)."""
        return self._get(f"/issue/{issue_key}")

    def get_transitions(self, issue_key):
        """Return list of available transitions for *issue_key*."""
        data = self._get(f"/issue/{issue_key}/transitions")
        return data.get("transitions", [])

    def transition_issue(self, issue_key, transition_id):
        """Execute a transition on *issue_key*."""
        payload = {"transition": {"id": str(transition_id)}}
        self._post(f"/issue/{issue_key}/transitions", payload)

    def set_fix_version(self, issue_key, version_name):
        """Set (replace) fix version on *issue_key* by version name."""
        payload = {
            "fields": {
                "fixVersions": [{"name": version_name}]
            }
        }
        self._put(f"/issue/{issue_key}", payload)

    def get_project_versions(self, project_key):
        """Return all versions for a project."""
        url = f"{self.base_url}/rest/api/2/project/{project_key}/versions"
        resp = requests.get(url, headers=self.headers, auth=self.auth, timeout=30)
        resp.raise_for_status()
        return resp.json()

    def check_all_dod_items(self, issue_key):
        """
        Find all checklist-type custom fields on *issue_key* and check every
        unchecked item.  Returns a list of (field_id, count_checked) tuples
        for fields that were updated, or an empty list if nothing to do.
        Fields that are not on the edit screen are silently skipped.
        """
        issue = self.get_issue_all_fields(issue_key)
        fields = issue.get("fields", {})
        updated = []
        skipped = []

        for field_id, value in fields.items():
            if not field_id.startswith("customfield_"):
                continue
            if not isinstance(value, list) or not value:
                continue
            # Detect checklist shape: list of dicts with a 'checked' key
            if not isinstance(value[0], dict) or "checked" not in value[0]:
                continue

            unchecked = [item for item in value if not item.get("checked")]
            if not unchecked:
                continue

            # Check all items
            for item in value:
                item["checked"] = True

            try:
                self._put(f"/issue/{issue_key}", {"fields": {field_id: value}})
                updated.append((field_id, len(unchecked)))
            except requests.HTTPError as exc:
                # Fields not on the edit screen return 400 — skip them
                if exc.response is not None and exc.response.status_code == 400:
                    skipped.append(field_id)
                else:
                    raise

        return updated, skipped


# ──────────────────────────────────────────────────────────────────────────────
# Transition logic
# ──────────────────────────────────────────────────────────────────────────────

def find_done_transition(transitions, target_name="Done"):
    """
    Find a transition whose name matches *target_name* (case-insensitive)
    or whose target statusCategory key is 'done'.
    """
    # exact name match first
    for t in transitions:
        if t["name"].lower() == target_name.lower():
            return t

    # fallback: any transition landing in 'done' category
    # Skip "Canceled"/"Cancelled" — they share the 'done' category but are the
    # wrong terminal state and typically require a Cancel Reason field.
    skip_names = {"canceled", "cancelled"}
    for t in transitions:
        if t["name"].lower() in skip_names:
            continue
        to_status = t.get("to", {})
        cat = to_status.get("statusCategory", {})
        if cat.get("key") == "done":
            return t

    return None


# ──────────────────────────────────────────────────────────────────────────────
# Processor
# ──────────────────────────────────────────────────────────────────────────────

def process_issue(client, issue_key, fix_version, done_name, dry_run):
    """
    Set fix version and transition a single issue to Done.
    Returns True on success, False on failure.
    """
    current = client.get_issue(issue_key)
    summary = current["fields"]["summary"]
    status = current["fields"]["status"]["name"]

    print(f"  [{issue_key}] \"{summary}\" — status: {status}")

    if status.lower() == "done":
        print(f"    ✓ Already Done, skipping.")
        return True

    # Set fix version
    if fix_version:
        current_versions = [v["name"] for v in current["fields"].get("fixVersions", [])]
        if fix_version in current_versions:
            print(f"    ✓ Fix version '{fix_version}' already set.")
        else:
            if dry_run:
                print(f"    [DRY-RUN] Would set fix version to '{fix_version}'")
            else:
                try:
                    client.set_fix_version(issue_key, fix_version)
                    print(f"    ✓ Fix version set to '{fix_version}'")
                except requests.HTTPError as exc:
                    print(f"    ✗ Failed to set fix version: {exc}")
                    return False

    # Check all DoD / checklist items before transitioning
    if dry_run:
        print(f"    [DRY-RUN] Would check all DoD/checklist items")
    else:
        try:
            checked, skipped = client.check_all_dod_items(issue_key)
            if checked:
                for field_id, count in checked:
                    print(f"    ✓ Checked {count} item(s) in {field_id}")
            else:
                print(f"    ✓ All DoD items already checked")
            if skipped:
                print(f"    ⚠ Skipped non-editable fields: {', '.join(skipped)}")
        except requests.HTTPError as exc:
            print(f"    ✗ Failed to check DoD items: {exc}")
            return False

    # Transition to Done (may require stepping through intermediate states)
    MAX_STEPS = 10  # safety limit to avoid infinite loops
    for step in range(MAX_STEPS):
        transitions = client.get_transitions(issue_key)
        done_t = find_done_transition(transitions, done_name)

        if done_t:
            if dry_run:
                print(f"    [DRY-RUN] Would transition via '{done_t['name']}' (id={done_t['id']})")
            else:
                try:
                    client.transition_issue(issue_key, done_t["id"])
                    print(f"    ✓ Transitioned via '{done_t['name']}'")
                except requests.HTTPError as exc:
                    print(f"    ✗ Transition failed: {exc}")
                    if exc.response is not None:
                        try:
                            print(f"      Response: {exc.response.text}")
                        except Exception:
                            pass
                    return False
            return True

        # No direct Done transition — try to advance through workflow
        # Prefer: In Progress > In Verification > any other forward step
        preferred_order = ["in progress", "in verification", "in review",
                           "ready for review", "resolved"]
        next_t = None
        for pref in preferred_order:
            for t in transitions:
                if t["name"].lower() == pref:
                    next_t = t
                    break
            if next_t:
                break

        # Last resort: pick any transition that isn't backward to "New"/"Planned"/"Open"
        if not next_t:
            skip = {"new", "planned", "open", "backlog", "to do"}
            for t in transitions:
                if t["name"].lower() not in skip:
                    next_t = t
                    break

        if not next_t:
            available = [f"{t['name']} (id={t['id']})" for t in transitions]
            print(f"    ✗ No path to '{done_name}' found. Available: {available}")
            return False

        if dry_run:
            # In dry-run we can't actually change status, so just report the plan
            print(f"    [DRY-RUN] Would step via '{next_t['name']}' (id={next_t['id']}) → then continue toward Done")
            return True
        else:
            try:
                client.transition_issue(issue_key, next_t["id"])
                print(f"    → Stepped via '{next_t['name']}'")
            except requests.HTTPError as exc:
                print(f"    ✗ Intermediate transition '{next_t['name']}' failed: {exc}")
                if exc.response is not None:
                    try:
                        print(f"      Response: {exc.response.text}")
                    except Exception:
                        pass
                return False
            time.sleep(0.3)  # small delay between transitions

    print(f"    ✗ Could not reach Done within {MAX_STEPS} steps")
    return False


def process_story(client, project, story_number, fix_version, done_name, dry_run):
    """Process a single story: close subtasks first, then parent."""
    issue_key = f"{project}-{story_number}"
    print(f"\n{'='*60}")
    print(f"Processing story: {issue_key}")
    print(f"{'='*60}")

    try:
        issue = client.get_issue(issue_key)
    except requests.HTTPError as exc:
        print(f"  ✗ Failed to fetch {issue_key}: {exc}")
        return False

    summary = issue["fields"]["summary"]
    subtasks = issue["fields"].get("subtasks", [])

    print(f"  Summary: \"{summary}\"")
    print(f"  Subtasks: {len(subtasks)}")

    all_ok = True

    # Step 1: Process subtasks first
    if subtasks:
        print(f"\n  --- Subtasks ---")
        for st in subtasks:
            st_key = st["key"]
            ok = process_issue(client, st_key, fix_version, done_name, dry_run)
            if not ok:
                all_ok = False
                print(f"    ⚠ Subtask {st_key} could not be completed.")

    # Step 2: Process the parent story
    print(f"\n  --- Parent Story ---")
    ok = process_issue(client, issue_key, fix_version, done_name, dry_run)
    if not ok:
        all_ok = False

    return all_ok


# ──────────────────────────────────────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="CsasziJira — Bulk-close Jira stories with subtasks.")
    parser.add_argument(
        "--stories", default=None,
        help="Path to input JSON file (default: stories.json next to this script)")
    parser.add_argument(
        "--dry-run", action="store_true", default=None,
        help="Preview changes without modifying Jira (overrides config)")
    parser.add_argument(
        "--fix-version", default=None,
        help="Override fix version from config")
    args = parser.parse_args()

    # Load config
    cfg = load_config()
    jira_url = cfg["jira_url"]
    email = cfg["email"]
    api_token = cfg["api_token"]
    fix_version = args.fix_version or cfg.get("fix_version", "")
    done_name = cfg.get("done_transition_name", "Done")
    dry_run = args.dry_run if args.dry_run is not None else cfg.get("dry_run", False)

    # Load stories
    stories_path = args.stories or os.path.join(os.path.dirname(__file__), "stories.json")
    try:
        stories_data = load_json(stories_path)
    except FileNotFoundError:
        print(f"ERROR: stories file not found at {stories_path}")
        sys.exit(1)
    except json.JSONDecodeError:
        print(f"ERROR: stories file is not valid JSON")
        sys.exit(1)

    project = stories_data.get("project")
    story_numbers = stories_data.get("stories", [])

    if not project:
        print("ERROR: 'project' key missing from stories JSON")
        sys.exit(1)
    if not story_numbers:
        print("ERROR: 'stories' list is empty")
        sys.exit(1)

    # Summary
    print("=" * 60)
    print("CsasziJira — Bulk Story Closer")
    print("=" * 60)
    print(f"  Jira URL     : {jira_url}")
    print(f"  Project      : {project}")
    print(f"  Stories      : {story_numbers}")
    print(f"  Fix Version  : {fix_version or '(not set)'}")
    print(f"  Done via     : '{done_name}' transition")
    print(f"  Dry Run      : {dry_run}")
    print()

    if dry_run:
        print("*** DRY-RUN MODE — no changes will be made ***\n")

    # Confirm
    if not dry_run:
        answer = input("Proceed? [y/N] ").strip().lower()
        if answer != "y":
            print("Aborted.")
            sys.exit(0)

    # Connect
    client = JiraClient(jira_url, email, api_token)

    # Verify connectivity
    print("\nVerifying Jira connection...")
    try:
        test_key = f"{project}-{story_numbers[0]}"
        client.get_issue(test_key)
        print("  ✓ Connection OK (Basic auth)\n")
    except requests.HTTPError as exc:
        if exc.response is not None and exc.response.status_code == 401:
            # Basic auth failed — try Bearer (PAT for Jira Server/DC)
            print("  ⟳ Basic auth failed, trying Bearer token (PAT)...")
            client._try_bearer()
            try:
                client.get_issue(test_key)
                print("  ✓ Connection OK (Bearer/PAT)\n")
            except requests.HTTPError as exc2:
                print(f"  ✗ Bearer auth also failed: {exc2}")
                print("    Check your email and api_token in config.json")
                sys.exit(1)
        else:
            print(f"  ✗ Connection failed: {exc}")
            sys.exit(1)
    except requests.ConnectionError as exc:
        print(f"  ✗ Cannot reach {jira_url}: {exc}")
        sys.exit(1)

    # Process stories
    results = {}
    for num in story_numbers:
        ok = process_story(client, project, num, fix_version, done_name, dry_run)
        results[f"{project}-{num}"] = "OK" if ok else "FAILED"
        time.sleep(0.5)  # rate-limit courtesy

    # Report
    print(f"\n{'='*60}")
    print("Summary")
    print(f"{'='*60}")
    for key, status in results.items():
        icon = "✓" if status == "OK" else "✗"
        print(f"  {icon} {key}: {status}")

    failed = sum(1 for s in results.values() if s == "FAILED")
    if failed:
        print(f"\n{failed} story(ies) had errors.")
        sys.exit(1)
    else:
        print(f"\nAll {len(results)} story(ies) processed successfully.")


if __name__ == "__main__":
    main()
