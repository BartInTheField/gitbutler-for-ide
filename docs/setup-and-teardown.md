# Set up GitButler, and leave GitButler mode

The integrations stay quiet until a repository is on `gitbutler/workspace`. You can reach
that state, and step back out of it, from the IDE. Both actions shell out to the GitButler
CLI 0.22.0+ (the flags below are the ones published for 0.22.3).

## When the prompt appears

The prompt is shown only when all of these are true:

- the project is a Git repository
- the `but` CLI is on `PATH` (or in one of the well-known install locations)
- the repository is **not** checked out on `gitbutler/workspace`

Folders that are not Git repositories are unchanged. `but setup --init` (create a Git repo
when none exists) is not offered.

**JetBrains.** A dismissible sticky balloon: "This repository isn't managed by GitButler
yet — Set up GitButler". **Set up GitButler** runs setup. **Don't ask again** is remembered
for this project. Closing the balloon hides it until the next time you open the project, or
until you leave GitButler mode and come back. The same action is **Git → GitButler → Set
Up GitButler for This Project**, and a **Set up GitButler** link on the empty tool window.

**VS Code.** A one-time information message with **Set up GitButler**, plus **GitButler:
Set Up This Repository** in the Command Palette. The workspace tree shows a **Set up
GitButler** row next to "Not a GitButler workspace". The message is not repeated for that
workspace; after you leave GitButler mode it can appear again the next time the folder is
not a GitButler workspace.

## Set up (`but setup`)

```text
but setup --json
```

On 0.22.3 this:

- adds the repository to the GitButler project registry
- switches to `gitbutler/workspace` when you are not already on it
- sets the default target branch to the remote's HEAD
- adds a `gb-local` remote when the repository has no push remote

`--json` prints `{repositoryPath, projectStatus, target}`. `projectStatus` is `added` or
`alreadyexists`. `target` carries `branchName`, `remoteName` and `newlySet`. A non-zero
exit is a failure (the message is stderr). Exit 0 means the repository was set up even if
the JSON cannot be parsed.

Nothing in the IDE passes `--init`.

After setup succeeds, Git is asked to re-read HEAD. The tool window, the commit-window
branch combo (JetBrains) and Source Control routing (VS Code) follow `gitbutler/workspace`
without an IDE restart.

## Leave GitButler mode (`but teardown`)

```text
but teardown --json
but teardown --checkout-to <local-branch> --json
```

On 0.22.3 this:

- snapshots the workspace in the oplog
- checks out the first applied branch, or the branch given with `-c` / `--checkout-to`
- cherry-picks dangling commits that were made directly on `gitbutler/workspace`
- prints how to return (`but setup`)

`--json` prints `{snapshotId, checkedOutBranch}`.

The action is named **Leave GitButler Mode…**. JetBrains puts it in the GitButler tool
window overflow menu (the **⋯** on the toolbar). VS Code exposes **GitButler: Leave
GitButler Mode…** in the Command Palette.

Both hosts confirm first, because teardown changes the checked-out branch. The branch
picker is filled from `but status` (applied virtual branches first, then other local
branches) and starts on the first applied branch. **First applied branch** omits
`--checkout-to` and lets the CLI choose.

Bare `but teardown` fails on 0.22.3 when no branch is applied ("Failed to determine
checkout target branch"). In that case the dialog requires a local branch instead of
offering the default.

After teardown, the plugin goes dormant again: the tool window shows "Not a GitButler
workspace", and commits are ordinary Git commits.
