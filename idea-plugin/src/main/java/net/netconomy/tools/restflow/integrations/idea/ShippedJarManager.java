package net.netconomy.tools.restflow.integrations.idea;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VfsUtil;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import net.netconomy.tools.restflow.integrations.idea.console.external.Interface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@Service(Service.Level.APP)
public final class ShippedJarManager {

    private static final Logger LOG = Logger.getInstance(ShippedJarManager.class);
    public static final String CONSOLE_JAR_NAME = "console.jar";

    private final Path jarsBaseDir;
    private final Path jarsIndexFile;
    private final Path classesJarsDir;
    private final Path sourcesJarsDir;
    private Index shippedIndex = null;

    private static final ClassLoader RES = Objects.requireNonNull(ShippedJarManager.class.getClassLoader(),
            "JarFactory.class.getClassLoader()");

    private final Object jarsLock = new Object();
    private Path consoleJar = null;

    ShippedJarManager() {
        jarsBaseDir = Paths.get(PathManager.getSystemPath()).resolve("restflow-jars").toAbsolutePath();
        jarsIndexFile = jarsBaseDir.resolve(Constants.RESTFLOW_JARS_INDEX_NAME);
        classesJarsDir = jarsBaseDir.resolve("classes");
        sourcesJarsDir = jarsBaseDir.resolve("sources");
        //ApplicationManagerEx.getApplicationEx().runWriteAction(this::initRestflowGlobalLibraries);
    }

    public static ShippedJarManager get(@SuppressWarnings("unused") Module module) {
        return get();
    }

    private static ShippedJarManager get() {
        return ApplicationManager.getApplication().getService(ShippedJarManager.class);
    }

