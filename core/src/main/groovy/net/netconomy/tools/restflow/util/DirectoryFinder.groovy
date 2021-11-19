package net.netconomy.tools.restflow.util

import java.nio.file.Path
import java.nio.file.Paths


/**
 * Utility class for finding directories searching from the given directory
 * upwards the directory tree.
 */
class DirectoryFinder {

    final static CWD = Paths.get(System.getProperty('user.dir'))

    final Path origin

    DirectoryFinder(Path origin = CWD) {
        this.origin = origin
    }

    /**
     * Shortcut for {@link #find(groovy.lang.Closure) new DirectoryFinder(origin).find(check)}.
     */
    static Path findDirectory(Path origin = CWD, Closure<Boolean> check) {
        new DirectoryFinder(origin).find(check)
    }

    /**
     * Find the first directory from the origin upwards where the
     * <code>check</code> closure returns true.
     *
     * @param check  A closure that takes a path as argument and returns
     *               <code>true</code> if that path looks like the one we're
     *               looking for.
     *
     * @return The path.
     *
     * @throws NotFoundException If no matching directory could be found.
     */
    Path find(Closure<Boolean> check) {
        def path = origin
        while (true) {
            if (check.call(path)) {
                return path
            }
            def p = path.parent
            if (!p || path == p) {
                throw new NotFoundException("Could not find directory from originin $origin")
            }
            path = p
        }
    }

    static class NotFoundException extends IllegalStateException {
        NotFoundException() {
            super()
        }
        NotFoundException(String s) {
            super(s)
        }
        NotFoundException(String message, Throwable cause) {
            super(message, cause)
        }
        NotFoundException(Throwable cause) {
            super(cause)
        }
    }
}
