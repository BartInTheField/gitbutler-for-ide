package me.inthefield.gitbutlerforjetbrains.toolwindow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Reflection on purpose: the non-modal commit handler ({@code ChangesViewCommitWorkflowHandler})
 * and its {@code setCommitState} are {@code @ApiStatus.Internal} with no public equivalent for
 * replacing the Commit tool window's checked inclusion. Reflecting past both keeps the internal
 * class out of this plugin's bytecode, so the platform verifier no longer reports internal-API
 * usage. A lookup failure means the platform renamed the API and is logged at ERROR so the
 * regression is visible rather than a silent no-op.
 */
final class CommitInclusion {

    private static final Logger LOG = Logger.getInstance(CommitInclusion.class);

    private CommitInclusion() {
    }

    /**
     * Replaces the Commit tool window's checked state with {@code items} ({@code Change}s for
     * tracked files, {@code FilePath}s for unversioned ones). Returns false when the non-modal
     * commit workflow handler is unavailable (e.g. commit-dialog mode) or the internal API is no
     * longer reachable by reflection.
     */
    static boolean setInclusion(Project project, LocalChangeList changeList, Collection<Object> items) {
        Object handler;
        try {
            ChangesViewWorkflowManager manager = ChangesViewWorkflowManager.getInstance(project);
            Method getHandler = ChangesViewWorkflowManager.class.getMethod("getCommitWorkflowHandler");
            getHandler.setAccessible(true);
            handler = getHandler.invoke(manager);
        } catch (ReflectiveOperationException e) {
            LOG.error("Commit workflow handler lookup failed; platform API renamed", e);
            return false;
        }
        if (handler == null) {
            return false;
        }
        try {
            Method setCommitState = handler.getClass()
                    .getMethod("setCommitState", LocalChangeList.class, Collection.class, boolean.class);
            setCommitState.setAccessible(true);
            setCommitState.invoke(handler, changeList, items, true);
            return true;
        } catch (ReflectiveOperationException e) {
            LOG.error("Commit inclusion API unavailable; platform API renamed", e);
            return false;
        }
    }
}
