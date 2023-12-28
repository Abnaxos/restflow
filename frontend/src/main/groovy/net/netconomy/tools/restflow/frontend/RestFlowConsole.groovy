package net.netconomy.tools.restflow.frontend

import groovy.console.ui.Console
import net.netconomy.tools.restflow.dsl.RestFlow
import net.netconomy.tools.restflow.impl.ProfileLoader
import net.netconomy.tools.restflow.impl.RestFlowScripts
import org.codehaus.groovy.control.CompilationFailedException

import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import java.awt.Component
import java.nio.file.Path
import java.util.prefs.Preferences


/**
 * @since 2018-10-18
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
class RestFlowConsole {

    static List<Path> profilePaths = []
    RestFlow restFlow
    final Console groovyConsole

    RestFlowConsole(Binding binding = new Binding()) {
        this(null, binding)
    }

    RestFlowConsole(ClassLoader parent, Binding binding = new Binding()) {
        groovyConsole = new Console(parent ?: getClass().getClassLoader(), binding)
        groovyConsole.maxOutputChars = Integer.MAX_VALUE
        installConsoleMethods()
        groovyConsole.newScript(parent, binding)
    }

    /**
     * We run into some access control problems with modern Java when extending
     * the console. As a workaround, we just use the metaClass to override the
     * methods we need to override.
     */
    private void installConsoleMethods() {
        groovyConsole.metaClass.newScript = {ClassLoader parent, Binding binding ->
            final self = (Console)delegate
            parent = parent ?: this.getClass().getClassLoader()
            restFlow = new RestFlow(new ProfileLoader(parent, profilePaths))
            final c = RestFlowScripts.newCompilerConfiguration(self.threadInterrupt)
            self.config = c
            self.shell = new GroovyShell(parent, binding, c) {

                @Override
                Object run(String scriptText, String fileName, List list) throws CompilationFailedException {
                    RestFlowScripts.withShell(this) {
                        RestFlowScripts.run(RestFlowScripts.parse(this, restFlow, scriptText, fileName))
                    }
                }
            }
        }
        groovyConsole.metaClass.selectFilename = {name = 'Open' ->
            final self = (Console)delegate
            final fc = new JFileChooser(self.currentFileChooserDir)
            fc.fileSelectionMode = JFileChooser.FILES_ONLY
            newFileNameExtensionFilter('RESTflow Script Files', 'restflow').with {
                fc.addChoosableFileFilter(it)
                fc.setFileFilter(it)
            }
            fc.addChoosableFileFilter(newFileNameExtensionFilter('Groovy Script Files', 'groovy', 'gvy', 'gy'))
            fc.acceptAllFileFilterUsed = true
            if(name == 'Save') {
                fc.selectedFile = new File('*.restflow')
            }
            if (fc.showDialog(self.frame as Component, name as String) == JFileChooser.APPROVE_OPTION) {
                self.currentFileChooserDir = fc.currentDirectory
                Preferences.userNodeForPackage(java.io.Console).put('currentFileChooserDir', self.currentFileChooserDir.path)
                return fc.selectedFile
            } else {
                return null
            }
        }
    }

    void run() {
        groovyConsole.run()
    }

    private static newFileNameExtensionFilter(String description, String... extensions) {
        new FileNameExtensionFilter(description + ' (' + extensions.collect { '*.' + it }.join(', ') + ')', extensions)
    }

    static void main(String[] args) {
        CommandLine.setupSwing()
        def cmdLine = new CommandLine().readArgs(args)
        profilePaths.addAll cmdLine.profilePaths
        def console = new RestFlowConsole(RestFlowConsole.getClassLoader())
        console.groovyConsole.useScriptClassLoaderForScriptExecution = true
        console.run()
        if (cmdLine.files) {
            console.groovyConsole.loadScriptFile(cmdLine.files.first() as File)
        }
    }
}
