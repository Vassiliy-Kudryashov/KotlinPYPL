package ktindex

import com.intellij.concurrency.JobScheduler
import com.intellij.ide.Prefs
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.PositionPanel
import com.intellij.util.io.HttpRequests
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.GeneralPath
import java.awt.geom.Line2D
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.border.Border

class KtIndexWidget(private val myProject: Project) : JLabel("   ", EmptyIcon.ICON_16, SwingConstants.LEADING), ProjectComponent, CustomStatusBarWidget, StatusBarWidget, DumbAware, Border, Runnable {
    private val KT_TIOBE_WHAT = "kt.tiobe.what"
    private val KT_TIOBE_WHEN = "kt.tiobe.when"
    private val ARROW = '\u2192'

    override fun projectOpened() {
        WindowManager.getInstance().getStatusBar(myProject).addWidget(this, "before " + PositionPanel.ID)
    }

    override fun install(statusBar: StatusBar) {
        revalidate()
        repaint()
        ProjectManager.getInstance().addProjectManagerListener(myProject, object: ProjectManagerListener {
            override fun projectClosing(project: Project?) {
                WindowManager.getInstance().getStatusBar(myProject).removeWidget(ID())
            }
        })
    }

    override fun run() {
        try {
            val text = initText()

            SwingUtilities.invokeLater {
                icon = IconLoader.getIcon("K.png")
                font = if (SystemInfo.isMac) JBUI.Fonts.smallFont() else JBUI.Fonts.miniFont()
                iconTextGap = -getFontMetrics(font).stringWidth("#") / 2 - 1
                if (!font.canDisplay(ARROW)) {
                    setText(text.replace("" + ARROW, "->") + "  ")
                } else {
                    setText(text + "  ")
                }

                border = this
                revalidate()
                repaint()
            }
            JobScheduler.getScheduler().schedule(this, 23, TimeUnit.HOURS)
        } catch (e: IOException) {
            JobScheduler.getScheduler().schedule(this, 1, TimeUnit.HOURS)
        }
    }

    override fun initComponent() {
        JobScheduler.getScheduler().schedule(this, 1, TimeUnit.SECONDS)
    }

    @Throws(IOException::class)
    private fun initText(): String {
        var storedValue = 0
        try {
            storedValue = Integer.parseInt(Prefs.get(KT_TIOBE_WHAT, "0"))
            val storedTime = java.lang.Long.parseLong(Prefs.get(KT_TIOBE_WHEN, "0"))
            if (storedValue != 0 && storedTime != 0L && System.currentTimeMillis() - storedTime < 86400000) {
                return "#" + storedValue
            }
        } catch (e: NumberFormatException) {
            //ignore and parse
        }

        val s = HttpRequests.request("https://www.tiobe.com/tiobe-index/").readString(null)
        var pos = s.indexOf("<td>Kotlin</td>")
        if (pos == -1) throw IOException()
        pos = s.lastIndexOf("<tr><td>", pos)
        if (pos == -1) throw IOException()
        val pos2 = s.indexOf("<", pos + 8)
        if (pos2 == -1) throw IOException()
        try {
            val value = Integer.parseInt(s.substring(pos + 8, pos2))

            Prefs.put(KT_TIOBE_WHAT, "" + value)
            Prefs.put(KT_TIOBE_WHEN, "" + System.currentTimeMillis())

            if (value != storedValue && storedValue != 0) {
                return "#$storedValue $ARROW #$value"
            } else {
                return "#" + value
            }
        } catch (e: NumberFormatException) {
            throw IOException(e)
        }

    }

    override fun getComponent(): JComponent {
        return this
    }

    override fun ID(): String {
        return "TIOBE Index for Kotlin"
    }

    override fun getPresentation(platformType: StatusBarWidget.PlatformType): StatusBarWidget.WidgetPresentation? {
        return null
    }

    override fun dispose() {}

    override fun getBorderInsets(c: Component): Insets {
        return JBUI.emptyInsets()
    }

    override fun isBorderOpaque(): Boolean {
        return false
    }

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val m = (height - .5f) / 2
        val g2d = g as Graphics2D
        GraphicsUtil.setupAAPainting(g2d)
        g2d.paint = GradientPaint(
                (x + JBUI.scale(7)).toFloat(), y.toFloat(), Color(223, 110, 0, 0),
                (x + JBUI.scale(14)).toFloat(), y.toFloat(), Color(223, 110, 0))
        g2d.draw(Line2D.Float((x + JBUI.scale(7)).toFloat(), y.toFloat(), (x + JBUI.scale(14)).toFloat(), y.toFloat()))
        g2d.paint = GradientPaint(
                (x + JBUI.scale(7)).toFloat(), y + 2f * m, Color(90, 73, 173, 0),
                (x + JBUI.scale(14)).toFloat(), y + 2f * m, Color(90, 73, 173))
        g2d.draw(Line2D.Float((x + JBUI.scale(7)).toFloat(), y + 2 * m, (x + JBUI.scale(14)).toFloat(), y + 2 * m))

        g2d.paint = GradientPaint(
                x.toFloat(), y.toFloat(), Color(223, 110, 0),
                x.toFloat(), y + height - .5f, Color(90, 73, 173))
        val path = GeneralPath()
        path.moveTo((x + JBUI.scale(14)).toFloat(), y.toFloat())
        path.lineTo((x + width - 1).toFloat() - m, y.toFloat())
        path.lineTo((x + width - 1).toFloat(), y + m)
        path.lineTo((x + width - 1).toFloat() - m, y + 2 * m)
        path.lineTo((x + JBUI.scale(14)).toFloat(), y + 2 * m)
        g2d.draw(path)
    }

    override fun getComponentName(): String {
        return "Kotlin TIOBE Index Widget"
    }

    override fun disposeComponent() {
    }

    override fun projectClosed() {
    }
}
