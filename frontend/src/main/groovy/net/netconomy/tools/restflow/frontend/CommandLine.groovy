package net.netconomy.tools.restflow.frontend

import javax.swing.UIManager
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

class CommandLine {

    final Closure<?> exit

    List<Path> profilePaths = []
    List<String> files = []

    CommandLine(Closure<?> exit = {System.exit(it as int)}) {
        this.exit = exit
    }

    static void setupSwing() {
        System.setProperty('awt.useSystemAAFontSettings', 'on')
        System.setProperty('swing.aatext', 'true')
        UIManager.setLookAndFeel(UIManager.crossPlatformLookAndFeelClassName)
        UIManager.put('swing.boldMetal', Boolean.FALSE)
    }

    private void usage() {
        System.err.println 'Options:'
        System.err.println " -profiles <profiles>   Paths to search for profiles spearated by $File.pathSeparator"
    }

    CommandLine readArgs(String[] args) {
        return readArgs(args as List)
    }

    CommandLine readArgs(List<String> args) {
        args = new ArrayList<>(args)
        while (args) {
            switch (args.first()) {
            case '-profiles':
                args.remove(0)
                if (!args) {
                    usage()
                    exit(1)
                }
                profilePaths.addAll(args.first().split(Pattern.quote(File.pathSeparator)).collect {Paths.get(it)})
                args.remove(0)
                break
            case '-help':
                usage()
                exit(0)
                break
            default:
                files.add args.first()
                args.remove(0)
            }
        }
        this
    }
}
