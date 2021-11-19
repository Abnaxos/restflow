package net.netconomy.tools.restflow.integrations.idea.console;

import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import net.netconomy.tools.restflow.integrations.idea.ConsoleProcessManager;
import net.netconomy.tools.restflow.integrations.idea.console.external.Interface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class RfConsoleView implements ConsoleView {

    public static final TextAttributesKey TA_CONSOLE_KEY =
            TextAttributesKey.createTextAttributesKey("RESTFLOW_CONSOLE");
    public static final TextAttributesKey TA_HTTP_OUT_KEY =
            TextAttributesKey.createTextAttributesKey("RESTFLOW_HTTP_OUT");
    public static final TextAttributesKey TA_HTTP_IN_OK_KEY =
            TextAttributesKey.createTextAttributesKey("RESTFLOW_HTTP_IN_OK");
    public static final TextAttributesKey TA_HTTP_IN_ERR_KEY =
            TextAttributesKey.createTextAttributesKey("RESTFLOW_HTTP_IN_ERR");
    public static final TextAttributesKey TA_HTTP_IN_WARN_KEY =
            TextAttributesKey.createTextAttributesKey("RESTFLOW_HTTP_IN_WARN");
    private static final Executor DEFAULT_EXECUTOR = DefaultRunExecutor.getRunExecutorInstance();

    static final ConsoleViewContentType CONSOLE_OUTPUT =
            new ConsoleViewContentType("RESTFLOW_CONSOLE_CHATTER", TA_CONSOLE_KEY);
    static final ConsoleViewContentType CONSOLE_HTTP_OUT =
            new ConsoleViewContentType("RESTFLOW_CONSOLE_HTTP_OUT", TA_HTTP_OUT_KEY);
    static final ConsoleViewContentType CONSOLE_HTTP_IN_OK =
            new ConsoleViewContentType("RESTFLOW_CONSOLE_HTTP_IN_OK", TA_HTTP_IN_OK_KEY);
    static final ConsoleViewContentType CONSOLE_HTTP_IN_ERR =
            new ConsoleViewContentType("RESTFLOW_CONSOLE_HTTP_IN_ERR", TA_HTTP_IN_ERR_KEY);
    static final ConsoleViewContentType CONSOLE_HTTP_IN_WARN =
            new ConsoleViewContentType("RESTFLOW_CONSOLE_HTTP_IN_WARN", TA_HTTP_IN_WARN_KEY);
    static final ConsoleViewContentType CONSOLE_HTTP_IN_UNKNOWN =
            new ConsoleViewContentType("RESTFLOW_CONSOLE_HTTP_IN_UNKNWON", TA_HTTP_IN_ERR_KEY);

    private static final Pattern HTTP_CODE_RE = Pattern.compile("(\\d\\d\\d) ");
    private static final int HTTP_CODE_LEN = 4;
    private static final int RERUN_HISTORY_SIZE = 5;

    private final Project project;
    private final Module module;
    private final ProcessHandler processHandler;
    private final RunContentDescriptor descriptor;

    private final JPanel rootPanel = new JPanel(new BorderLayout());
    private final JBTabbedPane tabs = new JBTabbedPane();
    private final MyConsoleView consoleView;
    private final StructuredConsoleView structuredView;

    private final ActionGroup rerunAction = new ActionGroup("Rerun", "Rerun recent RESTflow script", AllIcons.Actions.Rerun) {
        {
            setPopup(true);
        }
        @NotNull
        @Override
        public AnAction[] getChildren(@Nullable AnActionEvent ___) {
            return structuredView.history()
                    .collect(Collectors.groupingBy(e -> e.first, LinkedHashMap::new, Collectors.reducing((f, __) -> f)))
                    .values().stream()
                    .filter(Optional::isPresent)
                    .limit(5)
                    .map(Optional::get)
                    .map(e -> new AnAction(e.first, "Rerun " + e.first, e.third) {
                        @Override
                        public void actionPerformed(@NotNull AnActionEvent __) {
                            RunRestFlowAction.runRestFlow(module, e.second);
                        }
                    })
                    .toArray(AnAction[]::new);
        }

    };
    private final AnAction restartAction = new AnAction("Restart", "Restart RESTflow console", AllIcons.Actions.Restart) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                ConsoleProcessManager proc = module.getComponent(ConsoleProcessManager.class);
                proc.stop(proc::uiStartIfNotRunning);
            });
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(
                    !module.isDisposed() && !processHandler.isProcessTerminated());
        }
    };

    private boolean isShown = false;

    RfConsoleView(Project project, Module module, ProcessHandler processHandler) {
        this.project = project;
        this.module = module;
        this.processHandler = processHandler;
        structuredView = new StructuredConsoleView(this.project);
        this.consoleView = new MyConsoleView(project, GlobalSearchScope.allScope(project), true, true, structuredView);
        buildUI();
        descriptor = new RunContentDescriptor(
                this, this.processHandler, rootPanel, "RESTflow (" + module.getName() + ")");
    }

    public static RfConsoleView createView(Project project, Module module, ProcessHandler processHandler) {
        RfConsoleView view = new RfConsoleView(project, module, processHandler);
        view.attachToProcess(processHandler);
        processHandler.startNotify();
        return view;
    }

    @Override
    public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
        consoleView.print(text, contentType);
    }

    @Override
    public void clear() {
        consoleView.clear();
        structuredView.clear();
    }

    @Override
    public void scrollTo(int offset) {
        consoleView.scrollTo(offset);
        structuredView.scrollTo(offset);
    }

    @Override
    public void setOutputPaused(boolean value) {
        consoleView.setOutputPaused(value);
    }

    @Override
    public boolean isOutputPaused() {
        return consoleView.isOutputPaused();
    }

    @Override
    public boolean hasDeferredOutput() {
        return consoleView.hasDeferredOutput();
    }

    @Override
    public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
        consoleView.performWhenNoDeferredOutput(runnable);
    }

    @Override
    public void setHelpId(@NotNull String helpId) {
        consoleView.setHelpId(helpId);
    }

    @Override
    public void addMessageFilter(@NotNull Filter filter) {
        consoleView.addMessageFilter(filter);
    }

    @Override
    public void printHyperlink(@NotNull String hyperlinkText, @org.jetbrains.annotations.Nullable HyperlinkInfo info) {
        consoleView.printHyperlink(hyperlinkText, info);
    }

    @Override
    public int getContentSize() {
        return consoleView.getContentSize();
    }

    @Override
    public boolean canPause() {
        return consoleView.canPause();
    }

    @Override
    public void allowHeavyFilters() {
        consoleView.allowHeavyFilters();
    }

    @Override
    public JComponent getPreferredFocusableComponent() {
        return structuredView.getPreferredFocusableComponent();
    }

    @Override
    public void dispose() {
        // TODO (2019-08-31) more?
        consoleView.dispose();
    }

    @Nullable
    private static ConsoleViewContentType contentTypeForPrefix(char prefix) {
        switch (prefix) {
        case Interface.PREFIX_SCRIPT:
        case Interface.PREFIX_RUN:
            return CONSOLE_OUTPUT;
        case Interface.PREFIX_OUT_STDOUT:
            return ConsoleViewContentType.NORMAL_OUTPUT;
        case Interface.PREFIX_OUT_STDERR:
            return ConsoleViewContentType.ERROR_OUTPUT;
        case Interface.PREFIX_OUT_HTTP_IN:
            return CONSOLE_HTTP_IN_UNKNOWN;
        case Interface.PREFIX_OUT_HTTP_OUT:
            return CONSOLE_HTTP_OUT;
        default:
            return null;
        }
    }

    @Override
    public void attachToProcess(ProcessHandler processHandler) {
        consoleView.attachToProcess(processHandler);
        consoleView.getEditor().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                Application app = ApplicationManager.getApplication();
                if (app.isDispatchThread()) {
                    structuredView.update();
                } else {
                    app.invokeLater(structuredView::update);
                }
            }
        });
    }

    public void bringToFront() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (isShown) {
            ExecutionManager.getInstance(project).getContentManager()
                    .toFrontRunContent(DEFAULT_EXECUTOR, descriptor);
        } else {
            ExecutionManager.getInstance(project).getContentManager()
                    .showRunContent(DEFAULT_EXECUTOR, descriptor, descriptor);
            isShown = true;
        }
    }

    @NotNull
    @Override
    public JComponent getComponent() {
        return rootPanel;
    }

    @NotNull
    @Override
    public AnAction[] createConsoleActions() {
        return new AnAction[] {restartAction};
    }

    private void buildUI() {
        rootPanel.add(tabs, BorderLayout.CENTER);
        tabs.addTab("Requests", buildStructuredComponent());
        tabs.addTab("Console", buildConsoleComponent());
    }

    private JComponent buildStructuredComponent() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(structuredView.getComponent(), BorderLayout.CENTER);
        panel.add(ActionManager.getInstance()
                .createActionToolbar(getClass().getName() + ".structuredView",
                        createTabActionGroup(structuredView.createActions()), false)
                .getComponent(), BorderLayout.WEST);
        tabs.setTabComponentInsets(JBUI.emptyInsets());
        return panel;
    }

    private JComponent buildConsoleComponent() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(consoleView.getComponent(), BorderLayout.CENTER);
        panel.add(ActionManager.getInstance()
                .createActionToolbar(getClass().getName() + ".console",
                        createTabActionGroup(consoleView.createConsoleActions()), false)
                .getComponent(), BorderLayout.WEST);
        return panel;
    }

    private ActionGroup createTabActionGroup(AnAction[] specificActions) {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(rerunAction);
        actionGroup.addSeparator();
        actionGroup.addAll(specificActions);
        actionGroup.addSeparator();
        actionGroup.add(restartAction);
        return actionGroup;
    }

    private static class MyConsoleView extends ConsoleViewImpl {

        private final StructuredConsoleView structuredView;

        private final Object printLock = new Object();
        private boolean hadCR = false;
        private boolean hadLF = true;
        private ConsoleViewContentType currentType = ConsoleViewContentType.NORMAL_OUTPUT;
        @Nullable
        private ConsoleViewContentType currentResponseType;
        private boolean expectingHttpCode;
        @SuppressWarnings("StringBufferField")
        private final StringBuilder httpCodeBuffer = new StringBuilder(HTTP_CODE_LEN);

        public MyConsoleView(@NotNull Project project, @NotNull GlobalSearchScope searchScope, boolean viewer, boolean usePredefinedMessageFilter, StructuredConsoleView structuredView) {
            super(project, searchScope, viewer, usePredefinedMessageFilter);
            this.structuredView = structuredView;
            synchronized (printLock) {
                currentResponseType = null;
                expectingHttpCode = false;
            }
        }

        @Override
        public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
            if (contentType.equals(ConsoleViewContentType.NORMAL_OUTPUT) && text.length() > 0) {
                synchronized (printLock) {
                    structuredView.append(text);
                    int mark = 0;
                    int pos = 0;
                    while (pos < text.length()) {
                        char c = text.charAt(pos);
                        if (expectingHttpCode) {
                            httpCodeBuffer.append(c);
                            if (httpCodeBuffer.length() == HTTP_CODE_LEN) {
                                String str = httpCodeBuffer.toString();
                                Matcher m = HTTP_CODE_RE.matcher(str);
                                if (m.matches()) {
                                    int code = Integer.parseInt(m.group(1));
                                    if (code >= 200 && code < 300) {
                                        currentType = CONSOLE_HTTP_IN_OK;
                                    } else if (code >= 100 && code < 200 || code >= 300 && code < 400) {
                                        currentType = CONSOLE_HTTP_IN_WARN;
                                    } else {
                                        currentType = CONSOLE_HTTP_IN_ERR;
                                    }
                                    currentResponseType = currentType;
                                }
                                doPrint(str, 0, str.length(), currentType);
                                mark = pos + 1;
                                continue;
                            }
                        }
                        if (c == '\r') {
                            if (hadCR) {
                                mark = doPrint(text, mark, pos, currentType);
                            }
                            hadCR = true;
                            hadLF = false;
                            currentType = ConsoleViewContentType.NORMAL_OUTPUT;
                            mark = doPrint(text, mark, pos + 1, currentType);
                        } else if (c == '\n') {
                            mark = doPrint(text, mark, pos + 1, currentType);
                            hadCR = false;
                            hadLF = true;
                            currentType = ConsoleViewContentType.NORMAL_OUTPUT;
                        } else {
                            if (hadCR || hadLF) {
                                mark = doPrint(text, mark, pos, currentType);
                                ConsoleViewContentType t = contentTypeForPrefix(c);
                                if (t != null) {
                                    mark++;
                                    currentType = t;
                                    if (currentType.equals(CONSOLE_HTTP_OUT)) {
                                        currentResponseType = null;
                                    } else if (currentType.equals(CONSOLE_HTTP_IN_UNKNOWN)) {
                                        if (currentResponseType == null) {
                                            expectingHttpCode = true;
                                            httpCodeBuffer.setLength(0);
                                        } else {
                                            currentType = currentResponseType;
                                        }
                                    }
                                } else {
                                    currentType = ConsoleViewContentType.NORMAL_OUTPUT;
                                }
                            }
                            hadCR = hadLF = false;
                        }
                        pos++;
                    }
                    if (!expectingHttpCode) {
                        doPrint(text, mark, text.length(), currentType);
                    }
                }
            } else {
                doPrint(text, 0, text.length(), contentType);
            }
        }

        private int doPrint(String text, int start, int end, ConsoleViewContentType contentType) {
            expectingHttpCode = false;
            httpCodeBuffer.setLength(0);
            if (end > start) {
                String part = text.substring(start, end);
                super.print(part, contentType);
            }
            return end;
        }
    }
}
