package net.netconomy.tools.restflow.integrations.idea.console;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import net.netconomy.tools.restflow.integrations.idea.ConsoleProcessManager;
import net.netconomy.tools.restflow.integrations.idea.lang.RfScriptType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class RfConsoleEditorHeaderProvider extends EditorNotifications.Provider<JComponent> {

    private static final Key<JComponent> KEY = Key.create(RfConsoleEditorHeaderProvider.class.getName());

    @NotNull
    @Override
    public Key<JComponent> getKey() {
        return KEY;
    }

    @Nullable
    @Override
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
        Module module = ModuleUtil.findModuleForFile(file, project);
        if (module == null) {
            return null;
        }
        if (!ConsoleProcessManager.get(module).determineRestFlowAvailable()) {
            return null;
        }
        DefaultActionGroup group = new DefaultActionGroup(
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
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(
                "RESTflow Console", group, true);
        EditorHeaderComponent editorHeader = new EditorHeaderComponent();
        panel.add(actionToolbar.getComponent(), BorderLayout.WEST);
        JPanel profilePathPanel = new JPanel(new BorderLayout(5, 5));
        JLabel profilePathLabel = new JLabel("Profile Paths");
        JTextField profilePathTextField = new JTextField();
        profilePathTextField.setEditable(false);
        profilePathTextField.setEnabled(false);
        ConsoleSettings settings = ConsoleProcessManager.get(module).settings();
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
        //profilePathTextField.setBackground(panel.getBackground());
        profilePathPanel.add(profilePathLabel, BorderLayout.WEST);
        TextFieldWithBrowseButton profilePathTextFieldBrowse = new TextFieldWithBrowseButton(profilePathTextField,
                e -> FileChooserFactory.getInstance().createPathChooser(
                        new FileChooserDescriptor(false, true, false, false, false, true)
                                .withTitle("Profile Paths")
                                .withDescription("Choose paths to load profiles from"),
                        project, null)
                        .choose(null, files -> settings.setProfilePaths(files.stream()
                                .filter(VirtualFile::isInLocalFileSystem)
                                .map(VirtualFile::getPath)
                                .collect(Collectors.toList()))));
        profilePathPanel.add(profilePathTextFieldBrowse, BorderLayout.CENTER);
        panel.add(profilePathPanel, BorderLayout.CENTER);
        editorHeader.add(panel);
        return editorHeader;
    }
}
