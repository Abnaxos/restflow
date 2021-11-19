package net.netconomy.tools.restflow.integrations.idea.lang;

import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;


public class RfScriptRunner extends DefaultGroovyScriptRunner {

    @Override
    public void configureCommandLine(JavaParameters params, @Nullable Module module, boolean tests, VirtualFile script, GroovyScriptRunConfiguration configuration) throws CantRunException {
        Objects.requireNonNull(module, "module");
        params.configureByModule(module, tests ? JavaParameters.CLASSES_AND_TESTS : JavaParameters.CLASSES_ONLY);
        params.getVMParametersList().addParametersString(configuration.getVMParameters());
        params.setMainClass("net.netconomy.tools.restflow.frontend.RestFlowRunner");
        params.getProgramParametersList().addAll("-profiles", "profiles"); // TODO (2019-08-22) configurable
        params.getProgramParametersList().add(configuration.getScriptPath());
        params.getProgramParametersList().addParametersString(configuration.getProgramParameters());
    }
}
