package net.netconomy.tools.restflow.frontend

import net.netconomy.tools.restflow.dsl.RestFlow
import net.netconomy.tools.restflow.impl.ProfileLoader
import net.netconomy.tools.restflow.impl.RestFlowScripts
import net.netconomy.tools.restflow.impl.RestFlowScripts.ReadOnlyBinding

import java.nio.file.Paths

class RestFlowRunner {

    static void main(String[] args) {
        CommandLine.setupSwing()
        def cmdLine = new CommandLine().readArgs(args)
        GroovyShell shell = RestFlowScripts.newGroovyShell(new ReadOnlyBinding(), RestFlowRunner.classLoader, false)
        RestFlow restFlow = new RestFlow(new ProfileLoader(RestFlowRunner.classLoader, cmdLine.profilePaths))
        for (f in cmdLine.files) {
            RestFlowScripts.run(RestFlowScripts.parse(shell, restFlow, Paths.get(f).toUri()))
        }
    }
}
