package com.github.dvranes1.deploymentplugin


import com.intellij.ui.IconManager
import javax.swing.Icon

object Icons {
    @JvmField
    val DeployToolWindow: Icon =
        IconManager.getInstance().getIcon("/icons/deployIcon.png", Icons::class.java.classLoader)
}
