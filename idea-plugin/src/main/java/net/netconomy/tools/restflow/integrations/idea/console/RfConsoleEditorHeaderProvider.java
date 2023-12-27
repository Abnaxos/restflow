package net.netconomy.tools.restflow.integrations.idea.console;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationProvider;
import net.netconomy.tools.restflow.integrations.idea.ConsoleProcessManager;
import net.netconomy.tools.restflow.integrations.idea.lang.RfScriptType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RfConsoleEditorHeaderProvider implements EditorNotificationProvider {

    @Override
    public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project, @NotNull VirtualFile virtualFile) {
        return fileEditor -> createNotificationPanel(virtualFile, fileEditor, project);
    }

    @Nullable
    public JComponent createNotificationPanel(@NotNull VirtualFile file,
                                              @NotNull FileEditor fileEditor,
                                              @NotNull Project project) {
        if (!RfScriptType.isRestFlowFile(file, project)) {
            return null;
        }
        Editor editor = EditorUtil.getEditorEx(fileEditor);
        if (editor == null) {
            return null;
        }
        return new HeaderPanel(file, fileEditor, project, editor);
    }

    private static class HeaderPanel extends EditorHeaderComponent {
        private final JTextField profilePathTextField;
        private volatile ConsoleSettings settings = null;

        HeaderPanel(VirtualFile file, FileEditor fileEditor, Project project, Editor editor) {
            var application = ApplicationManager.getApplication();
            var actionGroup = new DefaultActionGroup(
              new RunRestFlowAction(file, editor)
              //new ToggleAction("Reset on run", "Reset the RESTflow before running", AllIcons.General.Reset) {
              //    @Override
              //    public boolean isSelected(@NotNull AnActionEvent e) {
              //        return mgr(e).map(m -> m.settings().isResetOnRun()).orElse(false);
              //    }
              //    @Override
              //    public void setSelected(@NotNull AnActionEvent e, boolean state) {
              //        mgr(e).ifPresent(m -> m.settings().setResetOnRun(state));
              //    }
              //    private Optional<ConsoleProcessManager> mgr(AnActionEvent e) {
              //        Project project = e.getProject();
              //        if (project == null) {
              //            return Optional.empty();
              //        }
              //        return Optional.ofNullable(ModuleUtil.findModuleForFile(file, project))
              //                .map(ConsoleProcessManager::get);
              //    }
              //}
            );
            var panel = new JPanel(new BorderLayout(5, 5));
            var actionToolbar = ActionManager.getInstance().createActionToolbar(
              "RESTflow Console", actionGroup, true);
            actionToolbar.setTargetComponent(fileEditor.getComponent());
            panel.add(actionToolbar.getComponent(), BorderLayout.WEST);
            var profilePathPanel = new JPanel(new BorderLayout(5, 5));
            var profilePathLabel = new JLabel("Profile Paths");
            profilePathTextField = new JTextField();
            profilePathTextField.setEditable(false);
            profilePathTextField.setEnabled(false);
            profilePathPanel.add(profilePathLabel, BorderLayout.WEST);
            TextFieldWithBrowseButton profilePathTextFieldBrowse = new TextFieldWithBrowseButton(profilePathTextField,
              e -> chooseProfilePath(project));
            profilePathPanel.add(profilePathTextFieldBrowse, BorderLayout.CENTER);
            panel.add(profilePathPanel, BorderLayout.CENTER);
            add(panel);
            application.executeOnPooledThread(() -> {
                // TODO (2023-12-26) make that header more informative
                // - "checking for RESTflow ..." (spinner)
                //   - (if available) enable and show stuff
                //   - (if not available) informative message
                var module = ModuleUtilCore.findModuleForFile(file, project);
                if (module == null || !ConsoleProcessManager.get(module).determineRestFlowAvailable()) {
                    return;
                }
                var settings = ConsoleProcessManager.get(module).settings();
                if (settings == null) {
                    return;
                }
                application.invokeLater(() -> setSettings(settings));
            });
        }

        void setSettings(ConsoleSettings settings) {
            this.settings = settings;
            profilePathTextField.setText(settings.getProfilePathsAsString());
            settings.addPropertyChangeListener(new PropertyChangeListener() {
                private final WeakReference<JTextField> textField = new WeakReference<>(profilePathTextField);
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    JTextField textField = this.textField.get();
                    if (textField == null) {
                        settings.removePropertyChangeListener(this);
                        return;
                    }
                    if (evt.getPropertyName().equals(ConsoleSettings.PROPERT_PROFILE_PATHS)) {
                        ApplicationManager.getApplication().invokeLater(
                          () -> textField.setText(settings.getProfilePathsAsString()));
                    }
                }
            });
        }

        void chooseProfilePath(Project project) {
            var settings = this.settings;
            if (settings == null) {
                return;
            }
            FileChooserFactory.getInstance().createPathChooser(
                new FileChooserDescriptor(false, true, false, false, false, true)
                  .withTitle("Profile Paths")
                  .withDescription("Choose paths to load profiles from"),
                project, null)
              .choose(null, files -> settings.setProfilePaths(files.stream()
                .filter(VirtualFile::isInLocalFileSystem)
                .map(VirtualFile::getPath)
                .collect(Collectors.toList())));
        }
    }
}
