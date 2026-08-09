# Changelog

Every PR must add an entry under `## Unreleased` in the matching section
(Features / Fixes / Internal improvements) — CI blocks PRs that don't touch
this file. On release, the Unreleased section becomes the VSCode part of the
release body.

## Unreleased

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
