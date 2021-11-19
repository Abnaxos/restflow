package net.netconomy.tools.restflow.integrations.idea.lang;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTypesUtil;
import net.netconomy.tools.restflow.integrations.idea.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;


public class RestflowScriptContributor extends  NonCodeMembersContributor {

    @Override
    public void processDynamicElements(@NotNull PsiType qualifierType, PsiClass aClass,
                                       @NotNull PsiScopeProcessor processor,
                                       @NotNull PsiElement place, @NotNull ResolveState state) {
        if (!(aClass instanceof GroovyScriptClass)) {
            return;
        }
        PsiFile file = aClass.getContainingFile();
        if (file == null || !file.getName().endsWith(Constants.RESTFLOW_FILE_EXTENSION)) {
            return;
        }
        Module module = ModuleUtilCore.findModuleForFile(file);
        if (module == null) {
            return;
        }
        processAllDeclarations(aClass, processor, place, Constants.RESTFLOW_CLASS_NAME);
        //processAllDeclarations(aClass, processor, place, Constants.RESTFLOW_SCRIPT_CLASS_NAME);
    }

    private void processAllDeclarations(
            PsiClass aClass, @NotNull PsiScopeProcessor processor, @NotNull PsiElement place, String name) {
        PsiClass psiClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass(name, place.getResolveScope());
        if (psiClass != null) {
            PsiClassType type = PsiTypesUtil.getClassType(psiClass);
            ResolveUtil.processAllDeclarations(type, processor, false, place);
        }
    }
}
