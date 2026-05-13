package com.lvt4j.spider4videostation.ffmpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

class IOTransfer extends Thread {

    private InputStream in;
    private OutputStream out;
    private OutWrapper outWrapper;

    public IOTransfer(InputStream in, OutputStream out) {
        this(in, out, false);
    }

    public IOTransfer(InputStream in, OutputStream out, boolean cacheOut) {
        this.in = in;
        if (cacheOut) {
            this.out = this.outWrapper = new OutWrapper(out);
        } else {
            this.out = out;
        }
    }

    public byte[] getCachedOut() {
        return outWrapper.baos.toByteArray();
    }

    @Override
    @SneakyThrows
    public void run() {
        IOUtils.copy(in, out, 64);
    }

    @RequiredArgsConstructor
    class OutWrapper extends OutputStream {

        private final OutputStream out;
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            baos.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            out.write(b);
            baos.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            baos.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
            baos.flush();
        }

        @Override
        public void close() throws IOException {
            out.close();
            baos.close();
        }
    }
}
