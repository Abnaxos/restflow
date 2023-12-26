package net.netconomy.tools.restflow.integrations.idea.console;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.render.LabelBasedRenderer;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeModelAdapter;
import net.netconomy.tools.restflow.integrations.idea.console.external.Interface;
import net.netconomy.tools.restflow.integrations.idea.util.StreamNavigation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * TODO JavaDoc
 */
class StructuredConsoleView {

    public static final String UNKNOWN_SCRIPT_NAME = "unknown.restflow";
    public static final String RESPONSE_TAB_TITLE = "Response Body";
    public static final String REQUEST_TAB_TITLE = "Request Body";


    private static final Icon PREV_ICON = AllIcons.Actions.PreviousOccurence;
    private static final Icon NEXT_ICON = AllIcons.Actions.NextOccurence;
    private static final Icon PREV_ERR_ICON = LayeredIcon.create(PREV_ICON, AllIcons.General.WarningDecorator);
    private static final Icon NEXT_ERR_ICON = LayeredIcon.create(NEXT_ICON, AllIcons.General.WarningDecorator);
    private static final Icon LAST_ERR_ICON = LayeredIcon.create(
            AllIcons.RunConfigurations.Scroll_down, AllIcons.General.WarningDecorator);

    private final Project project;
    private final JPanel root;
    private final JBSplitter mainSplitter;
    private final Tree logTree;
    private final JBScrollPane eventScroller;
    private final JPanel detailsPanel;
    private final JLabel noDetails;
    private final JPanel requestDetails;
    private final JPanel requestSummaryPanel;
    private final JLabel requestSummaryHeadLabel;
    private final LinkLabel<?> requestLinkLabel;
    private final JBTabbedPane detailsTabs;
    private final ContentView requestView;
    private final ContentView responseView;
    private final ContentView logView;

    private final StructuredLogTreeModel logModel;

    private final List<LogLine> lineBuffer = new ArrayList<>();
    @SuppressWarnings("StringBufferField")
    private final StringBuilder currentLineBuffer = new StringBuilder();
    private boolean hadCR = false;
    private String currentScript = UNKNOWN_SCRIPT_NAME;

    private boolean autoScroll = true;
    @Nullable
    private TreePath autoScrollIgnoreSelection;
    private boolean autoExpanding = false;

    StructuredConsoleView(Project project) {
        this.project = project;
        root = new JPanel(new BorderLayout());
        mainSplitter = new JBSplitter(StructuredConsoleView.class.getName(), .25f);
        root.add(mainSplitter, BorderLayout.CENTER);
        logTree = new Tree(logModel = new StructuredLogTreeModel());
        logTree.setRootVisible(false);
        logTree.setShowsRootHandles(true);
        logModel.addTreeModelListener(new TreeModelAdapter() {
            @Override
            public void treeNodesInserted(TreeModelEvent event) {
                // make sure the JTree handles the events first, it gets confused otherwise
                ApplicationManager.getApplication().invokeLater(() -> onLogNodeAdded(event));
            }
        });
        logTree.setCellRenderer(new LabelBasedRenderer.Tree() {
            @Override
            public Component getTreeCellRendererComponent(
                    JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                var node = (StructuredLogTreeModel.Node<?, ?>)value;
                setText(node.text());
                setIcon(node.icon());
                return this;
            }
        });
        logTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        eventScroller = new JBScrollPane(logTree);
        eventScroller.setBorder(BorderFactory.createEmptyBorder());
        mainSplitter.setFirstComponent(eventScroller);
        detailsPanel = new JPanel(new BorderLayout(0, 0));
        noDetails = new JLabel("No Selection", SwingConstants.CENTER);
        noDetails.setVerticalAlignment(SwingConstants.CENTER);
        requestDetails = new JPanel(new BorderLayout(0, 0));
        logTree.addTreeSelectionListener(
                e -> {
                    autoScrollIgnoreSelection = null;
                    if (e.getPath() == null || !e.isAddedPath()) {
                        setDetails(noDetails);
                    } else {
                        updateSelection((StructuredLogTreeModel.Node<?, ?>)e.getPath().getLastPathComponent());
                    }
                });
        mainSplitter.setSecondComponent(detailsPanel);
        requestSummaryPanel = new JPanel(new BorderLayout(0, 0));
        requestSummaryPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
        requestSummaryPanel.setVisible(false);
        requestSummaryHeadLabel = new JLabel("GET ");
        requestSummaryPanel.add(requestSummaryHeadLabel, BorderLayout.WEST);
        //noinspection DialogTitleCapitalization
        requestLinkLabel = new LinkLabel<>("https://example.com/", null, (l, __) -> BrowserUtil.browse(l.getText()));
        requestLinkLabel.setMinimumSize(new Dimension(0, 0));
        requestSummaryPanel.add(requestLinkLabel, BorderLayout.CENTER);
        requestDetails.add(requestSummaryPanel, BorderLayout.NORTH);
        detailsTabs = new JBTabbedPane();
        detailsTabs.setTabComponentInsets(JBUI.emptyInsets());
        requestView = new ContentView(project, false);
        responseView = new ContentView(project, false);
        detailsTabs.add(RESPONSE_TAB_TITLE, responseView.getComponent());
        detailsTabs.add(REQUEST_TAB_TITLE, requestView.getComponent());
        detailsTabs.add("Log", (logView = new ContentView(project, true)).getComponent());
        requestDetails.add(detailsTabs, BorderLayout.CENTER);
        setDetails(noDetails);
    }

