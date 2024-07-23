package com.codertainment.scrcpy.controller.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory

/*
 * Created by Shripal Jain
 * on 12/06/2020
 */

class ScrcpyToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val controller = ScrcpyController(toolWindow)

    val contentFactory = ContentFactory.getInstance()
    val content: Content = contentFactory.createContent(controller.mainPanel, "", false)
    toolWindow.contentManager.addContent(content)
  }
}