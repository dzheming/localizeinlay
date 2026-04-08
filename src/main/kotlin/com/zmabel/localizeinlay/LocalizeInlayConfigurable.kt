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

            root.add(form, BorderLayout.NORTH)
            panel = root

            reset()
        }
        return panel!!
    }

    override fun isModified(): Boolean {
        val current = LocalizeInlaySettingsState.getInstance().jsonPath ?: ""
        val ui = pathField?.text ?: ""
        return current != ui
    }

    override fun apply() {
        LocalizeInlaySettingsState.getInstance().jsonPath = pathField?.text?.trim().takeUnless { it.isNullOrEmpty() }
    }

    override fun reset() {
        val saved = LocalizeInlaySettingsState.getInstance().jsonPath ?: DEFAULT_PATH
        pathField?.text = saved
    }

    override fun disposeUIResources() {
        panel = null
        pathField = null
    }

    companion object {
        private const val DEFAULT_PATH = "D:\\zxhj\\zx-design\\4configs\\Config\\gen\\export\\sourcejson\\1_混合文本总表#文本#ConfLocalize.json"
    }
}

