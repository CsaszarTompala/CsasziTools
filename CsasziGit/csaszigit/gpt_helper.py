"""
GPT helper — sends natural-language Git requests to OpenAI and returns
a structured response with suggested commands.
"""

import json
import requests
from typing import Optional, Dict, Any


SYSTEM_PROMPT = """\
You are a Git command-line expert assistant built into a Git GUI application.
The user will describe what they want to do with their repository and you must
respond with the exact git commands that accomplish it.

Rules:
1. Reply ONLY with valid JSON in this exact schema (no markdown, no code fences):
   {
     "explanation": "<short human-readable explanation of what will happen>",
     "commands": ["git ...", "git ..."],
     "warning": "<optional warning about destructive operations, or empty string>"
   }
2. Each command MUST start with "git ".
3. Do NOT include commands that are not git commands.
4. If the request is unclear or dangerous, put a clear warning in "warning".
5. Keep explanations concise — one or two sentences.
6. If you truly cannot help, set "commands" to an empty list and explain why
   in "explanation".
"""


class GptHelper:
    """Handles communication with the OpenAI Chat Completions API."""

    def __init__(self, api_key: str, model: str = "gpt-4o-mini"):
        self.api_key = api_key
        self.model = model

    def suggest_commands(
        self,
        user_request: str,
        current_branch: str = "",
        repo_status: str = "",
    ) -> Dict[str, Any]:
        """Ask GPT for git commands.

        Returns a dict with keys ``explanation``, ``commands``, ``warning``.
        On error the ``explanation`` contains the error message and
        ``commands`` is empty.
        """
        context_parts = []
        if current_branch:
            context_parts.append(f"Current branch: {current_branch}")
        if repo_status:
            context_parts.append(f"Repository status (git status --short):\n{repo_status}")

        user_msg = user_request.strip()
        if context_parts:
            user_msg = "\n".join(context_parts) + "\n\nUser request: " + user_msg

        try:
            resp = requests.post(
                "https://api.openai.com/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self.model,
                    "temperature": 0.2,
                    "messages": [
                        {"role": "system", "content": SYSTEM_PROMPT},
                        {"role": "user", "content": user_msg},
                    ],
                },
                timeout=60,
            )
            resp.raise_for_status()
            data = resp.json()
            content = data["choices"][0]["message"]["content"].strip()
            # Strip markdown code fences if present
            if content.startswith("```"):
                content = content.split("\n", 1)[1] if "\n" in content else content[3:]
            if content.endswith("```"):
                content = content[: content.rfind("```")]
            result = json.loads(content)
            # Normalise keys
            return {
                "explanation": result.get("explanation", ""),
                "commands": result.get("commands", []),
                "warning": result.get("warning", ""),
            }
        except requests.exceptions.RequestException as exc:
            return {
                "explanation": f"Network error: {exc}",
                "commands": [],
                "warning": "",
            }
        except (json.JSONDecodeError, KeyError, IndexError) as exc:
            return {
                "explanation": f"Could not parse GPT response: {exc}",
                "commands": [],
                "warning": "",
            }