    JComponent getComponent() {
        return root;
    }

    JComponent getPreferredFocusableComponent() {
        return logTree;
    }

    public void clear() {
        logModel.getRoot().clear();
    }

    public void scrollTo(int offset) {
        // TODO (2019-09-15) implement
    }

    public void scrollToEnd() {
        if (logTree.getRowCount() > 0) {
            verticalScrollToVisible(logTree.getRowBounds(logTree.getRowCount() - 1));
        }
    }

    public boolean isAutoScroll() {
        return autoScroll;
    }

    public void setAutoScroll(boolean autoScroll) {
        if (this.autoScroll != autoScroll) {
            autoScrollIgnoreSelection = null;
            this.autoScroll = autoScroll;
            if (autoScroll) {
                autoScrollIgnoreSelection = logTree.getSelectionPath();
                scrollToEnd();
            }
        }
    }

    Stream<Trinity<String, VirtualFile, Icon>> history() {
        VirtualFileManager vfm = VirtualFileManager.getInstance();
        return Lists.reverse(logModel.getRoot().children).stream()
                .map(g -> new Trinity<>(g.text(), vfm.findFileByUrl(g.uri()), g.icon()))
                .filter(p -> p.second != null);
    }

    AnAction[] createActions() {
        abstract class NavAction extends AnAction {
            public NavAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
                super(text, description, icon);
            }
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                node().map(StructuredLogTreeModel.Node::treePath)
                        .ifPresent(p -> {
                            TreePath treePath = new TreePath(p);
                            logTree.setSelectionPath(treePath);
                            scrollToPath(treePath);
                        });
            }
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
            @Override
            public void update(@NotNull AnActionEvent e) {
                super.update(e);
                e.getPresentation().setEnabled(node().isPresent());
            }
            abstract Optional<? extends StructuredLogTreeModel.Node<?, ?>> node();
        }
        Predicate<StructuredLogTreeModel.RequestNode> errorNodePredicate =
                r -> StructuredLogTreeModel.State.WARN.compareTo(r.state()) <= 0;
        return new AnAction[] {
                new NavAction("Last Failed Request", null, LAST_ERR_ICON) {
                    @Override
                    Optional<? extends StructuredLogTreeModel.Node<?, ?>> node() {
                        return lastRequest(errorNodePredicate);
                    }
                },
                new NavAction("Previous Failed Request", null, PREV_ERR_ICON) {
                    @Override
                    Optional<? extends StructuredLogTreeModel.Node<?, ?>> node() {
                        return previousRequest(selectedNode(), errorNodePredicate);
                    }
                },
                new NavAction("Next Failed Request", null, NEXT_ERR_ICON) {
                    @Override
                    Optional<? extends StructuredLogTreeModel.Node<?, ?>> node() {
                        return nextRequest(selectedNode(), errorNodePredicate);
                    }
                },
                new Separator(),
                new NavAction("Previous Request", null, PREV_ICON) {
                    @Override
                    Optional<? extends StructuredLogTreeModel.Node<?, ?>> node() {
                        return previousRequest(selectedNode(), __ -> true);
                    }
                },
                new NavAction("Next Request", null, NEXT_ICON) {
                    @Override
                    Optional<? extends StructuredLogTreeModel.Node<?, ?>> node() {
                        return nextRequest(selectedNode(), __ -> true);
                    }
                },
                new Separator(),
                new ToggleAction("Scroll to End", null, AllIcons.RunConfigurations.Scroll_down) {
                    @Override
                    public boolean isSelected(@NotNull AnActionEvent e) {
                        return isAutoScroll();
                    }
                    @Override
                    public void setSelected(@NotNull AnActionEvent e, boolean state) {
                        setAutoScroll(state);
                    }
                    @Override
                    public @NotNull ActionUpdateThread getActionUpdateThread() {
                        return ActionUpdateThread.EDT;
                    }
                },
                new AnAction("Clear", null, AllIcons.Actions.GC) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        clear();
                    }
                }
        };
    }

    void append(String string) {
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            boolean closeLine = false;
            boolean skip = false;
            if (c == '\r') {
                closeLine = true;
            } else if (c == '\n') {
                closeLine = true;
                skip = hadCR;
            }
            if (!skip) {
                if (closeLine) {
                    String line = currentLineBuffer.toString();
                    currentLineBuffer.setLength(0);
                    addLogLine(line);
                } else {
                    currentLineBuffer.append(c);
                }
            }
            hadCR = (c == '\r');
        }
    }

    private void addLogLine(String text) {
        LogLine line = LogLine.of(text);
        String statusBarMessage = null;
        if (line.channel() == LogLine.Channel.CONSOLE) {
            if (line.text().startsWith(Interface.RUN_OUT_RUN)) {
                String s = line.text().substring(Interface.RUN_OUT_RUN.length()).trim();
                s = s.replace(File.separatorChar, '/');
                int pos = s.lastIndexOf('/');
                if (pos >= 0) {
                    s = s.substring(pos + 1);
                }
                currentScript = s;
                statusBarMessage = "Running RESTflow script " + currentScript;
            }
            if (line.text().startsWith(Interface.RUN_OUT_RUN)) {
                statusBarMessage = "RESTflow script " + currentScript + " finished successfully";
            } else if (line.text().startsWith(Interface.RUN_OUT_ERROR)) {
                statusBarMessage = "RESTflow script " + currentScript + " finished with error: "
                        + line.text().substring(Interface.RUN_OUT_ERROR.length()).trim();
            }
        }
        lineBuffer.add(line);
        if (statusBarMessage != null) {
            // TODO (2023-12-26) find a good way to communicate these
            // just nothing is better than NPE and IDE error, though; also, it doesn't work anyway
            //StatusBar.Info.set(statusBarMessage, project);
            //WindowManager.getInstance().getStatusBar(root, project).setInfo(statusBarMessage);
        }
    }

    void update() {
        List<LogLine> lines;
        synchronized (lineBuffer) {
            lines = ImmutableList.copyOf(lineBuffer);
            lineBuffer.clear();
        }
        lines.forEach(logModel::appendLogLine);
    }

    private void updateSelection(StructuredLogTreeModel.Node<?, ?> node) {
        setDetails(requestDetails);
        ApplicationManager.getApplication().runWriteAction(() ->
                WriteCommandAction.writeCommandAction(project).run(() -> {
                    logView.showLog(node);
                    //noinspection ObjectEquality
                    if (detailsTabs.getComponentAt(0) != responseView.getComponent()) {
                        detailsTabs.insertTab(RESPONSE_TAB_TITLE, null, responseView.getComponent(), null, 0);
                    }
                    //noinspection ObjectEquality
                    if (detailsTabs.getComponentAt(1) != requestView.getComponent()) {
                        detailsTabs.insertTab(REQUEST_TAB_TITLE, null, requestView.getComponent(), null, 1);
                    }
                    requestLinkLabel.setText("");
                    requestLinkLabel.setToolTipText("");
                    requestSummaryHeadLabel.setText("");
                    boolean summaryVisible = false;
                    if (node instanceof StructuredLogTreeModel.RequestNode) {
                        var rn = (StructuredLogTreeModel.RequestNode)node;
                        StringBuilder summary = new StringBuilder();
                        if (rn.httpCode() != null) {
                            summary.append(rn.httpCode());
                            summaryVisible = true;
                        }
                        if (rn.method() != null) {
                            if (!summary.isEmpty()) {
                                summary.append(": ");
                            }
                            summary.append(rn.method());
                            summaryVisible = true;
                        }
                        if (!summary.isEmpty()) {
                            summary.append(' ');
                        }
                        requestSummaryHeadLabel.setText(summary.toString());
                        if (rn.requestUri() != null) {
                            requestLinkLabel.setText(rn.requestUri());
                            requestLinkLabel.setToolTipText(rn.requestUri());
                            summaryVisible = true;
                        }
                        requestView.showBody(rn.requestBody());
                        responseView.showBody(rn.responseBody());
                    } else {
                        requestView.showBody(null);
                        responseView.showBody(null);
                    }
                    updateTab(requestView, REQUEST_TAB_TITLE, 1);
                    updateTab(responseView, RESPONSE_TAB_TITLE, 0);
                    detailsTabs.setSelectedIndex(0);
                    requestSummaryPanel.setVisible(summaryVisible);
                    root.validate();
                }));
    }

    private void updateTab(ContentView view, String title, int index) {
        StructuredLogTreeModel.Body body = view.currentBody();
        if (body == null || body.empty()) {
            detailsTabs.removeTabAt(index);
        } else {
            FileType t = view.fileType();
            Component component = detailsTabs.getComponentAt(index);
            detailsTabs.removeTabAt(index);
            String description = " (" + (t.equals(FileTypes.UNKNOWN) ? body.mimeType : t.getDescription()) + ")";
            detailsTabs.insertTab((t.getIcon() != null ? " " : "") + title + description,
                    t.getIcon(), component, null, index);
            view.resetView();
        }
    }

    private void setDetails(JComponent component) {
        //noinspection ObjectEquality
        if (detailsPanel.getComponentCount() != 1 || detailsPanel.getComponent(0) != component) {
            while (detailsPanel.getComponentCount() > 0) {
                detailsPanel.remove(0);
            }
            detailsPanel.add(component, BorderLayout.CENTER);
            detailsPanel.revalidate();
            detailsPanel.repaint();
        }
    }

    private void onLogNodeAdded(TreeModelEvent event) {
        TreePath selectionPath = logTree.getSelectionPath();
        if (event.getTreePath().getLastPathComponent() instanceof StructuredLogTreeModel.GroupNode) {
            autoExpanding = true;
            try {
                StructuredLogTreeModel.GroupNode added =
                        (StructuredLogTreeModel.GroupNode)event.getTreePath().getLastPathComponent();
                int addedIndex = logModel.getRoot().children.indexOf(added);
                if (addedIndex > 0) {
                    StructuredLogTreeModel.GroupNode collapse = logModel.getRoot().children.get(addedIndex - 1);
                    if (selectionPath == null || !Arrays.asList(selectionPath.getPath()).contains(collapse)) {
                        logTree.collapsePath(new TreePath(collapse.treePath()));
                    }
                }
                logTree.expandPath(new TreePath(added.treePath()));
            } finally {
                autoExpanding = false;
            }
        }
        if (isAutoScroll()) {
            scrollToEnd();
            if (selectionPath != null && !selectionPath.equals(autoScrollIgnoreSelection)) {
                if (scrollToSelection()) {
                    setAutoScroll(false);
                }
            }
        }
    }

    private boolean scrollToSelection() {
        return scrollToPath(logTree.getSelectionPath());
    }

    private boolean scrollToPath(@Nullable TreePath path) {
        if (path == null) {
            return false;
        }
        Rectangle pathBounds = logTree.getPathBounds(path);
        if (pathBounds != null) {
            pathBounds.x = eventScroller.getViewport().getViewRect().x;
            pathBounds.width = 0;
            if (!eventScroller.getVisibleRect().contains(pathBounds)) {
                verticalScrollToVisible(pathBounds);
                setAutoScroll(false);
                return true;
            }
        }
        return false;
    }

    private void verticalScrollToVisible(@Nullable Rectangle rect) {
        if (rect == null) {
            return;
        }
        rect.x = eventScroller.getViewport().getViewRect().x;
        rect.width = 1;
        logTree.scrollRectToVisible(rect);
    }

    private Optional<StructuredLogTreeModel.Node<?, ?>> selectedNode() {
        StructuredLogTreeModel.Node<?, ?> s = (StructuredLogTreeModel.Node<?, ?>)logTree.getLastSelectedPathComponent();
        return s == null || s instanceof StructuredLogTreeModel.RootNode ? Optional.empty() : Optional.of(s);
    }

    private Optional<StructuredLogTreeModel.RequestNode> nextRequest(
            Optional<? extends StructuredLogTreeModel.Node<?, ?>> ref,
            Predicate<? super StructuredLogTreeModel.RequestNode> predicate)
    {
        StructuredLogTreeModel.Node<?, ?> refNode =
                ref.filter(n -> !(n instanceof StructuredLogTreeModel.RootNode)).orElse(null);
        if (refNode == null) {
            return logModel.breathFirst()
                    .filter(StructuredLogTreeModel.RequestNode.class::isInstance)
                    .map(StructuredLogTreeModel.RequestNode.class::cast)
                    .filter(predicate)
                    .reduce(StreamNavigation.last());
        } else {
            return logModel.breathFirst()
                    .filter(StreamNavigation.after(refNode))
                    .filter(StructuredLogTreeModel.RequestNode.class::isInstance)
                    .map(StructuredLogTreeModel.RequestNode.class::cast)
                    .filter(predicate)
                    .findFirst();
        }
    }

    private Optional<StructuredLogTreeModel.RequestNode> previousRequest(
            Optional<? extends StructuredLogTreeModel.Node<?, ?>> ref,
            Predicate<? super StructuredLogTreeModel.RequestNode> predicate)
    {
        StructuredLogTreeModel.Node<?, ?> refNode =
                ref.filter(n -> !(n instanceof StructuredLogTreeModel.RootNode)).orElse(null);
        if (refNode == null) {
            return logModel.breathFirst()
                    .filter(StructuredLogTreeModel.RequestNode.class::isInstance)
                    .map(StructuredLogTreeModel.RequestNode.class::cast)
                    .filter(predicate)
                    .findFirst();
        } else {
            return logModel.breathFirst()
                    .filter(StreamNavigation.before(refNode))
                    .filter(StructuredLogTreeModel.RequestNode.class::isInstance)
                    .map(StructuredLogTreeModel.RequestNode.class::cast)
                    .filter(predicate)
                    .reduce(StreamNavigation.last());
        }
    }

    private Optional<StructuredLogTreeModel.RequestNode> lastRequest(
            Predicate<? super StructuredLogTreeModel.RequestNode> predicate)
    {
        return logModel.breathFirst()
                .filter(StructuredLogTreeModel.RequestNode.class::isInstance)
                .map(StructuredLogTreeModel.RequestNode.class::cast)
                .filter(predicate)
                .reduce(StreamNavigation.last());
    }
}
