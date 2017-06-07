package ktindex;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.Prefs;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.PositionPanel;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class KtIndexWidget extends JLabel implements ProjectComponent, CustomStatusBarWidget, StatusBarWidget, DumbAware, Border {

  private static final String KT_TIOBE_WHAT = "kt.tiobe.what";
  private static final String KT_TIOBE_WHEN = "kt.tiobe.when";
  private static final char ARROW = '\u2192';
  private final Project myProject;
  private Runnable myParser;

  public KtIndexWidget(Project project) {
    super("   ", EmptyIcon.ICON_16, LEADING);
    myProject = project;
  }

  @Override
  public void projectOpened() {
    WindowManager.getInstance().getStatusBar(myProject).addWidget(this, "before " + PositionPanel.ID);
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    revalidate();
    repaint();
  }

  @Override
  public void initComponent() {
    myParser = () -> {
      try {
        final String text = initText();

        SwingUtilities.invokeLater(() -> {
          setIcon(IconLoader.getIcon("K.png"));
          setFont(SystemInfo.isMac ? JBUI.Fonts.smallFont() : JBUI.Fonts.miniFont());
          setIconTextGap(-getFontMetrics(getFont()).stringWidth("#") / 2 - 1);
          if (!getFont().canDisplay(ARROW)) {
            setText(text.replace("" + ARROW, "->") + "  ");
          } else {
            setText(text + "  ");
          }
          
          setBorder(this);
          revalidate();
          repaint();
        });
      } catch (IOException e) {
        JobScheduler.getScheduler().schedule(myParser, 1, TimeUnit.HOURS);
        return;
      }
      JobScheduler.getScheduler().schedule(myParser, 23, TimeUnit.HOURS);
    };
    JobScheduler.getScheduler().schedule(myParser, 1, TimeUnit.SECONDS);
  }

  @NotNull
  private String initText() throws IOException {
    int storedValue = 0;
    try {
      storedValue = Integer.parseInt(Prefs.get(KT_TIOBE_WHAT, "0"));
      long storedTime = Long.parseLong(Prefs.get(KT_TIOBE_WHEN, "0"));
      if (storedValue != 0 && storedTime != 0 && System.currentTimeMillis() - storedTime < 86400000) {
        return "#" + storedValue;
      }
    } catch (NumberFormatException e) {
      //ignore and parse
    }
    String s = HttpRequests.request("https://www.tiobe.com/tiobe-index/").readString(null);
    int pos = s.indexOf("<td>Kotlin</td>");
    if (pos == -1) throw new IOException();
    pos = s.lastIndexOf("<tr><td>", pos);
    if (pos == -1) throw new IOException();
    int pos2 = s.indexOf("<", pos + 8);
    if (pos2 == -1) throw new IOException();
    int value = Integer.parseInt(s.substring(pos + 8, pos2));

    Prefs.put(KT_TIOBE_WHAT, "" + value);
    Prefs.put(KT_TIOBE_WHEN, "" + System.currentTimeMillis());

    if (value != storedValue && storedValue != 0) {
      return "#" + storedValue + " " + ARROW + " #" + value;
    } else {
      return "#" + value;
    }
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @NotNull
  @Override
  public String ID() {
    return "TIOBE Index for Kotlin";
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType platformType) {
    return null;
  }

  @Override
  public void dispose() {
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.emptyInsets();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2d = (Graphics2D) g;
    g2d.setPaint(new GradientPaint(x, y, new Color(223, 110, 0), x, y + height - .5f, new Color(90, 73, 173)));
    GeneralPath path = new GeneralPath();
    double m = (height - .5) / 2d;
    path.moveTo(x + JBUI.scale(15), y);
    path.lineTo(x + width - 1 - m, y);
    path.lineTo(width - 1, y + m);
    path.lineTo(x + width - 1 - m, y + 2 * m);
    path.lineTo(x + JBUI.scale(15), y + 2 * m);
    GraphicsUtil.setupAAPainting(g2d);
    g2d.draw(path);
  }
}
