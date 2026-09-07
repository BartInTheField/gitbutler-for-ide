import * as vscode from 'vscode';
import * as path from 'path';
import { me } from 'gitbutler-core';

const GitButlerCore = me.inthefield.gitbutlerforjetbrains.core.GitButlerCore;
const ButBranch = me.inthefield.gitbutlerforjetbrains.core.ButBranch;
const ButCommits = me.inthefield.gitbutlerforjetbrains.core.ButCommits;

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
        public readonly childrenNodes: GitButlerNode[] = [],
        public readonly isCommitFile: boolean = false
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
            if (isCommitFile) {
                this.contextValue = 'commit-file';
            }
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

    private changeNode(change: Change, commitId?: string): GitButlerNode {
        const node = new GitButlerNode(
            `${change.changeType} ${path.basename(change.filePath)}`,
            vscode.TreeItemCollapsibleState.None,
            'change',
            undefined,
            change.filePath,
            commitId,
            [],
            commitId !== undefined
        );
        if (this.workspacePath) {
            node.resourceUri = vscode.Uri.file(path.join(this.workspacePath, change.filePath));
            node.command = {
                command: 'gitbutler.openDiff',
                title: 'Open Diff',
                arguments: commitId !== undefined
                    ? [path.join(this.workspacePath, change.filePath), commitId]
                    : [path.join(this.workspacePath, change.filePath)]
            };
        }
        return node;
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

            const unassignedChildNodes = unassignedChanges.map(change => this.changeNode(change));

            const unassignedNode = new GitButlerNode(
                `Unassigned changes (${unassignedChanges.length})`,
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
                        this.changeNode(change)
                    );

                    for (const branch of stack.branches || []) {
                        const commitNodes = (branch.commits || []).map(commit => {
                            const commitChangeNodes = (commit.changes || []).map(change =>
                                this.changeNode(change, commit.commitId)
                            );
                            return new GitButlerNode(
                                `${ButCommits.shortId(commit.commitId)} ${ButCommits.summary(commit.message)}`,
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
                        const branchStatus = ButBranch.statusSuffix(branch.branchStatus);
                        if (branchStatus) {
                            branchNode.description = branchStatus;
                        }
                        rootNodes.push(branchNode);
                    }
                }
            } else if (status.branches && status.branches.length > 0) {
                for (const branch of status.branches) {
                    const commitNodes = (branch.commits || []).map(commit => {
                        const commitChangeNodes = (commit.changes || []).map(change =>
                            this.changeNode(change, commit.commitId)
                        );
                        return new GitButlerNode(
                            `${ButCommits.shortId(commit.commitId)} ${ButCommits.summary(commit.message)}`,
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
                    const branchStatus = ButBranch.statusSuffix(branch.branchStatus);
                    if (branchStatus) {
                        branchNode.description = branchStatus;
                    }
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

/**
 * Drag-and-drop for the workspace tree, mirroring the JetBrains plugin: drag uncommitted
 * change rows onto a branch to commit them there, or onto a commit to amend them into it.
 */
class GitButlerDragAndDropController implements vscode.TreeDragAndDropController<GitButlerNode> {
    readonly dragMimeTypes = ['application/vnd.gitbutler.change-paths'];
    readonly dropMimeTypes = ['application/vnd.gitbutler.change-paths'];

    constructor(private readonly provider: GitButlerTreeDataProvider) {}

    handleDrag(source: readonly GitButlerNode[], dataTransfer: vscode.DataTransfer): void {
        const paths = source
            .filter(n => n.nodeType === 'change' && n.changeFilePath)
            .map(n => n.changeFilePath as string);
        if (paths.length > 0) {
            dataTransfer.set('application/vnd.gitbutler.change-paths', new vscode.DataTransferItem(JSON.stringify(paths)));
        }
    }

    async handleDrop(target: GitButlerNode | undefined, dataTransfer: vscode.DataTransfer): Promise<void> {
        const item = dataTransfer.get('application/vnd.gitbutler.change-paths');
        if (!item || !target) {
            return;
        }
        let paths: string[] = [];
        try {
            paths = JSON.parse(await item.asString());
        } catch {
            return;
        }
        if (paths.length === 0) {
            return;
        }

        const core = this.provider.getCore();
        const workspacePath = this.provider.getWorkspacePath();
        if (!core || !workspacePath) {
            vscode.window.showErrorMessage("GitButler core not initialized");
            return;
        }
        const absolute = paths.map(p => path.join(workspacePath, p));

        try {
            if (target.nodeType === 'branch' && target.branchName) {
                const message = await vscode.window.showInputBox({
                    prompt: `Commit ${paths.length} file(s) to '${target.branchName}'`,
                    placeHolder: "commit message"
                });
                if (!message) {
                    return;
                }
                callCore<string>(await core.commit(target.branchName, message, absolute));
                vscode.window.showInformationMessage(`GitButler: Committed ${paths.length} file(s) to '${target.branchName}'`);
                this.provider.refresh();
            } else if (target.nodeType === 'commit' && target.commitId) {
                // Tree nodes carry the git SHA; `but amend -t` needs the GitButler change id (cliId).
                const status = callCore<WorkspaceStatus>(await core.statusJson());
                const targetCommit = (status.branches || [])
                    .flatMap(b => b.commits || [])
                    .find(c => c.commitId === target.commitId);
                if (!targetCommit) {
                    vscode.window.showErrorMessage("Could not resolve the target commit");
                    return;
                }
                const short = ButCommits.shortId(target.commitId);
                const confirm = await vscode.window.showWarningMessage(
                    `Amend ${paths.length} file(s) into commit ${short}?`,
                    { modal: true },
                    "Amend"
                );
                if (confirm !== "Amend") {
                    return;
                }
                callCore<void>(await core.amend(targetCommit.cliId, absolute));
                vscode.window.showInformationMessage(`GitButler: Amended ${paths.length} file(s) into ${short}`);
                this.provider.refresh();
            }
        } catch {
            // Surfaced by callCore
        }
    }
}

/** Quick-pick entry that asks for a brand-new branch name instead of picking an existing one. */
export const NEW_BRANCH_PICK = '$(add) New branch\u2026';

/**
 * Input box for a new virtual-branch name, validated as you type against the same git
 * ref-name rules the CLI enforces (`ButBranch.newBranchNameError`), so a name `but` would
 * reject is refused here instead of coming back as a CLI error.
 */
export async function promptForNewBranchName(prompt: string): Promise<string | undefined> {
    const name = await vscode.window.showInputBox({
        prompt,
        placeHolder: 'my-feature',
        validateInput: value => {
            // An untouched, empty box shouldn't open already showing an error; an empty
            // value is treated as a cancel below.
            const trimmed = value.trim();
            return trimmed.length === 0 ? null : ButBranch.newBranchNameError(trimmed);
        }
    });
    const trimmed = name?.trim();
    return trimmed ? trimmed : undefined;
}

/**
 * Quick-pick over the applied virtual branches plus a "New branch..." entry. Picking that
 * asks for a name and returns it WITHOUT creating anything: `but commit -b <name>` creates
 * the branch as part of the commit, so cancelling the commit leaves the workspace untouched.
 */
export async function pickTargetBranch(status: WorkspaceStatus, placeHolder: string): Promise<string | undefined> {
    const items: vscode.QuickPickItem[] = (status.branches || []).map(b => ({
        label: b.name,
        description: ButBranch.statusSuffix(b.branchStatus) || undefined
    }));
    items.push({ label: NEW_BRANCH_PICK, description: 'created by the commit itself (but commit -b)' });

    const picked = await vscode.window.showQuickPick(items, { placeHolder });
    if (!picked) {
        return undefined;
    }
    if (picked.label === NEW_BRANCH_PICK) {
        return promptForNewBranchName('Name for the new virtual branch (created when you commit)');
    }
    return picked.label;
}

async function promptForCommit(status: WorkspaceStatus): Promise<{ branch: string; message: string } | undefined> {
    const branch = await pickTargetBranch(status, "Select target branch to commit to");
    if (!branch) {
        return undefined;
    }
    const message = await vscode.window.showInputBox({ prompt: "Enter commit message", placeHolder: "commit message" });
    if (!message) {
        return undefined;
    }
    return { branch, message };
}

interface GitApiChange {
    uri: vscode.Uri;
}

interface GitApiRepository {
    inputBox: { value: string };
    state: { indexChanges: GitApiChange[]; workingTreeChanges: GitApiChange[] };
}

interface GitApi {
    repositories: GitApiRepository[];
}

interface GitExtensionApi {
    getAPI(version: number): GitApi;
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

    const treeView = vscode.window.createTreeView('gitbutlerWorkspace', {
        treeDataProvider,
        canSelectMany: true,
        dragAndDropController: new GitButlerDragAndDropController(treeDataProvider),
    });
    context.subscriptions.push(treeView);

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

    // The branch a tree selection belongs to: branch and commit rows both carry branchName,
    // so an action invoked from the view title bar can still target what the user selected.
    const selectedBranchName = (): string | undefined =>
        treeView.selection.find(n => n.branchName)?.branchName;

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.push', async (node?: GitButlerNode) => {
            try {
                const currentCore = treeDataProvider.getCore();
                if (!currentCore) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }

                let targetBranch = node?.branchName ?? selectedBranchName();
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

                const status = callCore<WorkspaceStatus>(await currentCore.statusJson());
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

                const commit = await promptForCommit(status);
                if (!commit) {
                    return;
                }

                const absoluteFilePaths = selectedFilePaths.map(fp => path.join(workspacePath, fp));
                const commitId = callCore<string>(await currentCore.commit(commit.branch, commit.message, absoluteFilePaths));
                vscode.window.showInformationMessage(`GitButler: Committed ${selectedFilePaths.length} file(s)${commitId ? ` (ID: ${commitId})` : ''}`);
                treeDataProvider.refresh();
            } catch {
                // Surfaced by callCore or caught
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.newBranch', async () => {
            try {
                const currentCore = treeDataProvider.getCore();
                if (!currentCore) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }
                const name = await promptForNewBranchName('Name for the new virtual branch');
                if (!name) {
                    return;
                }
                callCore<void>(await currentCore.newBranch(name));
                vscode.window.showInformationMessage(`GitButler: Created virtual branch '${name}'`);
                treeDataProvider.refresh();
            } catch {
                // Surfaced by callCore
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.commitToBranch', async (node?: GitButlerNode) => {
            try {
                const currentCore = treeDataProvider.getCore();
                const workspacePath = treeDataProvider.getWorkspacePath();
                if (!currentCore || !workspacePath) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }

                // Unlike gitbutler.commit, the branch is NOT asked for: it comes from the row
                // the action was invoked on (or the selected one), which is the whole point.
                const targetBranch = node?.branchName ?? selectedBranchName();
                if (!targetBranch) {
                    vscode.window.showWarningMessage("Select a branch in the GitButler view first.");
                    return;
                }

                const status = callCore<WorkspaceStatus>(await currentCore.statusJson());
                const uncommitted = (status.uncommittedChanges || []).map(c => c.filePath);
                if (uncommitted.length === 0) {
                    vscode.window.showWarningMessage("No uncommitted changes available to commit");
                    return;
                }

                // Everything is pre-picked, so committing all of it is just Enter.
                const picked = await vscode.window.showQuickPick(
                    uncommitted.map(filePath => ({ label: filePath, picked: true })),
                    { canPickMany: true, placeHolder: `Select files to commit to '${targetBranch}'` }
                );
                if (!picked || picked.length === 0) {
                    return;
                }

                const message = await vscode.window.showInputBox({
                    prompt: `Commit ${picked.length} file(s) to '${targetBranch}'`,
                    placeHolder: "commit message"
                });
                if (!message) {
                    return;
                }

                const absolute = picked.map(item => path.join(workspacePath, item.label));
                const commitId = callCore<string>(await currentCore.commit(targetBranch, message, absolute));
                vscode.window.showInformationMessage(
                    `GitButler: Committed ${picked.length} file(s) to '${targetBranch}'${commitId ? ` (ID: ${commitId})` : ''}`
                );
                treeDataProvider.refresh();
            } catch {
                // Surfaced by callCore
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.commitSelected', async () => {
            try {
                const currentCore = treeDataProvider.getCore();
                const workspacePath = treeDataProvider.getWorkspacePath();
                if (!currentCore || !workspacePath) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }

                const selectedPaths = treeView.selection
                    .filter(n => n.nodeType === 'change' && n.changeFilePath)
                    .map(n => n.changeFilePath as string);
                if (selectedPaths.length === 0) {
                    vscode.window.showWarningMessage("Select one or more changed files in the GitButler view first.");
                    return;
                }

                const status = callCore<WorkspaceStatus>(await currentCore.statusJson());
                const commit = await promptForCommit(status);
                if (!commit) {
                    return;
                }

                const absoluteFilePaths = selectedPaths.map(fp => path.join(workspacePath, fp));
                const commitId = callCore<string>(await currentCore.commit(commit.branch, commit.message, absoluteFilePaths));
                vscode.window.showInformationMessage(`GitButler: Committed ${selectedPaths.length} file(s)${commitId ? ` (ID: ${commitId})` : ''}`);
                treeDataProvider.refresh();
            } catch {
                // Surfaced by callCore or caught
            }
        })
    );
    const allCommits = (s: WorkspaceStatus): Commit[] => [
        ...(s.branches || []).flatMap((b: Branch) => b.commits || []),
        ...(s.stacks || []).flatMap((st: Stack) => (st.branches || []).flatMap((b: Branch) => b.commits || []))
    ];

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.reword', async (node?: GitButlerNode) => {
            try {
                const currentCore = treeDataProvider.getCore();
                if (!currentCore) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }
                if (!node?.commitId) {
                    vscode.window.showErrorMessage("No commit selected");
                    return;
                }
                const status = callCore<WorkspaceStatus>(await currentCore.statusJson());
                const commit = allCommits(status).find(c => c.commitId === node.commitId);
                if (!commit) {
                    vscode.window.showErrorMessage("Could not resolve target commit");
                    return;
                }
                const id = ButCommits.effectiveId(commit.cliId, commit.commitId);
                const message = await vscode.window.showInputBox({
                    prompt: "New commit message",
                    value: commit.message
                });
                if (!message || message === commit.message) {
                    return;
                }
                callCore<string>(await currentCore.reword(id, message));
                vscode.window.showInformationMessage("GitButler: Renamed commit");
                treeDataProvider.refresh();
            } catch {}
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.uncommit', async (node?: GitButlerNode) => {
            try {
                const currentCore = treeDataProvider.getCore();
                if (!currentCore) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }
                if (!node?.commitId) {
                    vscode.window.showErrorMessage("No commit selected");
                    return;
                }
                const status = callCore<WorkspaceStatus>(await currentCore.statusJson());
                const commit = allCommits(status).find(c => c.commitId === node.commitId);
                if (!commit) {
                    vscode.window.showErrorMessage("Could not resolve target commit");
                    return;
                }
                const id = ButCommits.effectiveId(commit.cliId, commit.commitId);
                const confirm = await vscode.window.showWarningMessage(
                    `Uncommit ${ButCommits.shortId(commit.commitId)}? Its changes return to your working tree.`,
                    { modal: true },
                    'Uncommit'
                );
                if (confirm !== 'Uncommit') {
                    return;
                }
                callCore<string>(await currentCore.uncommit(id));
                vscode.window.showInformationMessage(`GitButler: Uncommitted ${ButCommits.shortId(commit.commitId)}`);
                treeDataProvider.refresh();
            } catch {}
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.uncommitFile', async (node?: GitButlerNode) => {
            try {
                const currentCore = treeDataProvider.getCore();
                if (!currentCore) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }
                if (!node?.commitId || !node.changeFilePath) {
                    vscode.window.showErrorMessage("No committed file selected");
                    return;
                }
                const status = callCore<WorkspaceStatus>(await currentCore.statusJson());
                const commit = allCommits(status).find(c => c.commitId === node.commitId);
                if (!commit) {
                    vscode.window.showErrorMessage("Could not resolve target commit");
                    return;
                }
                const fileChange = (commit.changes || []).find(ch => ch.filePath === node.changeFilePath);
                if (!fileChange || !fileChange.cliId) {
                    vscode.window.showErrorMessage("Could not resolve file change");
                    return;
                }
                callCore<string>(await currentCore.uncommit(fileChange.cliId));
                vscode.window.showInformationMessage(`GitButler: Uncommitted ${node.changeFilePath}`);
                treeDataProvider.refresh();
            } catch {}
        })
    );
    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.openDiff', async (filePath?: string, parentCommitId?: string) => {
            if (!filePath) {
                return;
            }
            const uri = vscode.Uri.file(filePath);
            if (!parentCommitId) {
                await vscode.commands.executeCommand('git.openChange', uri);
                return;
            }
            const ext = vscode.extensions.getExtension<{ getAPI(version: number): { toGitUri(uri: vscode.Uri, ref: string): vscode.Uri } }>('vscode.git');
            if (ext && !ext.isActive) {
                await ext.activate();
            }
            const api = ext?.exports?.getAPI(1);
            if (api?.toGitUri) {
                const left = api.toGitUri(uri, `${parentCommitId}^`);
                const right = api.toGitUri(uri, parentCommitId);
                await vscode.commands.executeCommand('vscode.diff', left, right, `${path.basename(filePath)} (${parentCommitId.slice(0, 7)})`);
            } else {
                await vscode.commands.executeCommand('vscode.open', uri);
            }
        })
    );

    const selectedBranchKey = 'gitbutler.selectedVirtualBranch';

    const scmStatusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
    scmStatusBar.command = 'gitbutler.selectVirtualBranch';
    context.subscriptions.push(scmStatusBar);

    const updateScmStatusBar = () => {
        const selected = context.workspaceState.get<string | null>(selectedBranchKey, null);
        scmStatusBar.text = selected ? `$(git-branch) GitButler: ${selected}` : '$(git-branch) Git: no virtual branch';
        scmStatusBar.tooltip = selected
            ? `Source Control commits route to virtual branch '${selected}'. Click to change.`
            : 'Source Control commits use plain git. Click to route them to a virtual branch.';
        scmStatusBar.show();
    };
    updateScmStatusBar();

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.selectVirtualBranch', async () => {
            try {
                const currentCore = treeDataProvider.getCore();
                if (!currentCore) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }
                const status = callCore<WorkspaceStatus>(await currentCore.statusJson());
                const noBranch = 'Git: no virtual branch';
                const items: vscode.QuickPickItem[] = [
                    { label: noBranch, description: 'commit with plain git' },
                    ...(status.branches || []).map(b => ({
                        label: b.name,
                        description: ButBranch.statusSuffix(b.branchStatus) || undefined
                    })),
                    { label: NEW_BRANCH_PICK, description: 'created by the commit itself (but commit -b)' }
                ];
                const picked = await vscode.window.showQuickPick(items, {
                    placeHolder: 'Route Source Control commits to a virtual branch'
                });
                if (picked === undefined) {
                    return;
                }
                // A name typed through "New branch..." is only remembered here; the branch is
                // created by the commit, so nothing exists until the user commits.
                let selection: string | null;
                if (picked.label === noBranch) {
                    selection = null;
                } else if (picked.label === NEW_BRANCH_PICK) {
                    const name = await promptForNewBranchName('Name for the new virtual branch (created when you commit)');
                    if (!name) {
                        return;
                    }
                    selection = name;
                } else {
                    selection = picked.label;
                }
                await context.workspaceState.update(selectedBranchKey, selection);
                updateScmStatusBar();
            } catch {
                // Surfaced by callCore
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('gitbutler.commitToVirtualBranch', async () => {
            const selected = context.workspaceState.get<string | null>(selectedBranchKey, null);
            if (!selected) {
                await vscode.commands.executeCommand('git.commit');
                return;
            }
            try {
                const currentCore = treeDataProvider.getCore();
                const workspacePath = treeDataProvider.getWorkspacePath();
                if (!currentCore || !workspacePath) {
                    vscode.window.showErrorMessage("GitButler core not initialized");
                    return;
                }
                const gitExtension = vscode.extensions.getExtension<GitExtensionApi>('vscode.git');
                if (gitExtension && !gitExtension.isActive) {
                    await gitExtension.activate();
                }
                const repository = gitExtension?.exports?.getAPI(1)?.repositories?.[0];
                if (!repository) {
                    vscode.window.showErrorMessage("No Git repository in the Source Control view");
                    return;
                }
                const message = (repository.inputBox.value || '').trim();
                if (!message) {
                    vscode.window.showWarningMessage("Enter a commit message in the Source Control input first.");
                    return;
                }
                const staged = repository.state.indexChanges || [];
                const working = repository.state.workingTreeChanges || [];
                const changes = staged.length > 0 ? staged : working;
                const filePaths = changes.map(c => c.uri.fsPath);
                if (filePaths.length === 0) {
                    vscode.window.showWarningMessage("No changes to commit.");
                    return;
                }
                callCore<string>(await currentCore.commit(selected, message, filePaths));
                repository.inputBox.value = '';
                vscode.window.showInformationMessage(`GitButler: Committed ${filePaths.length} file(s) to '${selected}'`);
                treeDataProvider.refresh();
            } catch {
                // Surfaced by callCore
            }
        })
    );
}

export function deactivate() {}
