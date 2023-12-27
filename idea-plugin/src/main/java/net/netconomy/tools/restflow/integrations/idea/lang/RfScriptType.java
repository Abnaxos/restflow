package net.netconomy.tools.restflow.integrations.idea.lang;

import java.util.Optional;

import javax.swing.Icon;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.extensions.GroovyRunnableScriptType;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;


public final class RfScriptType extends GroovyRunnableScriptType {

    public static final RfScriptType INSTANCE = new RfScriptType();

    public static final String SCRIPT_TYPE_ID = "RESTflow";
    public static final String FILE_EXTENSION = "restflow";

    private RfScriptType() {
        super(SCRIPT_TYPE_ID);
    }

    @Nullable
    @Override
    public GroovyScriptRunner getRunner() {
        return null;
    }

    @NotNull
    @Override
    public Icon getScriptIcon() {
        // TODO (2019-04-16) add a nice RESTflow icon
        return JetgroovyIcons.Groovy.GroovyFile;
    }

    public static boolean isRestFlowFile(VirtualFile file, Project project) {
        return Optional.of(file.getFileType())
          .filter(LanguageFileType.class::isInstance)
          .map(LanguageFileType.class::cast)
          .filter(t -> t.getLanguage().equals(GroovyLanguage.INSTANCE))
          .map(t -> {
              PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
              if (psiFile == null) {
                  return false;
              }
              return GroovyScriptUtil.isSpecificScriptFile(psiFile, RfScriptType.INSTANCE);
          })
          .orElse(false);
    }
}
