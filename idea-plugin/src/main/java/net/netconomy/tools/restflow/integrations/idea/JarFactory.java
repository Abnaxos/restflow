package net.netconomy.tools.restflow.integrations.idea;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import net.netconomy.tools.restflow.integrations.idea.console.external.Interface;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;


public final class JarFactory {

    private static final Logger LOG = Logger.getInstance(JarFactory.class);
    public static final String CONSOLE_JAR_NAME = "console.jar";

    private final Path jarsDir;

    private static final ClassLoader RES = Objects.requireNonNull(JarFactory.class.getClassLoader(),
            "JarFactory.class.getClassLoader()");

    private final Object jarsLock = new Object();
    private Path consoleJar = null;
    private volatile List<Path> restflowJars;

    JarFactory() {
        jarsDir = Paths.get(PathManager.getSystemPath()).resolve("restflow-jars").toAbsolutePath();
        ApplicationManager.getApplication().executeOnPooledThread(this::initRestflowGlobalLibraries);
    }

    public Path consoleJar() throws IOException {
        synchronized (jarsLock) {
            if (consoleJar == null || !Files.isRegularFile(consoleJar)) {
                Files.createDirectories(jarsDir);
                Path jarPath = jarsDir.resolve(CONSOLE_JAR_NAME);
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

    private void initRestflowGlobalLibraries() {
        synchronized (jarsLock) {
            Pair<Boolean, Set<Pair<OrderRootType, String>>> extractResult = extractShippedJars();
            boolean forceLibUpdate = extractResult.first;
            Set<Pair<OrderRootType, String>> roots = extractResult.second;
            Set<Pair<OrderRootType, String>> currentRoots = new HashSet<>();
            LibraryTablesRegistrar libraryRegistrar = LibraryTablesRegistrar.getInstance();
            Library current = ReadAction.compute(() -> {
                LibraryTable libs = libraryRegistrar.getLibraryTable();
                Library lib = libs.getLibraryByName(Constants.RESTFLOW_GLOBAL_LIBRARY);
                if (lib != null) {
                    for (OrderRootType t : OrderRootType.getAllTypes()) {
                        for (VirtualFile f : lib.getFiles(t)) {
                            currentRoots.add(new Pair<>(t, f.getUrl()));
                        }
                    }
                }
                return lib;
            });
            if (!forceLibUpdate && current != null && currentRoots.equals(roots)) {
                LOG.info(Constants.RESTFLOW_GLOBAL_LIBRARY + " global library up-to-date");
                return;
            }
            ApplicationManager.getApplication().invokeLater(
                    () -> WriteAction.run(() -> {
                        LOG.info("Updating " + Constants.RESTFLOW_GLOBAL_LIBRARY + " global library");
                        if (current != null) {
                            libraryRegistrar.getLibraryTable().removeLibrary(current);
                        }
                        Library.ModifiableModel lib = libraryRegistrar.getLibraryTable()
                                .createLibrary(Constants.RESTFLOW_GLOBAL_LIBRARY).getModifiableModel();
                        for (Pair<OrderRootType, String> root : roots) {
                            lib.addRoot(root.second, root.first);
                        }
                        lib.commit();
                    }));
        }
    }

    private Pair<Boolean, Set<Pair<OrderRootType, String>>> extractShippedJars() {
        try {
            Pair<String, Set<String>> versionAndIndex = loadIndex(
                    Objects.requireNonNull(RES.getResourceAsStream(Constants.RESTFLOW_JARS_INDEX),
                            "RES.getResourceAsStream(Constants.RESTFLOW_JARS_INDEX)"));
            String libDir = "lib." + versionAndIndex.first;
            Set<Path> jarIndex = versionAndIndex.second.stream()
                    .map(j -> jarsDir.resolve(libDir).resolve(j))
                    .collect(toSet());
            Files.createDirectories(jarsDir);
            String jarBaseUrl = "jar:///"
                    + stream(jarsDir.spliterator(), false)
                    .map(Path::toString)
                    .collect(joining("/"))
                    + "/" + libDir + "/";
            Set<Pair<OrderRootType, String>> roots = jarIndex.stream()
                    .map(j -> new Pair<>(
                            j.getFileName().toString().endsWith("-sources.jar")
                                    ? OrderRootType.SOURCES
                                    : OrderRootType.CLASSES,
                            "jar:///" + stream(j.spliterator(), false).map(Path::toString).collect(joining("/")) + "!/"))
                    .collect(toSet());
            Files.walkFileTree(jarsDir, new SimpleFileVisitor<Path>() {
                private final Set<Path> keepDirs = ImmutableSet.of(jarsDir.resolve(libDir), jarsDir);
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!jarIndex.contains(file)) {
                        LOG.info("Deleting file: " + file);
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            LOG.error("Error deleting file: " + file, e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (!keepDirs.contains(dir)) {
                        try {
                            LOG.info("Deleting directory: " + dir);
                            Files.delete(dir);
                        } catch (IOException e) {
                            LOG.error("Error deleting directory: " + dir);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    LOG.error("Error visiting path: " + file, exc);
                    return FileVisitResult.CONTINUE;
                }
            });
            boolean didExtract = false;
            Files.createDirectories(jarsDir.resolve(libDir));
            for (Path j : jarIndex) {
                if (!Files.isRegularFile(j)) {
                    didExtract = true;
                    byte[] buf = new byte[8192];
                    Path dest = jarsDir.resolve(j).toAbsolutePath();
                    LOG.info("Extracting: " + dest);
                    try (InputStream in = RES.getResourceAsStream(Constants.RESTFLOW_JARS_BASE + j.getFileName());
                         OutputStream out = Files.newOutputStream(dest))
                    {
                        int c;
                        //noinspection ConstantConditions
                        while ((c = in.read(buf)) >= 0) {
                            out.write(buf, 0, c);
                        }
                    }
                }
            }
            return new Pair<>(didExtract, roots);
        } catch (IOException e) {
            throw new RuntimeException("I/O error extracting RESTflow libraries: " + e, e);
        }
    }

    private Pair<String, Set<String>> loadIndex(InputStream inputStream) throws IOException {
        String version = null;
        try (InputStream in = inputStream) {
            Set<String> index = new HashSet<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charsets.UTF_8));
            String entry;
            while ((entry = reader.readLine()) != null) {
                entry = entry.trim();
                if (entry.isEmpty()) {
                    continue;
                }
                if (version == null) {
                    version = entry;
                } else {
                    index.add(entry);
                }
            }
            if (version == null || index.isEmpty()) {
                throw new IOException("Invalid index");
            }
            return new Pair<>(version, index);
        }
    }

}
