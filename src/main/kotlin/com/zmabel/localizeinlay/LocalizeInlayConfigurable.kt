package com.zmabel.localizeinlay

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextField
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.border.EmptyBorder
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JFileChooser

class LocalizeInlayConfigurable : Configurable {

    private var panel: JPanel? = null
    private var pathField: TextFieldWithBrowseButton? = null
    private var methodNamesField: JBTextField? = null

    override fun getDisplayName(): String = "Localize Argument Inlay"

    override fun createComponent(): JComponent {
        if (panel == null) {
            val root = JPanel(BorderLayout())
            root.border = EmptyBorder(10, 10, 10, 10)

            val form = JPanel(GridBagLayout())
            val c = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                weightx = 0.0
                gridx = 0
                gridy = 0
            }

            form.add(JLabel("混合文本表json文件路径："), c)

            c.gridx = 1
            c.weightx = 1.0
            pathField = TextFieldWithBrowseButton()
            val fileDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            fileDescriptor.title = "select file"
            fileDescriptor.description = "chose file"
            pathField?.addActionListener {
                val project = ProjectManager.getInstance().defaultProject
                com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                    fileDescriptor,
                    project,
                    null,
                ) { file: VirtualFile -> pathField?.text = file.path
                }
            }
            form.add(pathField, c)

            c.gridx = 0
            c.gridy = 1
            c.weightx = 0.0
            form.add(JLabel("方法名（用逗号分隔）："), c)

            c.gridx = 1
            c.weightx = 1.0
            methodNamesField = JBTextField()
            form.add(methodNamesField, c)

            root.add(form, BorderLayout.NORTH)
            panel = root

            reset()
        }
        return panel!!
    }

    override fun isModified(): Boolean {
        val currentPath = LocalizeInlaySettingsState.getInstance().jsonPath ?: ""
        val uiPath = pathField?.text ?: ""
        val currentMethodNames = LocalizeInlaySettingsState.getInstance().methodNames
        val uiMethodNames = methodNamesField?.text ?: ""
        return currentPath != uiPath || currentMethodNames != uiMethodNames
    }

    override fun apply() {
        LocalizeInlaySettingsState.getInstance().jsonPath = pathField?.text?.trim().takeUnless { it.isNullOrEmpty() }
        LocalizeInlaySettingsState.getInstance().methodNames = methodNamesField?.text?.trim().takeIf { it?.isNotEmpty() == true } ?: "LocalUtils.GetString"
    }

    override fun reset() {
        val savedPath = LocalizeInlaySettingsState.getInstance().jsonPath ?: DEFAULT_PATH
        pathField?.text = savedPath
        val savedMethodNames = LocalizeInlaySettingsState.getInstance().methodNames
        methodNamesField?.text = savedMethodNames
    }

    override fun disposeUIResources() {
        panel = null
        pathField = null
        methodNamesField = null
    }

    companion object {
        private const val DEFAULT_PATH = "ConfLocalize.json"
    }
}

