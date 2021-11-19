package net.netconomy.tools.restflow.integrations.idea.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;


public class RfScriptTypeDetector extends GroovyScriptTypeDetector {

    private RfScriptTypeDetector() {
        super(RfScriptType.INSTANCE);
    }

    @Override
    public boolean isSpecificScriptFile(@NotNull GroovyFile script) {
        return script.getName().endsWith("." + RfScriptType.FILE_EXTENSION);
    }
}
