package com.netflix.ribbon.examples;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.http.nio.util.ExpandableBuffer;
import org.apache.http.nio.util.HeapByteBufferAllocator;

import com.google.common.collect.Lists;
import com.netflix.client.StreamDecoder;

public class SSEDecoder implements StreamDecoder<List<String>, ByteBuffer> {
    final ExpandableByteBuffer dataBuffer = new ExpandableByteBuffer();

    @Override
    public List<String> decode(ByteBuffer buf) throws IOException {
        List<String> result = Lists.newArrayList();
        while (buf.position() < buf.limit()) {
            byte b = buf.get();
            if (b == 10 || b == 13) {
                if (dataBuffer.hasContent()) {
                    result.add(new String(dataBuffer.getBytes(), "UTF-8"));
                }
                dataBuffer.reset();
            } else {
                dataBuffer.addByte(b);
            }
        }
        return result;
    }    
}

class ExpandableByteBuffer extends ExpandableBuffer {
    public ExpandableByteBuffer(int size) {
        super(size, HeapByteBufferAllocator.INSTANCE);
    }

    public ExpandableByteBuffer() {
        super(4 * 1024, HeapByteBufferAllocator.INSTANCE);
    }

    public void addByte(byte b) {
        if (this.buffer.remaining() == 0) {
            expand();
        }
        this.buffer.put(b);
    }

    public boolean hasContent() {
        return this.buffer.position() > 0;
    }

    public byte[] getBytes() {
        byte[] data = new byte[this.buffer.position()];
        this.buffer.position(0);
        this.buffer.get(data);
        return data;
    }

    public void reset() {
        clear();
    }

    public void consumeInputStream(InputStream content) throws IOException {
        try {
            int b = -1;
            while ((b = content.read()) != -1) {
                addByte((byte) b);
            }
        } finally {
            content.close();
        }
    }
}