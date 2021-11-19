package net.netconomy.tools.restflow.integrations.idea.console.external;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;


class LinePrefixOutputStream extends OutputStream {

    private final Object lock = new Object();
    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private final OutputStream out;
    private final int prefix;

    private boolean hadCr = false;
    private boolean prefixRequired = true;

    LinePrefixOutputStream(OutputStream out, int prefix) {
        this.out = out;
        this.prefix = prefix;
    }

    @Override
    public void write(int b) throws IOException {
        synchronized (lock) {
            if (prefixRequired) {
                buf.write(prefix);
                prefixRequired = false;
            }
            if (b == '\r') {
                hadCr = true;
                newline();
                return;
            }
            try {
                if (b == '\n') {
                    // ignore \n after \r; \r\n -> \n
                    if (!hadCr) {
                        newline();
                    }
                } else {
                    buf.write(b);
                }
            } finally {
                hadCr = false;
            }
        }
    }

    private void newline() throws IOException {
        buf.write('\n');
        prefixRequired = true;
        flush();
    }

    @Override
    public void flush() throws IOException {
        synchronized (lock) {
            buf.writeTo(out);
            buf.reset();
            out.flush();
        }
    }
}
