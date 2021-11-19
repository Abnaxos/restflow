package net.netconomy.tools.restflow.impl

import groovy.swing.SwingBuilder

import java.awt.*

import javax.swing.*

/**
 * @since 2018-10-22
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
final class PasswordPrompt {

    private PasswordPrompt() {
    }

    static String swingPasswordPrompt(String username, String name) throws HeadlessException {
        //noinspection UnnecessaryQualifiedReference
        Window active = javax.swing.FocusManager.currentManager.activeWindow
        JPasswordField pw = null
        //noinspection GroovyUnusedAssignment
        def panel = new SwingBuilder().panel(layout: new GridBagLayout()) {
            label(text: "Login for $username ($name)", constraints: gbc(gridx: 0, gridy: 0, gridwidth: 2, fill: GridBagConstraints.HORIZONTAL))
            label(text: ' ', constraints: gbc(gridx: 0, gridy: 1, gridwidth: 2, fill: GridBagConstraints.HORIZONTAL))
//            label(text: 'Username ', constraints: gbc(gridx: 0, gridy: 2, fill: GridBagConstraints.HORIZONTAL, weightx: 0))
//            textField(text: 'foo', constraints: gbc(gridx: 1, gridy: 2, fill: GridBagConstraints.HORIZONTAL, weightx: 100))
            label(text: 'Password ', constraints: gbc(gridx: 0, gridy: 2, fill: GridBagConstraints.HORIZONTAL, weightx: 0))
            pw = passwordField(text: '', constraints: gbc(gridx: 1, gridy: 2, fill: GridBagConstraints.HORIZONTAL, weightx: 100))
        }
        def opt = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null) {
            @Override
            void selectInitialValue() {
                pw.requestFocusInWindow()
            }
        }
        def dialog = opt.createDialog(active, 'Authentication')
        dialog.setVisible(true)
        dialog.dispose()
        return opt.getValue() == JOptionPane.OK_OPTION ? new String(pw.getPassword()) : null
    }

    static String consolePasswordPrompt(String username, String name) throws IOException {
        System.console().with {
            printf "Login for %s (%s):%n", username, name
            def pw = readPassword()
            return pw != null ? new String(pw) : null
        }
    }

    static String nonInteractivePassword(String username, String name) {
        return System.getProperty("restflow.auth.$name.$username")
    }

}
