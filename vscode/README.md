# GitButler for VSCode

Work with [GitButler](https://gitbutler.com) virtual branches without leaving your editor.

> **Unofficial** — not affiliated with or endorsed by GitButler. The GitButler mark is used
> under its [CC0-1.0 brand assets](https://github.com/gitbutlerapp/gitbutler-brand-assets).

## Requirements

- [GitButler `but` CLI](https://docs.gitbutler.com/cli-overview) 0.22.0+ on your `PATH`.
- A project set up with GitButler (`but setup`), checked out on `gitbutler/workspace`.

## Features

A **GitButler** view container on the activity bar hosts a workspace tree that mirrors
`but status`:

- **Unassigned changes** — uncommitted changes not assigned to any stack.
- **Branches** — one node per applied virtual branch, each expandable into its commits.
- **Commits** — expandable into the files they changed.

When the open folder isn't on `gitbutler/workspace`, the tree shows a single
**"Not a GitButler workspace"** row.

Refresh, Pull Workspace, New Virtual Branch, Commit and Push sit in the view's title bar;
hovering a branch row reveals inline Commit to Branch, Push and Unapply icons. Every branch
picker also offers **New branch…**, which names a branch that `but commit -b` creates with
the commit — see [the VSCode docs](https://github.com/BartInTheField/gitbutler-for-ide/blob/main/docs/vscode.md).

## Commands

| Command | ID | Runs |
|---|---|---|
| Refresh | `gitbutler.refresh` | Re-reads `but status` and repaints the tree |
| Pull Workspace | `gitbutler.pull` | `but pull` |
| New Virtual Branch | `gitbutler.newBranch` | `but branch new <name>` |
| Push Branch | `gitbutler.push` | `but push <branch>` |
| Commit to Branch | `gitbutler.commitToBranch` | `but commit -b <branch>` to the branch you invoked it on |
| Apply Branch | `gitbutler.apply` | `but apply <branch>` |
| Unapply Branch | `gitbutler.unapply` | `but unapply <branch>` |
| Commit to virtual branch | `gitbutler.commit` | `but commit -b <branch> -m <message> <ids>` |

Failures surface as VS Code error toasts carrying the CLI's message; the tree refreshes
after every successful mutation. All `but` calls run asynchronously, so they never freeze
the window.

## License

[MIT](LICENSE)
