package net.netconomy.tools.restflow.integrations.idea.console;

import javax.swing.Icon;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import net.netconomy.tools.restflow.integrations.idea.Constants;
import net.netconomy.tools.restflow.integrations.idea.lang.RfScriptType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;


@SuppressWarnings("IntentionDescriptionNotFoundInspection")
public class RunScriptIntentionAction extends BaseIntentionAction implements Iconable, PriorityAction {

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        if (psiFile.getVirtualFile() == null || ModuleUtilCore.findModuleForFile(psiFile) == null) {
            return false;
        }
        return GroovyScriptUtil.isSpecificScriptFile(psiFile, RfScriptType.INSTANCE);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
        VirtualFile file = psiFile.getVirtualFile();
        if (file == null) {
            return;
        }
        Module module = ModuleUtilCore.findModuleForFile(psiFile);
        if (module == null) {
            return;
        }
        RunRestFlowAction.runRestFlow(module, editor, file);
    }

    @NotNull
    @Override
    public String getText() {
        return RunRestFlowAction.TEXT;
    }

    @Override
    public Icon getIcon(int flags) {
        return RunRestFlowAction.ICON;
    }

    @NotNull
    @Override
    public Priority getPriority() {
        return Priority.TOP;
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
        return Constants.RESTFLOW_RUN_INTENTION_FAMILY;
    }
}
