package net.netconomy.tools.restflow.integrations.idea;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.util.io.BaseOutputReader;
import net.netconomy.tools.restflow.integrations.idea.console.ConsoleSettings;
import net.netconomy.tools.restflow.integrations.idea.console.RfConsoleView;
import net.netconomy.tools.restflow.integrations.idea.console.external.Interface;
import org.jetbrains.annotations.NotNull;


@State(name = "RESTflow.ConsoleProcessManager")
public class ConsoleProcessManager implements PersistentStateComponent<ConsoleSettings> {

    private static final Logger LOG = LoggerFactory.getLogger(ConsoleProcessManager.class);

    private static final int DEBUG_PORT = 0;

    private static final ConsoleSettings DEFAULT_CONSOLE_SETTINGS = new ConsoleSettings();

    private final Project project;
    private final Module module;

    private final ConsoleSettings settings = new ConsoleSettings();
    private final Object consoleLock = new Object();
    @Nullable
    private OSProcessHandler consoleProcess = null;
    @Nullable
    private RfConsoleView consoleView = null;

    public ConsoleProcessManager(Project project, Module module) {
        this.project = project;
        this.module = module;
    }

    public static ConsoleProcessManager get(Module module) {
        return module.getComponent(ConsoleProcessManager.class);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean determineRestFlowAvailable() {
        Application app = ApplicationManager.getApplication();
        Computable<Boolean> computable = () -> JavaPsiFacade.getInstance(project).findClass(
                Constants.RESTFLOW_CLASS_NAME, module.getModuleWithDependenciesAndLibrariesScope(true))
                != null;
        if (app.isReadAccessAllowed()) {
            return computable.compute();
        } else {
            return app.runReadAction(computable);
        }
    }

    public OSProcessHandler startIfNotRunning() throws ExecException {
        synchronized (consoleLock) {
            if (consoleProcess != null) {
                Runnable bringToFront = () -> {
                    synchronized (consoleLock) {
                        if (consoleView != null) {
                            consoleView.bringToFront();
                        }
                    }
                };
                Application app = ApplicationManager.getApplication();
                if (app.isDispatchThread()) {
                    bringToFront.run();
                } else {
                    app.invokeLater(bringToFront);
                }
            } else {
                startConsole();
            }
            return consoleProcess;
        }
    }

    public void uiStartIfNotRunning() {
        uiStartIfNotRunning(__ -> {});
    }

    public void uiStartIfNotRunning(Consumer<Optional<OSProcessHandler>> consumer) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> uiStartIfNotRunning(consumer));
        } else {
            try {
                consumer.accept(Optional.of(startIfNotRunning()));
            } catch (ExecException e) {
                DiagnosticsDialog.notifyError(project, "Error starting RESTflow console: " + e.getMessage(), e);
                consumer.accept(Optional.empty());
            }
        }
    }

    private void startConsole() throws ExecException {
        var app = ApplicationManager.getApplication();
        if (!app.isReadAccessAllowed()) {
            app.<Void, ExecException>runReadAction(() -> {
                startConsole();
                return null;
            });
            return;
        }
        if (!determineRestFlowAvailable()) {
            throw new ExecException("RESTflow is not in the classpath of module " + module.getName());
        }
        Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
            throw new ExecException(
                    (sdk != null ? "Expected Java SDK" : " SDK is not configured")
                            + " for project " + project.getName());
        }
        JavaParameters javaParams = new JavaParameters();
        try {
            javaParams.configureByModule(module, JavaParameters.CLASSES_AND_TESTS);
            javaParams.setJdk(JavaParametersUtil.createModuleJdk(module, false, null));
        } catch (CantRunException e) {
            throw new ExecException(e.getMessage(), e);
        }
        try {
            javaParams.getClassPath().addFirst(ApplicationManager.getApplication().getComponent(JarFactory.class)
                    .consoleJar().toString());
        } catch (IOException e) {
            throw new ExecException("Cannot create console.jar", e);
        }
        List<File> localRoots = localRootsOf(ModuleRootManager.getInstance(module).getContentRoots());
        if (localRoots.isEmpty()) {
            LOG.warn("No local module content roots found for module" + project.getName() + "/" + module.getName()
                    + "; falling back to project content roots");
            localRoots = localRootsOf(ProjectRootManager.getInstance(project).getContentRoots());
        }
        if (localRoots.isEmpty()) {
            LOG.warn("No local content roots found in either project nor module: "
                    + project.getName() + "/" + module.getName());
        } else {
            if (localRoots.size() > 1) {
                LOG.warn("More than one local content root found, using the first one: " + localRoots);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using working directory: " + localRoots.get(0));
            }
            javaParams.setWorkingDirectory(localRoots.get(0));
        }
        javaParams.setCharset(Interface.CHARSET);
        javaParams.setMainClass(Interface.CONSOLE_MAIN_CLASS);
        //javaParams.getProgramParametersList().add(Interface.ARG_ECHO_SCRIPT);
        String profilePaths = settings.getProfilePathsAsString();
        if (!profilePaths.isEmpty()) {
            javaParams.getProgramParametersList().add("-profiles");
            javaParams.getProgramParametersList().add(profilePaths);
        }
        if (DEBUG_PORT > 0) {
            javaParams.getVMParametersList().add(
                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + DEBUG_PORT);
        }
        OSProcessHandler processHandler;
        try {
            processHandler = new OSProcessHandler(javaParams.toCommandLine()) {
                @NotNull
                @Override
                protected BaseOutputReader.Options readerOptions() {
                    return BaseOutputReader.Options.forMostlySilentProcess();
                }

                @Override
                public boolean isSilentlyDestroyOnClose() {
                    return true;
                }
            };
        } catch (ExecutionException e) {
            throw new ExecException(e.getMessage(), e);
        }
        processHandler.addProcessListener(new ProcessAdapter() {
            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
                consoleTerminated();
            }
        });
        this.consoleProcess = processHandler;
        ApplicationManager.getApplication().invokeLater(() -> {
            RfConsoleView view = RfConsoleView.createView(project, module, processHandler);
            synchronized (consoleLock) {
                consoleView = view;
            }
            view.bringToFront();
        });
    }

    @NotNull
    private List<File> localRootsOf(VirtualFile[] contentRoots) {
        return Stream.of(contentRoots)
                .filter(VirtualFile::isInLocalFileSystem)
                .map(VfsUtil::virtualToIoFile)
                .collect(Collectors.toList());
    }

    public void stop(Runnable onStop) {
        synchronized (consoleLock) {
            if (consoleProcess != null) {
                AtomicReference<Runnable> onStopRef = new AtomicReference<>(onStop);
                Runnable callOnStop = () -> {
                    consoleTerminated();
                    Runnable r = onStopRef.getAndSet(null);
                    if (r != null) {
                        r.run();
                    }
                };
                consoleProcess.addProcessListener(new ProcessAdapter() {
                    @Override
                    public void processTerminated(@NotNull ProcessEvent event) {
                        callOnStop.run();
                    }
                });
                if (consoleProcess.isProcessTerminated()) {
                    callOnStop.run();
                } else {
                    consoleProcess.destroyProcess();
                }
            } else {
                onStop.run();
            }
        }
    }

    private void consoleTerminated() {
        synchronized (consoleLock) {
            consoleProcess = null;
        }
    }

    public void stop() {
        stop(() -> {});
    }

    public ConsoleSettings settings() {
        return settings;
    }

    @Override
    public ConsoleSettings getState() {
        if (DEFAULT_CONSOLE_SETTINGS.equals(settings)) {
            return null;
        } else {
            return new ConsoleSettings(settings);
        }
    }

    @Override
    public void loadState(@NotNull ConsoleSettings state) {
        settings.load(state);
    }

    public static class ExecException extends Exception {

        public ExecException() {
        }

        public ExecException(String message) {
            super(message);
        }

        public ExecException(String message, Throwable cause) {
            super(message, cause);
        }

        public ExecException(Throwable cause) {
            super(cause);
        }
    }
}
