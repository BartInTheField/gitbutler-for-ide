import { _electron as electron, type ElectronApplication, type Page } from 'playwright-core';
import { execFileSync } from 'child_process';
import * as assert from 'assert';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

/**
 * UI end-to-end smoke against the Cursor editor (a VS Code fork) instead of VSCodium.
 * Cursor ships a standard Electron binary, so Playwright's Electron API attaches to it via
 * the built-in CDP exactly like the VSCodium harness. Launches Cursor with this extension
 * loaded from source, opens the GitButler view, and asserts the tree actually resolves
 * (renders a GitButler-provider row) rather than spinning forever.
 *
 * No screenshot here on purpose: Cursor gates its workbench behind an account login, so a
 * fresh profile renders the "log in to Cursor" welcome over the editor and the classic
 * sidebar stays collapsed. The view's tree data still resolves in the extension host, which
 * is what this smoke verifies. The visible, screenshotted UI lives in the VSCodium smoke
 * (`codium-smoke.ts`), which needs no login.
 *
 * Cursor gotchas handled here vs the plain VSCodium harness:
 *   - default binary path is the macOS app bundle, with CURSOR_BIN override;
 *   - the classic activity bar is gone (Cursor's unified sidebar replaces it), so the view
 *     is opened via the auto-generated "GitButler: Focus on Workspace View" command;
 *   - `--disable-updates` so Cursor's auto-updater never steals focus mid-run.
 *
 *   CURSOR_BIN=/Applications/Cursor.app/Contents/MacOS/Cursor node out-ui/ui/cursor-smoke.js
 */
function defaultCursorBin(): string {
  if (process.platform === 'darwin') return '/Applications/Cursor.app/Contents/MacOS/Cursor';
  if (process.platform === 'win32') {
    return path.join(os.homedir(), 'AppData', 'Local', 'Programs', 'cursor', 'Cursor.exe');
  }
  return '/usr/bin/cursor';
}

function seedWorkspace(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gb-cursor-'));
  const git = (...a: string[]) => execFileSync('git', a, { cwd: dir, stdio: 'ignore' });
  git('init', '-q', '-b', 'main', '.');
  git('config', 'user.email', 'ui@example.com');
  git('config', 'user.name', 'ui');
  fs.writeFileSync(path.join(dir, 'a.txt'), 'hello\n');
  git('add', '-A');
  git('commit', '-qm', 'init');
  let hasBut = true;
  try {
    execFileSync('but', ['--version'], { stdio: 'ignore' });
  } catch {
    hasBut = false;
  }
  if (hasBut) {
    execFileSync('but', ['setup', '--init'], { cwd: dir, stdio: 'ignore' });
    fs.appendFileSync(path.join(dir, 'a.txt'), 'a working-tree change\n');
  }
  return dir;
}

async function readTree(win: Page): Promise<{ rows: string[]; loading: boolean }> {
  const rows = (
    await win.locator('.pane-body .monaco-list-row, .part.sidebar .monaco-list-row').allInnerTexts().catch(() => [])
  )
    .map((r) => r.replace(/\s+/g, ' ').trim())
    .filter(Boolean);
  const loading = await win
    .locator('.monaco-progress-container.active')
    .first()
    .isVisible()
    .catch(() => false);
  return { rows, loading };
}

async function main(): Promise<void> {
  const cursor = process.env.CURSOR_BIN || defaultCursorBin();
  if (!fs.existsSync(cursor)) {
    throw new Error(`Cursor binary not found at ${cursor}. Set CURSOR_BIN to its path.`);
  }
  const extensionRoot = path.resolve(__dirname, '../..'); // -> vscode/
  const workspace = seedWorkspace();
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'gb-cursor-user-'));
  const extensionsDir = fs.mkdtempSync(path.join(os.tmpdir(), 'gb-cursor-exts-'));

  const app: ElectronApplication = await electron.launch({
    executablePath: cursor,
    args: [
      `--extensionDevelopmentPath=${extensionRoot}`,
      `--user-data-dir=${userDataDir}`,
      `--extensions-dir=${extensionsDir}`,
      '--disable-workspace-trust',
      '--skip-release-notes',
      '--skip-welcome',
      '--disable-telemetry',
      '--disable-updates',
      '--no-sandbox',
      '--disable-gpu',
      workspace,
    ],
  });

  const consoleLines: string[] = [];

  try {
    const win: Page = await app.firstWindow();
    win.on('console', (m) => consoleLines.push(`[${m.type()}] ${m.text()}`));
    win.on('pageerror', (e) => consoleLines.push(`[pageerror] ${e.message}`));
    await win.waitForSelector('.monaco-workbench', { timeout: 60_000 });

    // Cursor's default layout replaces the classic activity bar with its unified agent
    // sidebar, so the codium activity-bar click can't work here. Open the view through the
    // auto-generated focus command instead — layout-independent and identical across forks.
    // A startup modal (Cursor's login prompt) can eat the first keystrokes, so retry the
    // focus command until the primary sidebar shows and the tree resolves.
    const openView = async (page: Page): Promise<void> => {
      await page.keyboard.press('Escape');
      await page.keyboard.press('F1');
      await page.waitForTimeout(700);
      await page.keyboard.type('GitButler: Focus on Workspace View');
      await page.waitForTimeout(900);
      await page.keyboard.press('Enter');
      await page.waitForTimeout(1_200);
    };

    // Success = our provider rendered a row of its own AND stopped spinning. Match only
    // strings this extension emits so a visible Explorer (or any other view) can never pass
    // the smoke. An endless spinner with no such row is the loading-state bug this reproduces.
    const isGbRow = (r: string): boolean =>
      /^(Unassigned changes|Not a GitButler workspace|Error loading status)/i.test(r);
    const deadline = Date.now() + 60_000;
    let view: Page = win;
    let tree = { rows: [] as string[], loading: false };
    while (Date.now() < deadline) {
      view = app.windows()[app.windows().length - 1];
      tree = await readTree(view);
      if (tree.rows.some(isGbRow) && !tree.loading) break;
      await openView(view);
      tree = await readTree(view);
      if (tree.rows.some(isGbRow) && !tree.loading) break;
      await view.waitForTimeout(1_500);
    }

    const sidebarText = (await view.locator('[id="workbench.parts.sidebar"]').innerText().catch(() => '')).replace(/\s+/g, ' ').trim();
    console.log('product:', await app.evaluate(async ({ app: a }) => a.getName()));
    console.log('tree rows:', JSON.stringify(tree.rows));
    console.log('still loading:', tree.loading);
    console.log('sidebar text:', sidebarText.slice(0, 200));
    if (consoleLines.length) {
      console.log('--- renderer console (last 40) ---');
      console.log(consoleLines.slice(-40).join('\n'));
    }

    // Definitive proof the GitButler provider ran in Cursor's extension host: it rendered a
    // row only this extension emits, and the tree left its loading state. No screenshot —
    // Cursor's login gate hides the workbench (see file header).
    assert.ok(!tree.loading, 'GitButler tree is stuck in its loading state (endless spinner)');
    assert.ok(tree.rows.some(isGbRow), `GitButler view never rendered a provider row; rows: ${JSON.stringify(tree.rows)}`);
    console.log('CURSOR UI SMOKE OK');
  } finally {
    await app.close();
    fs.rmSync(workspace, { recursive: true, force: true });
    fs.rmSync(userDataDir, { recursive: true, force: true });
    fs.rmSync(extensionsDir, { recursive: true, force: true });
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
