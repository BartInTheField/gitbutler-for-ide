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

## Commands

| Command | ID | Runs |
|---|---|---|
| Refresh | `gitbutler.refresh` | Re-reads `but status` and repaints the tree |
| Pull Workspace | `gitbutler.pull` | `but pull` |
| Push Branch | `gitbutler.push` | `but push <branch>` |
| Apply Branch | `gitbutler.apply` | `but apply <branch>` |
| Unapply Branch | `gitbutler.unapply` | `but unapply <branch>` |
| Commit to virtual branch | `gitbutler.commit` | `but commit -b <branch> -m <message> <ids>` |

Failures surface as VS Code error toasts carrying the CLI's message; the tree refreshes
after every successful mutation. All `but` calls run asynchronously, so they never freeze
the window.

## License

[MIT](LICENSE)
