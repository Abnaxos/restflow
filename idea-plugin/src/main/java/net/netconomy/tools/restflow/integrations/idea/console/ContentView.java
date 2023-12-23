package net.netconomy.tools.restflow.integrations.idea.console;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.swing.JComponent;
import javax.swing.JPanel;

import com.google.common.collect.ImmutableMap;
import com.intellij.codeInsight.actions.FileInEditorProcessor;
import com.intellij.codeInsight.actions.ReformatCodeRunOptions;
import com.intellij.codeInsight.actions.TextRangeType;
import com.intellij.execution.filters.CompositeFilter;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.ConsoleFilterProviderEx;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.impl.http.RemoteFileUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ContentView {

    private static final Document PROGRESS_DOCUMENT = EditorFactory.getInstance().createDocument("Rendering...");

    private static final Map<String, String> MIME_TYPE_MAPPINGS = ImmutableMap.of(
            "application/xhtml+xml", "application/xml",
            "application/atom+xml", "application/xml",
            "application/rss+xml", "application/xml",
            "application/mathml+xml", "application/xml");

    private final Project project;
    private final boolean console;
    private final JPanel container;
    private final MyEditorTextField editorTextField;

    private final CompositeFilter filters;
    @Nullable
    private EditorHyperlinkSupport hyperlinks;

    private boolean reformat = true;
    private FileType fileType = FileTypes.UNKNOWN;
    @Nullable
    private StructuredLogTreeModel.Body currentBody;
    private int updateSerial = 0;

    public ContentView(Project project, boolean console) {
        this.project = project;
        this.console = console;
        container = new JPanel(new BorderLayout());
        editorTextField = new MyEditorTextField(project, console);
        container.add(editorTextField, BorderLayout.CENTER);
        var actionToolbar = ActionManager.getInstance().createActionToolbar(getClass().getName(),
                createToolbarActions(), false);
        actionToolbar.setTargetComponent(editorTextField);
        container.add(actionToolbar.getComponent(), BorderLayout.WEST);
        if (console) {
            GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
            ArrayList<Filter> result = new ArrayList<>();
            for (ConsoleFilterProvider eachProvider : ConsoleFilterProvider.FILTER_PROVIDERS.getExtensions()) {
                Filter[] filters;
                if (eachProvider instanceof ConsoleFilterProviderEx) {
                    filters = ((ConsoleFilterProviderEx)eachProvider).getDefaultFilters(project, searchScope);
                }
                else {
                    filters = eachProvider.getDefaultFilters(project);
                }
                ContainerUtil.addAll(result, filters);
            }
            this.filters = new CompositeFilter(project, result);
        } else {
            this.filters = new CompositeFilter(project);
        }
    }

    @Nullable
    public StructuredLogTreeModel.Body currentBody() {
        return currentBody;
    }

    public FileType fileType() {
        return fileType;
    }

    private DefaultActionGroup createToolbarActions() {
        DumbAwareToggleAction reformatAction = new DumbAwareToggleAction("Reformat", null, AllIcons.Actions.Annotate) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return reformat;
            }
            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                reformat(state);
            }
        };
        DumbAwareToggleAction softWrap = new DumbAwareToggleAction("Wrap Lines", null, AllIcons.Actions.ToggleSoftWrap) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return getEditor().getSettings().isUseSoftWraps();
            }
            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                getEditor().getSettings().setUseSoftWraps(state);
            }
        };
        if (console) {
            return new DefaultActionGroup(softWrap);
        } else {
            return new DefaultActionGroup(reformatAction, softWrap);
        }
    }

    JComponent getComponent() {
        return container;
    }

    EditorTextField getTextField() {
        return editorTextField;
    }

    EditorEx getEditor() {
        return (EditorEx)Objects.requireNonNull(editorTextField.getEditor(true), "editorTextField.getEditor()");
    }

    void showBody(@Nullable StructuredLogTreeModel.Body body) {
        currentBody = body;
        if (body == null) {
            clear();
            return;
        }
        FileType t = Optional.ofNullable(RemoteFileUtil.getFileType(body.mimeType))
                .orElseGet(() -> Optional.ofNullable(MIME_TYPE_MAPPINGS.get(body.mimeType))
                        .map(RemoteFileUtil::getFileType)
                        .orElse(FileTypes.UNKNOWN));
        this.fileType = t;
        String c = body.charContent != null
                ? body.charContent
                : body.base64();
        if (body.charContent != null) {
            editorTextField.setDocument(PROGRESS_DOCUMENT);
            updateDocument(t, c, this.reformat);
        } else {
            editorTextField.setDocument(EditorFactory.getInstance().createDocument(body.base64()));
            resetView();
        }
    }

    void showLog(StructuredLogTreeModel.Node<?, ?> node) {
        class Fold {
            final LogLine.Channel channel;
            final int start;
            final String placeholder;
            int end;
            Fold(LogLine.Channel channel, int start, String placeholder) {
                this(channel, start, -1, placeholder);
            }
            Fold(LogLine.Channel channel, int start, int end, String placeholder) {
                this.channel = channel;
                this.start = start;
                this.end = end;
                this.placeholder = placeholder;
            }
        }
        clear();
        fileType = FileTypes.PLAIN_TEXT;
        Document doc = getEditor().getDocument();
        List<Fold> foldings = new ArrayList<>();
        Fold bodyRegion = null;
        int lineCount = 0;
        for (LogLine line : node.log) {
            lineCount++;
            String text = line.text() + "\n";
            int start = doc.getTextLength();
            ConsoleViewContentType type = null;
            switch (line.channel()) {
            case CONSOLE:
                type = RfConsoleView.CONSOLE_OUTPUT;
                break;
            case INFO:
                type = ConsoleViewContentType.NORMAL_OUTPUT;
                break;
            case ERROR:
                type = ConsoleViewContentType.ERROR_OUTPUT;
                break;
            case HTTP_OUT:
                type = RfConsoleView.CONSOLE_HTTP_OUT;
                break;
            case HTTP_IN_OK:
                type = RfConsoleView.CONSOLE_HTTP_IN_OK;
                break;
            case HTTP_IN_ERR:
                type = RfConsoleView.CONSOLE_HTTP_IN_ERR;
                break;
            case HTTP_IN_WARN:
                type = RfConsoleView.CONSOLE_HTTP_IN_WARN;
                break;
            case UNKNOWN:
            case SCRIPT:
                break;
            }
            if (type == null) {
                continue;
            }
            int prevOffset = doc.getTextLength();
            doc.insertString(start, text);
            if (line.channel().isHttp()) {
                if (line.text().equals("BODY END") && bodyRegion != null) {
                    if (line.channel().equals(bodyRegion.channel)) {
                        bodyRegion.end = doc.getTextLength() - 1;
                        foldings.add(bodyRegion);
                    }
                    bodyRegion = null;
                } else //noinspection StatementWithEmptyBody
                    if (line.text().equals("BODY EMPTY")) {
                    // NOP -- one-line folding region makes no sense and isn't handled correctly by editor
                    //foldings.add(new Fold(line.channel(), prevOffset, doc.getTextLength() - 1, line.text().trim()));
                } else if (line.text().startsWith("BODY ")) {
                    bodyRegion = new Fold(line.channel(), prevOffset, line.text().trim());
                }
            }
            getEditor().getMarkupModel().addRangeHighlighter(start, doc.getTextLength(),
                    HighlighterLayer.SYNTAX, type.getAttributes(), HighlighterTargetArea.EXACT_RANGE);
        }
        if (hyperlinks == null) {
            hyperlinks = new EditorHyperlinkSupport(getEditor(), project);
        }
        hyperlinks.highlightHyperlinks(filters, 0, lineCount);
        FoldingModelEx foldingModel = getEditor().getFoldingModel();
        foldingModel.runBatchFoldingOperation(() ->
                foldings.forEach(f -> {
                    FoldRegion region = foldingModel.addFoldRegion(f.start, f.end, f.placeholder);
                    if (region != null) {
                        region.setExpanded(false);
                    }
                }));
        resetView();
    }

    void reformat(boolean reformat) {
        if (reformat == this.reformat) {
            return;
        }
        this.reformat = reformat;
        if (currentBody != null && currentBody.charContent != null) {
            doReformat(editorTextField.getDocument());
        }
    }

    private void updateDocument(FileType type, String content, boolean reformat) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        int serial = ++updateSerial;
        ApplicationManager.getApplication().executeOnPooledThread(
                () -> ApplicationManager.getApplication().runReadAction(() -> {
                    PsiFileFactory factory = PsiFileFactory.getInstance(project);
                    long stamp = LocalTimeCounter.currentTime();
                    PsiFile psiFile = factory.createFileFromText("Dummy." + type.getDefaultExtension(),
                            type, content, stamp, true, false);
                    Document doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (updateSerial != serial) {
                            return;
                        }
                        if (reformat && doc != null) {
                            doReformat(doc);
                        }
                        editorTextField.setDocument(doc);
                        resetView();
                    });
                }));
    }

    private void doReformat(Document document) {
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (psiFile == null) {
            return;
        }
        new FileInEditorProcessor(psiFile, getEditor(), new ReformatCodeRunOptions(TextRangeType.WHOLE_FILE)
                .setOptimizeImports(false)
                .setRearrangeCode(false))
                .processCode();
    }

    public void clear() {
        editorTextField.setNewDocumentAndFileType(FileTypes.PLAIN_TEXT,
                EditorFactory.getInstance().createDocument(""));
    }

    void resetView() {
        getEditor().getCaretModel().moveToOffset(0);
        getEditor().getScrollingModel().scrollTo(new LogicalPosition(0, 0), ScrollType.MAKE_VISIBLE);
    }

    private class MyEditorTextField extends EditorTextField {

        private final boolean console;

        MyEditorTextField(Project project, boolean console) {
            super(null, project, FileTypes.PLAIN_TEXT, false, false);
            this.console = console;
        }

        @Override
        protected EditorEx createEditor() {
            EditorEx editor = super.createEditor();
            EditorSettings settings = editor.getSettings();
            settings.setLineNumbersShown(true);
            settings.setWheelFontChangeEnabled(true);
            settings.setFoldingOutlineShown(true);
            settings.setAutoCodeFoldingEnabled(true);
            settings.setAllowSingleLogicalLineFolding(true);
            settings.setIndentGuidesShown(true);
            editor.setHorizontalScrollbarVisible(true);
            editor.setVerticalScrollbarVisible(true);
            if (console) {
                DelegateColorScheme scheme = ConsoleViewUtil.updateConsoleColorScheme(editor.getColorsScheme());
                editor.setColorsScheme(scheme);
                if (UISettings.getInstance().getPresentationMode()) {
                    editor.getColorsScheme().setEditorFontSize(UISettings.getInstance().getPresentationModeFontSize());
                }
            } else {
                editor.setColorsScheme(EditorColorsManager.getInstance().getGlobalScheme());
            }
            editor.setViewer(true);
            hyperlinks = null;
            //editor.setHighlighter(new ConsoleViewUtil.NullEditorHighlighter());
            return editor;
        }
    }
}
