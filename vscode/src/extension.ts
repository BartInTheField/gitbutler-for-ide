import * as vscode from 'vscode';
import * as path from 'path';
import { me } from 'gitbutler-core';

const GitButlerCore = me.inthefield.gitbutlerforjetbrains.core.GitButlerCore;

export interface Change {
    cliId: string;
    filePath: string;
    changeType: string;
}

export interface Commit {
    cliId: string;
    commitId: string;
    message: string;
    authorName: string;
    createdAt: string;
    conflicted: boolean;
    changes: Change[];
}

export interface Branch {
    cliId: string;
    name: string;
    commits: Commit[];
    branchStatus: string;
}

export interface Stack {
    cliId: string;
    branches: Branch[];
    assignedChanges: Change[];
}

export interface WorkspaceStatus {
    uncommittedChanges: Change[];
    branches: Branch[];
    stacks: Stack[];
}

export interface CoreEnvelope<T> {
    ok: boolean;
    value?: T;
    error?: string;
}

export function callCore<T>(jsonStr: string): T {
    let envelope: CoreEnvelope<T>;
    try {
        envelope = JSON.parse(jsonStr) as CoreEnvelope<T>;
    } catch (e: unknown) {
        const errorDetail = e instanceof Error ? e.message : String(e);
        const errorMsg = `Failed to parse GitButler core response: ${errorDetail}`;
        vscode.window.showErrorMessage(errorMsg);
        throw new Error(errorMsg);
    }

    if (!envelope || envelope.ok === false) {
        const errorMsg = envelope?.error || 'Unknown GitButler core error';
        vscode.window.showErrorMessage(errorMsg);
        throw new Error(errorMsg);
    }

    return envelope.value as T;
}

export class GitButlerNode extends vscode.TreeItem {
    constructor(
        public override readonly label: string,
        public override readonly collapsibleState: vscode.TreeItemCollapsibleState,
        public readonly nodeType: 'root-unassigned' | 'branch' | 'commit' | 'change' | 'info',
        public readonly branchName?: string,
        public readonly changeFilePath?: string,
        public readonly commitId?: string,
        public readonly childrenNodes: GitButlerNode[] = []
    ) {
        super(label, collapsibleState);

        if (nodeType === 'branch') {
            this.contextValue = 'branch';
            this.iconPath = new vscode.ThemeIcon('git-branch');
        } else if (nodeType === 'commit') {
            this.contextValue = 'commit';
            this.iconPath = new vscode.ThemeIcon('git-commit');
        } else if (nodeType === 'change') {
            this.contextValue = 'change';
            this.iconPath = new vscode.ThemeIcon('file');
            if (changeFilePath) {
                const dir = path.dirname(changeFilePath);
                this.description = dir === '.' ? undefined : dir;
            }
        } else if (nodeType === 'root-unassigned') {
            this.contextValue = 'unassigned';
            this.iconPath = new vscode.ThemeIcon('list-unordered');
        } else if (nodeType === 'info') {
            this.contextValue = 'info';
            this.iconPath = new vscode.ThemeIcon('info');
        }
    }
}

export class GitButlerTreeDataProvider implements vscode.TreeDataProvider<GitButlerNode> {
    private _onDidChangeTreeData: vscode.EventEmitter<GitButlerNode | undefined | null | void> = new vscode.EventEmitter<GitButlerNode | undefined | null | void>();
    readonly onDidChangeTreeData: vscode.Event<GitButlerNode | undefined | null | void> = this._onDidChangeTreeData.event;

    private core: InstanceType<typeof GitButlerCore> | null = null;
    private workspacePath: string | null = null;

    constructor() {
        this.updateCore();
    }

    public updateCore(): void {
        const folders = vscode.workspace.workspaceFolders;
        if (folders && folders.length > 0) {
            const newPath = folders[0].uri.fsPath;
            if (this.workspacePath !== newPath || !this.core) {
                this.workspacePath = newPath;
                try {
                    this.core = new GitButlerCore(this.workspacePath);
                } catch (err: unknown) {
                    console.error("Failed to initialize GitButlerCore:", err);
                    this.core = null;
                }
            }
        } else {
            this.workspacePath = null;
            this.core = null;
        }
    }

    public refresh(): void {
        this.updateCore();
        this._onDidChangeTreeData.fire();
    }

    public getCore(): InstanceType<typeof GitButlerCore> | null {
        return this.core;
    }

    public getWorkspacePath(): string | null {
        return this.workspacePath;
    }

    getTreeItem(element: GitButlerNode): vscode.TreeItem {
        return element;
    }

