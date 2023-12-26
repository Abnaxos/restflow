package net.netconomy.tools.restflow.integrations.idea;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MyNotifications extends DialogWrapper {

    private final String message;
    private final String report;

    private MyNotifications(@Nullable Project project, String message, @Nullable Throwable exception) {
        super(project, false, IdeModalityType.MODELESS);
        setTitle("RESTflow Diagnostics");
        this.message = message;
        this.report = createReport(exception);
        init();
    }

    public static void notifyInfo(@Nullable Project project, String message) {
        Notifications.Bus.notify(new Notification(Constants.RESTFLOW_NOTIFICATION_GROUP,
          "RESTflow", message, NotificationType.INFORMATION));
    }

    public static void notifyError(@Nullable Project project, String message, Throwable exception) {
        Notifications.Bus.notify(new Notification(Constants.RESTFLOW_NOTIFICATION_GROUP,
          "RESTflow", message, NotificationType.ERROR)
          .addAction(new AnAction("Show Details...") {
              @Override
              public void actionPerformed(@NotNull AnActionEvent e) {
                  MyNotifications.showDetailDialog(project, message, exception);
              }
          }));
    }

    private String createReport(@Nullable Throwable exception) {
        ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
        IdeaPluginDescriptor pluginInfo = Objects.requireNonNull(PluginManager.getPlugin(Constants.PLUGIN_ID),
                "PluginManager.getPlugin(Constants.PLUGIN_ID)");
        StringWriter string = new StringWriter();
        PrintWriter print = new PrintWriter(string);
        print.println(message);
        print.println();
        print.println(String.format("%s (%s) Build #%s",
                appInfo.getVersionName() + " " + appInfo.getFullVersion(),
                ApplicationNamesInfo.getInstance().getEditionName(),
                appInfo.getBuild().asString()));
        print.println(String.format("%s %s",
                pluginInfo.getPluginId().getIdString(), pluginInfo.getVersion()));
        print.println();
        print.println(new Date());
        printProperty(print, "java.vendor");
        printProperty(print, "java.runtime.version");
        printProperty(print, "java.version");
        printProperty(print, "java.vm.name");
        printProperty(print, "java.vm.info");
        printProperty(print, "os.name");
        printProperty(print, "os.version");
        printProperty(print, "os.arch");
        print.println();
        if (exception != null) {
            exception.printStackTrace(print);
        } else {
            print.println("No exception given");
        }
        print.flush();
        return string.toString().trim();
    }

    @NotNull
    @Override
    protected Action[] createActions() {
        return new Action[] {
                new AbstractAction("Close") {
                    {
                        putValue(Action.MNEMONIC_KEY, (int)'o');
                    }

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        close(OK_EXIT_CODE, true);
                    }
                }
        };
    }

    @NotNull
    @Override
    protected Action[] createLeftSideActions() {
        return new Action[] {
                new AbstractAction("Copy to Clipboard", AllIcons.Actions.Copy) {
                    {
                        putValue(Action.MNEMONIC_KEY, (int)'c');
                    }
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        CopyPasteManager.getInstance().setContents(
                                new TextTransferable("<pre>" + escapeHtml(report) + "</pre>", report));
                    }

                }
        };
    }

    private String escapeHtml(String str) {
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JLabel(message), BorderLayout.NORTH);
        JTextArea info = new JTextArea();
        info.setTabSize(4);
        info.setText(report);
        info.setEditable(false);
        info.setBackground(panel.getBackground());
        info.setCaretPosition(0);
        JBScrollPane scrollPane = new JBScrollPane(info);
        Dimension d = scrollPane.getPreferredSize();
        int fontHeight = scrollPane.getFontMetrics(scrollPane.getFont()).getHeight();
        int maxHeight = fontHeight * 25;
        d.height = d.height > 0 ? Math.min(d.height, maxHeight) : maxHeight;
        int maxWidth = fontHeight * 70;
        d.width = d.width > 0 ? Math.min(d.width, maxWidth) : maxWidth;
        scrollPane.setPreferredSize(d);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private static PrintWriter printProperty(PrintWriter print, String name) {
        print.print(name);
        print.print(": ");
        print.println(System.getProperty(name));
        return print;
    }

    public static void showDetailDialog(@Nullable Project project, String message, @Nullable Throwable exception) {
        ApplicationManager.getApplication().invokeLater(() -> {
            MyNotifications dialog = new MyNotifications(project, message, exception);
            dialog.pack();
            dialog.show();
        });
    }
}
