# GitButler tool window

A dedicated **GitButler** tool window that mirrors `but status` inside the IDE — see your unassigned changes, virtual-branch stacks, and their commits without leaving IntelliJ.

## Where to find it

The tool window is registered on the **left stripe, secondary group** — the bottom-left corner, alongside the Git tool window. It has its own 13×13 icon with a matching dark variant.

If your project isn't checked out on `gitbutler/workspace`, the tree stays empty and shows **"Not a GitButler workspace"**.

## The tree

The panel renders `but status` as a single tree:

- **Unassigned changes (N)** — top-level group listing every uncommitted change that isn't yet assigned to a stack. File rows show the change type prefix (e.g. `modified`, `added`) followed by the path, with the filename highlighted and the directory grayed.
- **`<branch name>`** — one node per applied virtual branch, name in bold. A status suffix follows the name:
  - `✓ pushed` when the branch has nothing left to push,
  - `unpushed` when the branch has unpushed commits (also shown for `completelyUnpushed`),
  - any other CLI status string is shown verbatim.
- **Assigned changes (N)** — a child group under the *first* branch of a stack, containing the changes that are staged to that stack (mirroring the "staged to `<branch>`" lane in `but status`).
- **Commits** — each commit under its branch renders as `<7-char sha> <message first line>`. Conflicted commits are marked in red with `(conflicted)`. Expand a commit to see the files it changed, rendered like other file rows.
- **(no commits)** — placeholder shown under a branch that has no commits yet.

File rows are colored using the IDE's VCS `FileStatus` palette by change type, the same colors the Commit tool window uses: added = green, modified = blue, deleted = the IDE's deleted color; renamed/moved files are shown with the modified color. Unknown change types fall back to the regular foreground.

## Navigation

- **Double-click** or press **Enter** on an uncommitted file row → opens the working-tree diff for that file. Files that aren't known to VCS (e.g. untracked) fall back to opening the file in the editor.
- **Double-click** or press **Enter** on a file row inside a commit → opens the diff for that file as changed by that commit (parent vs. commit).
- **Double-click / Enter** on a container row (branch, commit, group) toggles expansion, same as any tree. Jumping to a commit in the Git Log moved to the commit's context menu (**Show in Git Log**).

## Toolbar actions

| Action | What it runs |
|---|---|
| **Refresh** | Re-runs `but status` and repaints the tree. |
| **Pull Workspace** | Runs `but pull` in the background — fetches the remote and rebases every applied branch onto the updated target. Success and error results land in the "GitButler" notification group. Disabled outside a `gitbutler/workspace` checkout, and while another operation is running. |
| **New Virtual Branch…** | Prompts for a name, then runs `but branch new <name>` to add an empty virtual branch to the workspace — a lane to drag changes onto or commit to. The name is validated as you type against git's ref-name rules, so an invalid one is refused in the dialog rather than by the CLI afterward. |
| **Commit to Branch** | Preselects the target branch in the Commit tool window's GitButler combo and opens that window, so **Commit** routes through `but commit -b <branch>`. If the tree selection is uncommitted-change rows, exactly those files are checked for you. |
| **Push Branch** | `but push <branch>` — pushes just the target branch. |

![The GitButler tool-window toolbar — Refresh, Pull Workspace, New Virtual Branch, Commit to Branch, Push Branch — with a branch row selected](images/tool-window-toolbar.png)

**Commit to Branch** and **Push Branch** act on the branch the tree selection belongs to: the selected branch row, or the branch enclosing the selected row (a commit, a file inside a commit, an assigned change). With nothing branch-related selected they stay visible but grayed out, and the tooltip says so.

The **New Virtual Branch…** prompt validates as you type, so `but branch new` is never called with a name git would reject:

![The New Virtual Branch prompt rejecting "bad name" with "Branch name must not contain whitespace" and a disabled OK button](images/new-virtual-branch-dialog.png)

## Context-menu actions (right-click a branch row)

| Action | What it runs |
|---|---|
| **Commit to Branch** | Same as the toolbar action above. |
| **Push Branch** | `but push <branch>` — pushes just that branch. |
| **Unapply Branch** | `but unapply <branch>` — stash-like: takes the branch out of the workspace without losing its work. Reversible. |
| **New Virtual Branch…** | Same as the toolbar action above; available from any row. |

Unapply is the one branch action that requires the branch **row** itself to be selected — it takes work out of the workspace, so the target is never inferred from a nested row.

All actions run as background tasks, surface a success or error balloon in the "GitButler" notification group, and refresh the tree when they finish. Actions are disabled while another GitButler operation is in flight so they can never overlap.

## Context-menu actions (right-click a commit row)

| Action | What it runs |
|---|---|
| **Show in Git Log** | Jumps to the commit in the Git Log tool window. |
| **Rename Commit** | Prompts for a new commit message (prefilled with the current one), then runs `but reword <commit>` if you confirm a non-blank, changed message. |
| **Uncommit** | `but uncommit <commit>` — removes the commit and moves its changes back into your working tree as uncommitted changes. No confirmation dialog. |

Rename and Uncommit target the GitButler change id of the selected commit and are disabled when no commit row is selected or another GitButler operation is in flight.

## Context-menu actions (right-click a file inside a commit)

| Action | What it runs |
|---|---|
| **Uncommit File** | `but uncommit <file-in-commit id>` — takes just that file's change out of the commit and returns it to your working tree as an uncommitted change. |

## Drag and drop

Drag one or more **uncommitted changes** from the tree to reassign them without leaving the tool window:

- **Drop onto a branch** — preselects that branch in the commit toolbar and opens the IDE's Commit tool window with exactly the dragged files checked and everything else unchecked, so you finish the commit through the normal flow with the branch already chosen. If the IDE is configured to commit through the modal dialog instead of the Commit tool window, the branch is still preselected but the checkboxes are left untouched.
- **Drop onto a commit** — amends the dragged files into that commit (`but amend`), after a confirmation you can permanently suppress with "Don't ask again" (only ticking the box *and* confirming suppresses it). The amend runs as a background task with a success or error balloon and a tree refresh, like every other action. Locked-file or other `but` errors surface as a notification with the CLI's message.

Only selections made entirely of uncommitted change rows can be dragged. Valid drop targets are branch rows and commit rows that carry a usable GitButler change id. Dragging is disabled entirely while another GitButler operation is in flight, so a drop can never overlap a running mutation.

## Auto-refresh

The panel subscribes to `GitRepository.GIT_REPO_CHANGE`, so the tree re-renders itself whenever the working tree, branches, or commits change. Bursts are debounced through a 500 ms merging queue to keep repaints cheap. The subscription is scoped to the tool window content, so it dies with the panel.

You can also hit **Refresh** in the toolbar at any time.

## CLI commands used

| Action | Command |
|---|---|
| Refresh / every load | `but status -f --json` |
| Pull Workspace | `but pull --json` |
| New Virtual Branch… | `but branch new <name> --json` |
| Push Branch | `but push <branch> --json` |
| Unapply Branch | `but unapply <branch> --json` |
| Rename Commit | `but reword <commit> -m <message> --json` |
| Uncommit / Uncommit File | `but uncommit <id> --json` |
| Drop onto a commit (amend) | `but amend -t <commit> --json <change-ids>` |

**Commit to Branch** runs no command itself — it hands off to the Commit tool window, which commits through `but commit -b <branch>` (see [Virtual-branch commit](virtual-branch-commit.md)).
