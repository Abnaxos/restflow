package net.netconomy.tools.restflow.impl

import net.netconomy.tools.restflow.dsl.RestFlow
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

class ProfileLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileLoader.class)

    static final PROFILES_PATH_PROPERTY = 'restflow.profiles'

    private static final FILEEXT = '.restflow'
    private static final AUTO = 'auto'
    private static final RESOURCE_PATH = 'META-INF/restflow'

    private final ClassLoader classLoader
    private final List<Path> profilesPath

    ProfileLoader(ClassLoader classLoader, List<Path> profilesPath = []) {
        this.classLoader = classLoader
        profilesPath = new ArrayList<>(profilesPath)
        def sysProfilepath = System.getProperty(PROFILES_PATH_PROPERTY)
        if (sysProfilepath) {
            profilesPath.addAll(0, sysProfilepath.split(Pattern.quote(File.pathSeparator)).collect {Paths.get(it)})
        }
        this.profilesPath = profilesPath.asImmutable()
    }

    void apply(RestFlow target, String name, Map<String, Object> args) {
        if (name == AUTO) {
            throw new IllegalArgumentException('Cannot apply profile \'auto\', use reset()')
        } else {
            def uri = null
            for (p in profilesPath) {
                def f = p.resolve("$name$FILEEXT")
                if (Files.isRegularFile(f)) {
                    uri = f.toUri()
                }
            }
            if (uri == null) {
                uri = classLoader.getResource("META-INF/restflow/${name}.restflow")?.toURI()
            }
            if (!uri) {
                throw new IllegalArgumentException("So such profile: $name")
            }
            doApply(uri, target, args)
        }
    }

    void applyAuto(RestFlow target) {
        def urls = profilesPath.
                collect {it.resolve("$AUTO$FILEEXT")}.
                findAll {Files.isRegularFile(it)}*.toUri()
        def res = Collections.<URL> list(classLoader.getResources("$RESOURCE_PATH/$AUTO$FILEEXT"))*.toURI()
        urls.addAll(res)
        urls.each {doApply(it, target, Collections.emptyMap())}
    }

    private void doApply(URI uri, RestFlow target, Map<String, Object> args) {
        target.log.debug('applying profile from', uri, 'with arguments', args)
        LOG.debug('Applying profile from {} with arguments {}', uri, args)
        def shell = RestFlowScripts.newGroovyShell(new RestFlowScripts.ReadOnlyBinding(), classLoader)
        def argsBinding = new RestFlowScripts.ReadOnlyBinding([args: (args ?: Collections.emptyMap())])
        RestFlowScripts.run(RestFlowScripts.parse(shell, argsBinding, target, uri))
    }
}
