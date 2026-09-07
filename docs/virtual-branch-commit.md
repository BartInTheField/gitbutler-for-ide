# Virtual-branch commit

Commit to a GitButler virtual branch straight from the IntelliJ Commit tool window — no context-switch to the GitButler app or terminal.

## What it does

When your project is checked out on the `gitbutler/workspace` branch, a **GitButler branch** selector appears in the Commit tool window's message-area toolbar, right next to the **Amend** toggle. Pick a virtual branch, check your files, hit **Commit**, and the commit is routed through the `but` CLI instead of plain git.

The selector always shows — no per-project setup, no keybinding — and quietly stays dormant when the project isn't on `gitbutler/workspace`.

## Usage

1. Open a GitButler-managed project (branch `gitbutler/workspace`).
2. Open the Commit tool window.
3. In the message-area toolbar, choose a virtual branch from the **GitButler branch** combo — or pick `Git: no virtual branch` to fall through to a regular git commit.
4. Select the files to include and write a commit message.
5. Hit **Commit** or **Commit and Push…**.

The plugin remembers the last-used virtual branch per project.

## Committing to a new virtual branch

The combo's **New branch…** item prompts for a name and selects it, so the next commit lands on a branch that doesn't exist yet: `but commit -b <name>` creates it as an unstacked branch as part of the commit. Nothing is created until you actually commit, so cancelling the commit leaves the workspace untouched.

The name is validated as you type against git's ref-name rules (no whitespace, no `~^:?*[\`, no `..`, no `/` at either end, and so on), so an invalid one is refused in the dialog instead of coming back as a CLI error. The typed name stays visible and checked in the combo even though `but status` doesn't know it yet.

To create an *empty* virtual branch up front instead — a lane to drag changes onto — use **New Virtual Branch…** in the [GitButler tool window](tool-window.md), which runs `but branch new`.

## CLI commands used

Every GitButler action runs the `but` CLI with `--json` (plus `BUT_OUTPUT_FORMAT=json` in the environment):

| Step | Command |
|---|---|
| List branches; map file paths → change IDs | `but status -f --json` |
| Commit the selected changes | `but commit -b <branch> -m <message> --json <change-ids>` |
| Commit to a name that doesn't exist yet | same command — `-b` creates the branch when it is unknown |
| Push (only for *Commit and Push*) | `but push <branch> --json` |

If no virtual branch is picked, the plugin's checkin handler steps aside and IntelliJ's normal git commit runs untouched.

## Notifications

All commit outcomes land in the "GitButler" notification group:

- **Committed to `<branch>`** — commit succeeded; the subtitle is the short SHA, or the literal "Commit created" when the CLI didn't return one.
- **Committed to `<branch>` and pushed** — *Commit and Push* succeeded end-to-end.
- **Committed to `<branch>`, but push failed** — commit is in; only the push failed, with the CLI's error as the subtitle.
- **GitButler commit failed** — the commit itself failed. The commit dialog stays open and your message is preserved.
- **GitButler commit failed — Commit message is empty** — sent when you try to commit through a virtual branch without a message.

## Limitations

- Single git repository per project.
- Virtual branches with identical names across stacks are ambiguous — the CLI is invoked by branch name.
- New branches are always created unstacked; there's no way to stack one above or below an existing branch from the commit window (`but branch new --above/--below` would be the hook for this).
