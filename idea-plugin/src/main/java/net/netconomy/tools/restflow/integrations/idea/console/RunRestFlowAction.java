package net.netconomy.tools.restflow.integrations.idea.console;

import java.io.IOException;
import java.util.function.Function;

import javax.swing.Icon;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import net.netconomy.tools.restflow.integrations.idea.ConsoleProcessManager;
import net.netconomy.tools.restflow.integrations.idea.Constants;
import net.netconomy.tools.restflow.integrations.idea.DiagnosticsDialog;
import net.netconomy.tools.restflow.integrations.idea.console.external.Interface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class RunRestFlowAction extends AnAction {

    public static final String TEXT = "Run RESTflow Script";
    public static final Icon ICON = Constants.RUN_ICON;

    private final Function<? super AnActionEvent, ? extends VirtualFile> virtualFileFun;
    private final Function<? super AnActionEvent, ? extends Editor> editorFun;

    public RunRestFlowAction(Function<? super AnActionEvent, ? extends VirtualFile> virtualFileFun,
                             Function<? super AnActionEvent, ? extends Editor> editorFun)
    {
        super(TEXT, null, Constants.RUN_ICON);
        this.virtualFileFun = virtualFileFun;
        this.editorFun = editorFun;
    }

    public RunRestFlowAction(VirtualFile virtualFile, Editor editor) {
        this(__ -> virtualFile, __ -> editor);
    }

    public RunRestFlowAction() {
        this(e -> CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext()),
                e -> CommonDataKeys.EDITOR.getData(e.getDataContext()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Context ctx = new Context(e);
        ctx.perform();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(new Context(e).performable());
    }

    public static void runRestFlow(Module module, Editor editor, VirtualFile virtualFile) {
        FileDocumentManager.getInstance().saveAllDocuments();
        Document document = editor.getDocument();
        //TextRange selectedRange = EditorUtil.getSelectionInAnyMode(editor);
        String script;
        //if (selectedRange.isEmpty()) {
        script = document.getText(); // whole document
        //} else {
        //    StringBuilder scriptBuilder = new StringBuilder();
        //    if (file instanceof GroovyFile) {
        //        GrImportStatement[] statements = ((GroovyFile)file).getImportStatements();
        //        for (GrImportStatement statement : statements) {
        //            if (!selectedRange.contains(statement.getTextRange())) {
        //                scriptBuilder.append(statement.getText()).append("\n");
        //            }
        //        }
        //    }
        //    scriptBuilder.append(document.getText(selectedRange));
        //    script = scriptBuilder.toString();
        //}
        runRestFlow(module, virtualFile.getUrl(), script);
    }

    public static void runRestFlow(Module module, VirtualFile virtualFile) {
        FileDocumentManager.getInstance().saveAllDocuments();
        try {
            runRestFlow(module, virtualFile.getUrl(), VfsUtil.loadText(virtualFile));
        } catch (IOException e) {
            DiagnosticsDialog.notifyError(module.getProject(), "I/O loading script from " + virtualFile.getUrl(), e);
        }
    }

    public static void runRestFlow(Module module, String uri, String script) {
        ConsoleProcessManager.get(module).uiStartIfNotRunning(po -> po.ifPresent(
                p -> {
                    try {
                        Interface.sendScript(uri, script, p.getProcessInput());
                    } catch (IOException ex) {
                        DiagnosticsDialog.notifyError(module.getProject(), "I/O error sending script to console", ex);
                    }
                }));
    }

    @SuppressWarnings("UnusedAssignment")
    private final class Context {

        @Nullable
        Project project = null;
        @Nullable
        Editor editor = null;
        @Nullable
        Module module = null;
        @Nullable
        VirtualFile file = null;
        @Nullable
        PsiFile psiFile = null;

        Context(AnActionEvent e) {
            project = e.getProject();
            editor = editorFun.apply(e);
            file = virtualFileFun.apply(e);
            if (project == null || editor == null || file == null) {
                return;
            }
            module = ModuleUtil.findModuleForFile(file, project);
            if (module == null) {
                return;
            }
            psiFile = e.getData(CommonDataKeys.PSI_FILE);
            if (psiFile == null) {
                psiFile = PsiUtil.getPsiFile(project, file);
            }
        }

        void perform() {
            if (performable()) {
                runRestFlow(module, editor, file);
            }
        }

        boolean performable() {
            return module != null && editor != null && file != null && psiFile != null;
        }
    }

}