    public Path consoleJar() throws IOException {
        synchronized (jarsLock) {
            if (consoleJar == null || !Files.isRegularFile(consoleJar)) {
                Files.createDirectories(classesJarsDir);
                Path jarPath = classesJarsDir.resolve(CONSOLE_JAR_NAME);
                try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
                    for (String classFile : Interface.CLASS_FILES) {
                        try (InputStream in = Objects.requireNonNull(
                                RES.getResourceAsStream(classFile), "resource " + classFile))
                        {
                            ZipEntry entry = new ZipEntry(classFile);
                            entry.setLastModifiedTime(FileTime.from(Instant.now()));
                            jar.putNextEntry(entry);
                            byte[] buf = new byte[8192];
                            int c;
                            while ((c = in.read(buf)) >= 0) {
                                jar.write(buf, 0, c);
                            }
                        }
                    }
                }
                this.consoleJar = jarPath;
            }
            return consoleJar;
        }
    }

    private void initShippedJars() {
        try {
            if (shippedIndex == null) {
                try (var stream = new BufferedInputStream(
                  Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(Constants.RESTFLOW_JARS_INDEX),
                    "Resource " + Constants.RESTFLOW_JARS_INDEX)))
                {
                    shippedIndex = loadIndexFile(stream);
                }
            }
            boolean didUpdate = false;
            if (!checkExtractedJars(shippedIndex)) {
                didUpdate = true;
                extractShippedJars(shippedIndex);
            }
            if (initRestflowGlobalLibrary()) {
                didUpdate = true;
            }
            if (didUpdate) {
                //noinspection DialogTitleCapitalization
                Notifications.Bus.notify(new Notification(Constants.RESTFLOW_JAR_MANAGER_NOTIFICATION_GROUP,
                  "RESTflow Global Library",
                  "The RESTflow global library has been updated", NotificationType.INFORMATION));
            }
        } catch (Exception e) {
            setupError(e);
        }
    }

    private boolean initRestflowGlobalLibrary() {
        synchronized (jarsLock) {
            LibraryTablesRegistrar libraryRegistrar = LibraryTablesRegistrar.getInstance();
            Library current = ReadAction.compute(
              () -> libraryRegistrar.getLibraryTable().getLibraryByName(Constants.RESTFLOW_GLOBAL_LIBRARY));
            if (current == null) {
                ApplicationManager.getApplication().invokeLater(() -> {
                  try {
                    WriteAction.run(() -> {
                        var libRo = libraryRegistrar.getLibraryTable().createLibrary(Constants.RESTFLOW_GLOBAL_LIBRARY);
                        var lib = libRo.getModifiableModel();
                        //noinspection DataFlowIssue
                        lib.addJarDirectory(VfsUtil.findFile(Files.createDirectories(classesJarsDir), true),
                          false, OrderRootType.CLASSES);
                        //noinspection DataFlowIssue
                        lib.addJarDirectory(VfsUtil.findFile(Files.createDirectories(sourcesJarsDir), true),
                          false, OrderRootType.SOURCES);
                        lib.commit();
                    });
                  } catch (IOException e) {
                      setupError(e);
                  }
                });
                return true;
            } else {
                return false;
            }
        }
    }

    private void extractShippedJars(Index index) throws IOException {
        synchronized (jarsLock) {
            Files.walkFileTree(jarsBaseDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!dir.equals(jarsBaseDir)) {
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            for (var file : index.paths()) {
                var target = jarsBaseDir.resolve(file);
                var resource = Constants.RESTFLOW_JARS_BASE + toSlashedPath(file);
                try (var inStream = getClass().getClassLoader().getResourceAsStream(resource)) {
                    if (inStream == null) {
                        throw new FileNotFoundException("resource " + resource);
                    }
                    Files.createDirectories(target.getParent());
                    Files.copy(inStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            try (var out = Files.newBufferedWriter(jarsIndexFile, StandardCharsets.UTF_8)) {
                out.write(index.version);
                out.write("\n");
                for (var p: index.paths) {
                    out.write(toSlashedPath(p));
                    out.write("\n");
                }
            }
        }
    }

    private String toSlashedPath(Path path) {
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (var f : path) {
            if (!first) {
                buf.append('/');
            }
            first = false;
            buf.append(f);
        }
        return buf.toString();
    }

    private Index loadIndexFile(InputStream inputStream) throws IOException {
        String version = null;
        try (InputStream in = inputStream) {
            Set<Path> files = new LinkedHashSet<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String entry;
            while ((entry = reader.readLine()) != null) {
                entry = entry.trim();
                if (entry.isEmpty()) {
                    continue;
                }
                if (version == null) {
                    version = entry;
                } else {
                    files.add(Path.of(entry.replace('/', File.separatorChar)));
                }
            }
            if (version == null || files.isEmpty()) {
                throw new IOException("Invalid index");
            }
            return new Index(version, files);
        }
    }

    private boolean checkExtractedJars(Index expectedIndex) throws IOException {
        synchronized (jarsLock) {
            if (!Files.isDirectory(jarsBaseDir)) {
                return false;
            }
            Path indexPath = jarsBaseDir.resolve(Constants.RESTFLOW_JARS_INDEX_NAME);
            if (!Files.isRegularFile(indexPath)) {
                return false;
            }
            Index index;
            try (var stream = new BufferedInputStream(Files.newInputStream(indexPath))) {
                index = loadIndexFile(stream);
            }
            if (!index.equals(expectedIndex)) {
                return false;
            }
            //FileTime indexLastModified = Files.getLastModifiedTime(indexPath);
            boolean[] upToDate = {true};
            Files.walkFileTree(jarsBaseDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    var relFile = jarsBaseDir.relativize(file);
                    if (index.paths().contains(relFile) || file.equals(jarsIndexFile)) {
                        // || Files.getLastModifiedTime(file).compareTo(indexLastModified) > 0
                        return FileVisitResult.CONTINUE;
                    } else {
                        upToDate[0] = false;
                        return FileVisitResult.TERMINATE;
                    }
                }
            });
            return upToDate[0];
        }
    }

    private void setupError(Throwable exception) {
        LOG.error("Error setting up RESTflow global library", exception);
        //noinspection DialogTitleCapitalization
        Notifications.Bus.notify(new Notification(Constants.RESTFLOW_JAR_MANAGER_NOTIFICATION_GROUP,
          "RESTflow Global Library",
          "An error occurred updating RESTflow global library (see IDE Errors for details).", NotificationType.ERROR));
    }

    private record Index(String version, Set<Path> paths) {}

    public static final class Updater implements ProjectActivity {
        @Nullable
        @Override
        public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
            ShippedJarManager.get().initShippedJars();
            return null;
        }
    }
}