    async getChildren(element?: GitButlerNode): Promise<GitButlerNode[]> {
        if (element) {
            return element.childrenNodes;
        }

        if (!this.core || !this.workspacePath) {
            return [
                new GitButlerNode(
                    "Not a GitButler workspace",
                    vscode.TreeItemCollapsibleState.None,
                    "info"
                )
            ];
        }

        let isWorkspace = false;
        try {
            isWorkspace = this.core.isGitButlerWorkspace();
        } catch {
            isWorkspace = false;
        }

        if (!isWorkspace) {
            return [
                new GitButlerNode(
                    "Not a GitButler workspace",
                    vscode.TreeItemCollapsibleState.None,
                    "info"
                )
            ];
        }

        try {
            const rawStatusJson = await this.core.statusJson();
            const status = callCore<WorkspaceStatus>(rawStatusJson);

            const rootNodes: GitButlerNode[] = [];

            // 1. Unassigned changes node (uncommittedChanges NOT belonging to any stack's assignedChanges)
            const assignedCliIds = new Set<string>();
            if (status.stacks) {
                for (const stack of status.stacks) {
                    if (stack.assignedChanges) {
                        for (const change of stack.assignedChanges) {
                            assignedCliIds.add(change.cliId);
                        }
                    }
                }
            }

            const unassignedChanges = (status.uncommittedChanges || []).filter(
                c => !assignedCliIds.has(c.cliId)
            );

            const unassignedChildNodes = unassignedChanges.map(change =>
                new GitButlerNode(
                    path.basename(change.filePath),
                    vscode.TreeItemCollapsibleState.None,
                    "change",
                    undefined,
                    change.filePath
                )
            );

            const unassignedNode = new GitButlerNode(
                "Unassigned changes",
                unassignedChildNodes.length > 0
                    ? vscode.TreeItemCollapsibleState.Collapsed
                    : vscode.TreeItemCollapsibleState.None,
                "root-unassigned",
                undefined,
                undefined,
                undefined,
                unassignedChildNodes
            );
            rootNodes.push(unassignedNode);

            // 2. Each stack's branches, commits, and assignedChanges
            if (status.stacks && status.stacks.length > 0) {
                for (const stack of status.stacks) {
                    const assignedChangeNodes = (stack.assignedChanges || []).map(change =>
                        new GitButlerNode(
                            path.basename(change.filePath),
                            vscode.TreeItemCollapsibleState.None,
                            "change",
                            undefined,
                            change.filePath
                        )
                    );

                    for (const branch of stack.branches || []) {
                        const commitNodes = (branch.commits || []).map(commit => {
                            const commitChangeNodes = (commit.changes || []).map(change =>
                                new GitButlerNode(
                                    path.basename(change.filePath),
                                    vscode.TreeItemCollapsibleState.None,
                                    "change",
                                    undefined,
                                    change.filePath
                                )
                            );
                            return new GitButlerNode(
                                commit.message,
                                commitChangeNodes.length > 0
                                    ? vscode.TreeItemCollapsibleState.Collapsed
                                    : vscode.TreeItemCollapsibleState.None,
                                "commit",
                                branch.name,
                                undefined,
                                commit.commitId,
                                commitChangeNodes
                            );
                        });

                        const branchChildren = [...commitNodes, ...assignedChangeNodes];

                        const branchNode = new GitButlerNode(
                            branch.name,
                            branchChildren.length > 0
                                ? vscode.TreeItemCollapsibleState.Collapsed
                                : vscode.TreeItemCollapsibleState.None,
                            "branch",
                            branch.name,
                            undefined,
                            undefined,
                            branchChildren
                        );
                        rootNodes.push(branchNode);
                    }
                }
            } else if (status.branches && status.branches.length > 0) {
                for (const branch of status.branches) {
                    const commitNodes = (branch.commits || []).map(commit => {
                        const commitChangeNodes = (commit.changes || []).map(change =>
                            new GitButlerNode(
                                path.basename(change.filePath),
                                vscode.TreeItemCollapsibleState.None,
                                "change",
                                undefined,
                                change.filePath
                            )
                        );
                        return new GitButlerNode(
                            commit.message,
                            commitChangeNodes.length > 0
                                ? vscode.TreeItemCollapsibleState.Collapsed
                                : vscode.TreeItemCollapsibleState.None,
                            "commit",
                            branch.name,
                            undefined,
                            commit.commitId,
                            commitChangeNodes
                        );
                    });

                    const branchNode = new GitButlerNode(
                        branch.name,
                        commitNodes.length > 0
                            ? vscode.TreeItemCollapsibleState.Collapsed
                            : vscode.TreeItemCollapsibleState.None,
                        "branch",
                        branch.name,
                        undefined,
                        undefined,
                        commitNodes
                    );
                    rootNodes.push(branchNode);
                }
            }

            return rootNodes;
        } catch (err: unknown) {
            console.error("Error building GitButler tree data:", err);
            return [
                new GitButlerNode(
                    "Error loading status",
                    vscode.TreeItemCollapsibleState.None,
                    "info"
                )
            ];
        }
    }
}

