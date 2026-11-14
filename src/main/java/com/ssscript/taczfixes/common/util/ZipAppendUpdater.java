package com.ssscript.taczfixes.common.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public final class ZipAppendUpdater {
    private static final int EOCD = 0x06054b50;
    private static final int CENTRAL = 0x02014b50;
    private static final int LOCAL = 0x04034b50;

    private ZipAppendUpdater() {
    }

    public static boolean replaceEntry(Path zipFile, String entryName, byte[] data) throws Exception {
        byte[] name = entryName.getBytes(StandardCharsets.UTF_8);
        long oldSize;
        byte[] central;
        int entryCount;
        try (FileChannel channel = FileChannel.open(zipFile, StandardOpenOption.READ)) {
            oldSize = channel.size();
            if (oldSize <= 0 || oldSize > 0xFFFFFFFFL) return false;
            int tailSize = (int) Math.min(oldSize, 65557L);
            ByteBuffer tail = ByteBuffer.allocate(tailSize).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, tail, oldSize - tailSize);
            tail.flip();
            int eocd = -1;
            for (int i = tail.limit() - 22; i >= 0; i--) {
                if (tail.getInt(i) == EOCD) {
                    int commentLength = u16(tail, i + 20);
                    if (i + 22 + commentLength == tail.limit()) {
                        eocd = i;
                        break;
                    }
                }
            }
            if (eocd < 0) return false;
            entryCount = u16(tail, eocd + 10);
            long centralSize = u32(tail, eocd + 12);
            long centralOffset = u32(tail, eocd + 16);
            if (entryCount == 0xFFFF || centralSize == 0xFFFFFFFFL || centralOffset == 0xFFFFFFFFL) return false;
            if (centralSize > Integer.MAX_VALUE || centralOffset + centralSize > oldSize) return false;
            central = new byte[(int) centralSize];
            ByteBuffer centralBuffer = ByteBuffer.wrap(central);
            readFully(channel, centralBuffer, centralOffset);
        }

        ByteBuffer cd = ByteBuffer.wrap(central).order(ByteOrder.LITTLE_ENDIAN);
        ByteArrayOutputStream kept = new ByteArrayOutputStream(central.length + 128);
        boolean found = false;
        int parsed = 0;
        int position = 0;
        while (position + 46 <= cd.limit() && cd.getInt(position) == CENTRAL) {
            int nameLength = u16(cd, position + 28);
            int extraLength = u16(cd, position + 30);
            int commentLength = u16(cd, position + 32);
            int length = 46 + nameLength + extraLength + commentLength;
            if (position + length > cd.limit()) return false;
            boolean matches = nameLength == name.length && matches(central, position + 46, name);
            if (!matches) {
                kept.write(central, position, length);
            } else {
                found = true;
            }
            position += length;
            parsed++;
        }
        if (!found || parsed != entryCount) return false;

        byte[] compressed = deflate(data);
        CRC32 crc = new CRC32();
        crc.update(data);
        int method = compressed.length < data.length ? 8 : 0;
        byte[] payload = method == 8 ? compressed : data;
        long localOffset = oldSize;

        ByteArrayOutputStream local = new ByteArrayOutputStream(name.length + payload.length + 64);
        le32(local, LOCAL);
        le16(local, 20);
        le16(local, 0x0800);
        le16(local, method);
        le16(local, 0);
        le16(local, 0);
        le32(local, (int) crc.getValue());
        le32(local, payload.length);
        le32(local, data.length);
        le16(local, name.length);
        le16(local, 0);
        local.write(name);
        local.write(payload);
        byte[] localRecord = local.toByteArray();

        ByteArrayOutputStream appendedCentral = new ByteArrayOutputStream(kept.size() + 128);
        kept.writeTo(appendedCentral);
        le32(appendedCentral, CENTRAL);
        le16(appendedCentral, 20);
        le16(appendedCentral, 20);
        le16(appendedCentral, 0x0800);
        le16(appendedCentral, method);
        le16(appendedCentral, 0);
        le16(appendedCentral, 0);
        le32(appendedCentral, (int) crc.getValue());
        le32(appendedCentral, payload.length);
        le32(appendedCentral, data.length);
        le16(appendedCentral, name.length);
        le16(appendedCentral, 0);
        le16(appendedCentral, 0);
        le16(appendedCentral, 0);
        le16(appendedCentral, 0);
        le32(appendedCentral, 0);
        le32(appendedCentral, (int) localOffset);
        appendedCentral.write(name);
        byte[] newCentral = appendedCentral.toByteArray();

        ByteArrayOutputStream eocdBytes = new ByteArrayOutputStream(22);
        le32(eocdBytes, EOCD);
        le16(eocdBytes, 0);
        le16(eocdBytes, 0);
        le16(eocdBytes, entryCount);
        le16(eocdBytes, entryCount);
        le32(eocdBytes, newCentral.length);
        le32(eocdBytes, (int) (localOffset + localRecord.length));
        le16(eocdBytes, 0);

        ByteArrayOutputStream append = new ByteArrayOutputStream(localRecord.length + newCentral.length + 22);
        append.write(localRecord);
        append.write(newCentral);
        eocdBytes.writeTo(append);
        byte[] bytes = append.toByteArray();

        try (FileChannel channel = FileChannel.open(zipFile, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        return true;
    }

    private static boolean matches(byte[] data, int offset, byte[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) return false;
        }
        return true;
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 2));
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws Exception {
        while (buffer.hasRemaining()) {
            int count = channel.read(buffer, position + buffer.position());
            if (count < 0) throw new java.io.EOFException();
        }
    }

    private static int u16(ByteBuffer buffer, int offset) {
        return buffer.getShort(offset) & 0xFFFF;
    }

    private static long u32(ByteBuffer buffer, int offset) {
        return buffer.getInt(offset) & 0xFFFFFFFFL;
    }

    private static void le16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void le32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }
}
