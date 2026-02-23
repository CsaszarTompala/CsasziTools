# Copilot repository instructions

## Documentation maintenance rule (required)

For every code or file change in this repository:

1. Evaluate whether documentation updates are needed in:
   - `CONTENTS.md` (repository-wide index)
   - The affected tool's `README.md`
2. If updates are needed, apply them in the same change set.
3. Before any Git commit, perform a final documentation check and refresh:
   - Ensure `CONTENTS.md` is still accurate.
   - Ensure each changed tool/project README remains accurate.

## Scope mapping

- Changes under `Common/Menu/` -> review `Common/Menu/README.md` and `CONTENTS.md`
- Changes under `Mini_projects/Unix_timestamper/` -> review `Mini_projects/Unix_timestamper/README.md` and `CONTENTS.md`
- Changes under `Mouse_and_keyboard_replayer/` -> review `Mouse_and_keyboard_replayer/README.md` and `CONTENTS.md`
- Changes under `OpenJenkins/` -> review `OpenJenkins/README.md` and `CONTENTS.md`
- Changes under `CsasziGit/` -> review `CsasziGit/README.md` and `CONTENTS.md`
- Changes under `MoneySplitter/` -> review `MoneySplitter/Python/README.md` and `CONTENTS.md`
- Changes under `RemoteDesktop/` -> review `RemoteDesktop/README.md` and `CONTENTS.md`
- Changes under `TravelTool/` -> review `TravelTool/README.md` and `CONTENTS.md`

## Decision policy

- If behavior, setup, configuration, CLI usage, file layout, dependencies, or examples changed, README update is required.
- If repository structure or README paths changed, `CONTENTS.md` update is required.
- If no user-visible or maintenance-relevant information changed, document that no README/CONTENTS update was needed.
