package net.netconomy.tools.restflow.frontend

import groovy.ui.Console
import net.netconomy.tools.restflow.dsl.RestFlow
import net.netconomy.tools.restflow.impl.ProfileLoader
import net.netconomy.tools.restflow.impl.RestFlowScripts
import org.codehaus.groovy.control.CompilationFailedException

import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import java.nio.file.Path
import java.util.prefs.Preferences


/**
 * @since 2018-10-18
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
class RestFlowConsole extends Console {

    static List<Path> profilePaths = []
    RestFlow restFlow

    RestFlowConsole(Binding binding = new Binding()) {
        this(null, binding)
    }

    RestFlowConsole(ClassLoader parent, Binding binding = new Binding()) {
        super(parent ?: RestFlowConsole.class.classLoader, binding)
        maxOutputChars = Integer.MAX_VALUE
    }

    @Override
    void newScript(ClassLoader parent, Binding binding) {
        restFlow = new RestFlow(new ProfileLoader(parent, profilePaths))
        config = RestFlowScripts.newCompilerConfiguration(threadInterrupt)
        shell = new GroovyShell(parent, binding, config) {
            @Override
            Object run(String scriptText, String fileName, List list) throws CompilationFailedException {
                RestFlowScripts.withShell(shell) {
                    RestFlowScripts.run(RestFlowScripts.parse(shell, restFlow, scriptText, fileName))
                }
            }
        }
    }

    def selectFilename(name = 'Open') {
        def fc = new JFileChooser(currentFileChooserDir)
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
        //noinspection GroovyAssignabilityCheck
        if (fc.showDialog(frame, name) == JFileChooser.APPROVE_OPTION) {
            currentFileChooserDir = fc.currentDirectory
            Preferences.userNodeForPackage(java.io.Console).put('currentFileChooserDir', currentFileChooserDir.path)
            return fc.selectedFile
        } else {
            return null
        }
    }

    private static newFileNameExtensionFilter(String description, String... extensions) {
        new FileNameExtensionFilter(description + ' (' + extensions.collect { '*.' + it }.join(', ') + ')', extensions)
    }

    static void main(String[] args) {
        CommandLine.setupSwing()
        def cmdLine = new CommandLine().readArgs(args)
        profilePaths.addAll cmdLine.profilePaths
        def console = new RestFlowConsole(RestFlowConsole.getClassLoader())
        console.useScriptClassLoaderForScriptExecution = true
        console.run()
        if (cmdLine.files) {
            console.loadScriptFile(cmdLine.files.first() as File)
        }
    }
}
