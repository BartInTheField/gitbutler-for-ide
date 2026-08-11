# Changelog

Every PR must add an entry under `## Unreleased` in the matching section
(Features / Fixes / Internal improvements) — CI blocks PRs that don't touch
this file. On release, the Unreleased section becomes the VSCode part of the
release body.

## Unreleased

### Features

### Fixes

### Internal improvements

- Added a Cursor UI end-to-end smoke test (`npm run test:ui:cursor`, `test/ui/cursor-smoke.ts`)
  that launches the extension in the Cursor editor via Playwright and asserts the GitButler
  workspace tree resolves instead of spinning. Opens the view through the
  **GitButler: Focus on Workspace View** command since Cursor has no classic activity bar; no
  screenshot because Cursor gates its workbench behind login (the screenshotted UI check stays
  on the VSCodium smoke).

## 2026.8.9.2 - 2026-08-09

### Features

- Initial VSCode extension — a GitButler workspace tree view (unassigned
  changes, stacks, branches, commits and their files) plus Pull Workspace,
  Push, Apply, Unapply and Commit-to-virtual-branch commands, all powered by
  the shared `:core` (Kotlin/JS) package.
- The workspace tree supports multi-selecting files (Ctrl/Shift/Cmd-click); a new **Commit
  Selected Changes** right-click action commits exactly the selected files to a chosen
  virtual branch.
- Drag-and-drop in the workspace tree: drag uncommitted changes onto a branch to commit
  them there, or onto a commit to amend them into it (after a confirmation).
- Added commit history actions with right-click menus: Rename Commit and Uncommit on commit rows, and Uncommit File on a file inside a commit — wired to the shared core's reword/uncommit.
- Workspace tree rows now show richer labels: the unassigned node shows its change count (`Unassigned changes (N)`), branch rows show push status (`unpushed` / `✓ pushed`), and commit rows are prefixed with the 7-char commit sha.
- Workspace tree file rows now show the change type as a prefix (e.g. `modified README.md`) and set the row's `resourceUri` so VS Code applies its native source-control color and status badge.
- Clicking a file row in the GitButler tree now opens its diff — the working-tree change for uncommitted files, or the parent-vs-commit diff for a file inside a commit.
- Route commits from VS Code's native Source Control view to a selected virtual branch: a status-bar item picks the target branch (or "no virtual branch" for plain git), and a **Commit to Virtual Branch** action on the SCM input routes the commit through the shared core.

### Fixes

- GitButler operations (status, pull, push, apply, unapply, commit) run asynchronously,
  so they never freeze the VS Code window while `but` runs.
- Commands are grouped under a **GitButler** category, so they surface when you type
  "GitButler" in the Command Palette (previously only the auto-generated view commands did).
- Tree file rows no longer duplicate the filename; the containing directory (when any) is
  shown as the row description instead of the full path.

### Internal improvements

- Added end-to-end tests: a core suite driving the compiled package against the real `but`
  CLI (host + hermetic Testcontainers modes), and a UI suite that launches the extension in
  a real editor and screenshots the GitButler view — VSCodium via Playwright's Electron API
  (default), or Microsoft VS Code via ExTester.
- Added Marketplace/Open VSX publishing metadata to `package.json` (description,
  categories, keywords, icon, repository, bugs, homepage, gallery banner) plus an
  extension `README.md`, `LICENSE` and 128×128 `icon.png`, so `vsce`/`ovsx` package
  and publish cleanly.