export function activate(context: vscode.ExtensionContext) {
    const treeDataProvider = new GitButlerTreeDataProvider();

    const core = treeDataProvider.getCore();
    if (core) {
        try {
            if (!core.isGitButlerWorkspace()) {
                vscode.window.showInformationMessage("Current workspace is not a GitButler workspace.");
            }
        } catch {
            // Guard core call
        }
    }

    context.subscriptions.push(
        vscode.workspace.onDidChangeWorkspaceFolders(() => {
            treeDataProvider.refresh();
        })
    );

    context.subscriptions.push(
        vscode.window.registerTreeDataProvider('gitbutlerWorkspace', treeDataProvider)
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.refresh', () => {
            try {
                treeDataProvider.refresh();
            } catch (err: unknown) {
                const errorDetail = err instanceof Error ? err.message : String(err);
                vscode.window.showErrorMessage(`Refresh failed: ${errorDetail}`);
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.pull', async () => {
            try {
                const currentCore = treeDataProvider.getCore();
                if (!currentCore) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }
                callCore<void>(await currentCore.pull());
                vscode.window.showInformationMessage("GitButler: Pull completed successfully");
                treeDataProvider.refresh();
            } catch {
                // Surfaced by callCore or caught
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.push', async (node?: GitButlerNode) => {
            try {
                const currentCore = treeDataProvider.getCore();
                if (!currentCore) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }

                let targetBranch = node?.branchName;
                if (!targetBranch) {
                    const rawStatus = await currentCore.statusJson();
                    const status = callCore<WorkspaceStatus>(rawStatus);
                    const branches = (status.branches || []).map(b => b.name);
                    if (branches.length === 0) {
                        vscode.window.showWarningMessage("No branches available to push");
                        return;
                    }
                    targetBranch = await vscode.window.showQuickPick(branches, {
                        placeHolder: "Select branch to push"
                    });
                }

                if (!targetBranch) {
                    return;
                }

                callCore<void>(await currentCore.push(targetBranch));
                vscode.window.showInformationMessage(`GitButler: Pushed branch '${targetBranch}' successfully`);
                treeDataProvider.refresh();
            } catch {
                // Surfaced by callCore or caught
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.apply', async () => {
            try {
                const currentCore = treeDataProvider.getCore();
                if (!currentCore) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }

                const branchName = await vscode.window.showInputBox({
                    prompt: "Enter branch name to apply",
                    placeHolder: "branch-name"
                });

                if (!branchName) {
                    return;
                }

                callCore<void>(await currentCore.apply(branchName));
                vscode.window.showInformationMessage(`GitButler: Applied branch '${branchName}'`);
                treeDataProvider.refresh();
            } catch {
                // Surfaced by callCore or caught
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.unapply', async (node?: GitButlerNode) => {
            try {
                const currentCore = treeDataProvider.getCore();
                if (!currentCore) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }

                let targetBranch = node?.branchName;
                if (!targetBranch) {
                    const rawStatus = await currentCore.statusJson();
                    const status = callCore<WorkspaceStatus>(rawStatus);
                    const branches = (status.branches || []).map(b => b.name);
                    if (branches.length === 0) {
                        vscode.window.showWarningMessage("No branches available to unapply");
                        return;
                    }
                    targetBranch = await vscode.window.showQuickPick(branches, {
                        placeHolder: "Select branch to unapply"
                    });
                }

                if (!targetBranch) {
                    return;
                }

                callCore<void>(await currentCore.unapply(targetBranch));
                vscode.window.showInformationMessage(`GitButler: Unapplied branch '${targetBranch}'`);
                treeDataProvider.refresh();
            } catch {
                // Surfaced by callCore or caught
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.commit', async () => {
            try {
                const currentCore = treeDataProvider.getCore();
                const workspacePath = treeDataProvider.getWorkspacePath();
                if (!currentCore || !workspacePath) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }

                const rawStatus = await currentCore.statusJson();
                const status = callCore<WorkspaceStatus>(rawStatus);

                const branchNames = (status.branches || []).map(b => b.name);
                let targetBranch: string | undefined;
                if (branchNames.length === 0) {
                    targetBranch = await vscode.window.showInputBox({
                        prompt: "Enter target virtual branch name",
                        placeHolder: "branch-name"
                    });
                } else {
                    targetBranch = await vscode.window.showQuickPick(branchNames, {
                        placeHolder: "Select target branch to commit to"
                    });
                }

                if (!targetBranch) {
                    return;
                }

                const filePaths = (status.uncommittedChanges || []).map(c => c.filePath);
                if (filePaths.length === 0) {
                    vscode.window.showWarningMessage("No uncommitted changes available to commit");
                    return;
                }

                const selectedFilePaths = await vscode.window.showQuickPick(filePaths, {
                    canPickMany: true,
                    placeHolder: "Select files to commit"
                });

                if (!selectedFilePaths || selectedFilePaths.length === 0) {
                    return;
                }

                const commitMessage = await vscode.window.showInputBox({
                    prompt: "Enter commit message",
                    placeHolder: "commit message"
                });

                if (!commitMessage) {
                    return;
                }

                const absoluteFilePaths = selectedFilePaths.map(fp => path.join(workspacePath, fp));

                const commitId = callCore<string>(await currentCore.commit(targetBranch, commitMessage, absoluteFilePaths));
                vscode.window.showInformationMessage(`GitButler: Committed ${commitId ? `(ID: ${commitId})` : 'successfully'}`);
                treeDataProvider.refresh();
            } catch {
                // Surfaced by callCore or caught
            }
        })
    );
}

export function deactivate() {}
