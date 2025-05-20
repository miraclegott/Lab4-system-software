package org.storage;

public class FileEntry {
    Node node;
    int cursor = 0;

    public FileEntry(Node node) {
        this.node = node;
    }

    public byte[] extract(int size) {
        if (cursor >= node.size) {
            System.out.println("Read failed: Cursor is beyond file size.");
            return new byte[0];
        }

        size = Math.min(size, node.size - cursor);
        byte[] buffer = new byte[size];
        int bytesExtracted = 0;

        while (bytesExtracted < size) {
            int segmentIndex = cursor / FileSystem.SEGMENT_SIZE;
            int segmentOffset = cursor % FileSystem.SEGMENT_SIZE;
            byte[] segment = node.segments.get(segmentIndex);
            if (segment == null) {
                segment = new byte[FileSystem.SEGMENT_SIZE];
            }

            int bytesToExtract = Math.min(size - bytesExtracted, FileSystem.SEGMENT_SIZE - segmentOffset);
            System.arraycopy(segment, segmentOffset, buffer, bytesExtracted, bytesToExtract);

            bytesExtracted += bytesToExtract;
            cursor += bytesToExtract;
        }

        return buffer;
    }

    public void insert(byte[] data) {
        int bytesInserted = 0;

        while (bytesInserted < data.length) {
            int segmentIndex = cursor / FileSystem.SEGMENT_SIZE;
            int segmentOffset = cursor % FileSystem.SEGMENT_SIZE;

            if (!node.segments.containsKey(segmentIndex)) {
                if (FileSystem.assignedSegments >= FileSystem.MAX_SEGMENTS) {
                    System.out.println("No free segments available.");
                    return;
                }
                node.segments.put(segmentIndex, new byte[FileSystem.SEGMENT_SIZE]);
                FileSystem.assignedSegments++;
            }

            byte[] segment = node.segments.get(segmentIndex);
            int bytesToInsert = Math.min(data.length - bytesInserted, FileSystem.SEGMENT_SIZE - segmentOffset);

            System.arraycopy(data, bytesInserted, segment, segmentOffset, bytesToInsert);

            bytesInserted += bytesToInsert;
            cursor += bytesToInsert;
        }

        node.size = Math.max(node.size, cursor);
    }
}
