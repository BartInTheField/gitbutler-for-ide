package me.inthefield.gitbutlerforjetbrains.setup

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

/**
 * Confirmation for `but teardown`, with an optional branch to pass as `--checkout-to`.
 * OK returns the chosen branch, or null when the user left the GitButler default
 * ("first applied branch") selected. Cancel returns no result — the caller checks
 * [DialogWrapper.showAndGet].
 */
class GitButlerLeaveDialog(
    project: Project,
    candidates: List<String>,
    private val allowDefault: Boolean,
) : DialogWrapper(project) {

    private val defaultChoice = Any()
    private val model = DefaultComboBoxModel<Any>()
    private val combo = ComboBox(model)

    init {
        title = "Leave GitButler Mode"
        setOKButtonText("Leave GitButler Mode")
        if (allowDefault) {
            model.addElement(defaultChoice)
        }
        candidates.forEach { model.addElement(it) }
        combo.isEditable = true
        combo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val label = if (value === defaultChoice) "First applied branch" else value?.toString().orEmpty()
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus)
            }
        }
        when {
            candidates.isNotEmpty() -> combo.selectedItem = candidates.first()
            allowDefault -> combo.selectedItem = defaultChoice
        }
        init()
    }

    /** Branch for `--checkout-to`, or null to let `but teardown` pick the first applied branch. */
    fun checkoutTo(): String? {
        val item = combo.selectedItem
        if (item === defaultChoice || item == null) return null
        return item.toString().trim().ifEmpty { null }
    }

    override fun doOKAction() {
        if (!allowDefault && checkoutTo() == null) {
            setErrorText("Choose a local branch to check out. GitButler needs one when no branch is applied.")
            return
        }
        super.doOKAction()
    }

    override fun createCenterPanel(): JComponent {
        val explanation = JBLabel(
            """
            <html><body style='width: 440px'>
            This runs <code>but teardown</code> and changes the checked-out branch.<br><br>
            GitButler will snapshot the workspace, check out a normal Git branch,
            and cherry-pick any commits made directly on <code>gitbutler/workspace</code>.
            The IDE stops managing this repository until you set it up again
            (Git → GitButler → Set Up GitButler for This Project).
            </body></html>
            """.trimIndent(),
        )
        val pickerLabel = JBLabel("Branch to check out:")
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        val top = JPanel(BorderLayout(0, JBUI.scale(12)))
        top.add(explanation, BorderLayout.NORTH)
        val picker = JPanel(BorderLayout(0, JBUI.scale(4)))
        picker.add(pickerLabel, BorderLayout.NORTH)
        picker.add(combo, BorderLayout.CENTER)
        top.add(picker, BorderLayout.SOUTH)
        panel.add(top, BorderLayout.NORTH)
        panel.border = JBUI.Borders.empty(4, 8)
        return panel
    }
}
