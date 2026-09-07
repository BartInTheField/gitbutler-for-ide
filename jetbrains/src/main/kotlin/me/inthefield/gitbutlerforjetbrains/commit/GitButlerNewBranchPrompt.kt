package me.inthefield.gitbutlerforjetbrains.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import me.inthefield.gitbutlerforjetbrains.core.ButBranch

/**
 * Modal prompt for a new virtual-branch name, shared by the commit-toolbar combo
 * ([GitButlerBranchComboAction]) and the tool window's "New Virtual Branch" action.
 *
 * The input is validated live against [ButBranch.newBranchNameError], so a name git would
 * reject is refused in the dialog instead of coming back as a CLI error afterwards.
 */
object GitButlerNewBranchPrompt {

    /** Returns the trimmed branch name, or null if the user cancelled. */
    fun ask(project: Project, title: String, message: String): String? =
        Messages.showInputDialog(project, message, title, null, "", BranchNameValidator)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private object BranchNameValidator : InputValidatorEx {
        // Blank input yields no error text — an empty field the user hasn't typed in yet
        // shouldn't open the dialog already showing red — but checkInput still rejects it,
        // so OK stays disabled until there is a usable name.
        override fun getErrorText(inputString: String): String? =
            if (inputString.isBlank()) null else ButBranch.newBranchNameError(inputString.trim())

        override fun checkInput(inputString: String): Boolean =
            inputString.isNotBlank() && getErrorText(inputString) == null

        override fun canClose(inputString: String): Boolean = checkInput(inputString)
    }
}
