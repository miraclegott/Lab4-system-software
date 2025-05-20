package org.storage;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileSystem {
    private final Map<String, Node> catalog = new HashMap<>();
    private final Map<Integer, FileEntry> activeFiles = new HashMap<>();

    public static final BitSet allocationMap = new BitSet();
    public static final int SEGMENT_SIZE = 16;
    public static final int MAX_SEGMENTS = 1024;

    private int nodeCapacity = 50;
    private int nodeCount = 0;
    private int nextFileId = 0;

    public static int assignedSegments = 0;
    private static final int MAX_NAME_SIZE = 255;

    @FunctionalInterface
    interface Action {
        void execute();
    }

    public static void main(String[] args) {
        new FileSystem().run();
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            String[] tokens = parseArgs(input); // <- новий метод!
            if (tokens.length == 0) continue;

            String command = tokens[0];
            String[] arguments = Arrays.copyOfRange(tokens, 1, tokens.length);

            selectAction(command, arguments).execute();
        }
    }

    private String[] parseArgs(String input) {
        List<String> args = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(input);
        while (m.find()) {
            if (m.group(1) != null)
                args.add(m.group(1)); // текст у лапках
            else
                args.add(m.group(2)); // звичайне слово
        }
        return args.toArray(new String[0]);
    }

    private Action selectAction(String command, String[] args) {
        return switch (command) {
            case "stat" -> args.length >= 1 ? () -> showDetails(args[0]) : Helper::invalidAction;
            case "ls" -> this::listContents;
            case "create" -> args.length >= 1 ? () -> addFile(args[0]) : Helper::invalidAction;
            case "open" -> args.length >= 1 ? () -> startFile(args[0]) : Helper::invalidAction;
            case "close" -> args.length >= 1 && Helper.isNumber(args[0]) ? () -> endFile(Integer.parseInt(args[0])) : Helper::invalidAction;
            case "seek" -> args.length >= 2 && Helper.isNumber(args[0]) && Helper.isNumber(args[1]) ? () -> moveCursor(Integer.parseInt(args[0]), Integer.parseInt(args[1])) : Helper::invalidAction;
            case "read" -> args.length >= 2 && Helper.isNumber(args[0]) && Helper.isNumber(args[1]) ? () -> getData(Integer.parseInt(args[0]), Integer.parseInt(args[1])) : Helper::invalidAction;
            case "write" -> args.length >= 2 && Helper.isNumber(args[0]) ? () -> putData(Integer.parseInt(args[0]), args[1]) : Helper::invalidAction;
            case "link" -> args.length >= 2 ? () -> bindFile(args[0], args[1]) : Helper::invalidAction;
            case "unlink" -> args.length >= 1 ? () -> detachFile(args[0]) : Helper::invalidAction;
            case "mkfs" -> args.length == 1 && Helper.isNumber(args[0]) ? () -> initSystem(Integer.parseInt(args[0])) : Helper::invalidAction;
            case "truncate" -> args.length == 2 && Helper.isNumber(args[1]) ? () -> setLength(args[0], Integer.parseInt(args[1])) : Helper::invalidAction;
            default -> Helper::invalidAction;
        };
    }

    public void showDetails(String name) {
        Node node = catalog.get(name);
        if (node == null) {
            System.out.println("File not found: " + name);
            return;
        }
        System.out.println("File: " + node);
    }

    public void listContents() {
        System.out.println("Directory contents:");
        for (Map.Entry<String, Node> entry : catalog.entrySet()) {
            System.out.println(entry.getValue());
        }
    }

    public void initSystem(int count) {
        if (count <= 0 || count > nodeCapacity) {
            System.out.println("Invalid number of nodes. Must be between 1 and " + nodeCapacity + ".");
            return;
        }
        catalog.clear();
        activeFiles.clear();
        allocationMap.clear();
        nextFileId = 0;
        nodeCount = 0;
        nodeCapacity = count;
        System.out.println("File system initialized with " + count + " nodes.");
    }

    public void addFile(String name) {
        try {
            if (name.length() > MAX_NAME_SIZE) {
                throw new IllegalArgumentException("File name too long: " + name);
            }
            if (catalog.containsKey(name)) {
                throw new IllegalStateException("File already exists: " + name);
            }
            if (nodeCount >= nodeCapacity) {
                throw new IllegalStateException("Cannot create file: Maximum nodes reached.");
            }
            catalog.put(name, new Node(name, Node.NodeType.REGULAR));
            nodeCount++;
            System.out.println("File created: " + name);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public void startFile(String name) {
        try {
            Node node = catalog.get(name);
            if (node == null) {
                throw new IllegalStateException("File not found: " + name);
            }
            if (nextFileId == nodeCapacity) {
                throw new IllegalStateException("Too many open files");
            }
            activeFiles.put(nextFileId, new FileEntry(node));
            System.out.println("File opened: " + name + " with FD: " + nextFileId);
            nextFileId++;
        } catch (IllegalStateException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public void endFile(int fd) {
        if (!activeFiles.containsKey(fd)) {
            System.out.println("Invalid FD: " + fd);
            return;
        }
        activeFiles.remove(fd);
        System.out.println("File closed with FD: " + fd);
    }

    public void moveCursor(int fd, int offset) {
        FileEntry entry = activeFiles.get(fd);
        if (entry == null) {
            System.out.println("Invalid FD: " + fd);
            return;
        }
        if (offset < 0 || offset > entry.node.size) {
            System.out.println("Seek failed: Offset out of bounds.");
            return;
        }
        entry.cursor = offset;
        System.out.println("Seek set to " + offset + " for FD: " + fd);
    }

    public void getData(int fd, int size) {
        FileEntry entry = activeFiles.get(fd);
        if (entry == null) {
            System.out.println("Invalid FD: " + fd);
            return;
        }
        byte[] data = entry.extract(size);
        if (data.length == 0) {
            System.out.println("Read failed or no data available.");
        } else {
            System.out.println("Read data: \"" + new String(data, StandardCharsets.UTF_8).trim() + "\"");
        }
    }

    public void putData(int fd, String content) {
        try {
            FileEntry entry = activeFiles.get(fd);
            if (entry == null) {
                throw new IllegalStateException("Invalid FD: " + fd);
            }
            entry.insert(content.getBytes(StandardCharsets.UTF_8));
            System.out.println("Written data: \"" + content + "\"");
        } catch (IllegalStateException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public void bindFile(String source, String target) {
        Node node = catalog.get(source);
        if (node == null) {
            System.out.println("File not found: " + source);
            return;
        }
        catalog.put(target, node);
        node.increaseLinks();
        System.out.println("Hard link created: " + target);
    }

    public void detachFile(String name) {
        try {
            Node node = catalog.get(name);
            if (node == null) {
                throw new IllegalStateException("File not found: " + name);
            }
            catalog.remove(name);
            node.decreaseLinks();
            if (node.getLinks() == 0) {
                System.out.println("File unlinked: " + name + ". File will be deleted if no descriptors remain open.");
            } else {
                System.out.println("File unlinked: " + name + ". File still exists due to remaining links.");
            }
        } catch (IllegalStateException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public void setLength(String name, int newSize) {
        try {
            if (newSize < 0) {
                throw new IllegalArgumentException("New size cannot be negative.");
            }
            Node node = catalog.get(name);
            if (node == null) {
                throw new IllegalStateException("File not found: " + name);
            }

            int currentSize = node.size;
            int currentSegmentCount = (currentSize + SEGMENT_SIZE - 1) / SEGMENT_SIZE;
            int newSegmentCount = (newSize + SEGMENT_SIZE - 1) / SEGMENT_SIZE;

            if (newSize < currentSize) {
                for (int i = newSegmentCount; i < currentSegmentCount; i++) {
                    allocationMap.clear(i);
                    node.segments.remove(i);
                }
            } else if (newSize > currentSize) {
                for (int i = currentSegmentCount; i < newSegmentCount; i++) {
                    if (assignedSegments >= MAX_SEGMENTS) {
                        throw new IllegalStateException("No free segments available.");
                    }
                    node.segments.put(i, new byte[SEGMENT_SIZE]);
                    assignedSegments++;
                }

                int lastSegmentIndex = currentSegmentCount - 1;
                if (lastSegmentIndex >= 0 && currentSize % SEGMENT_SIZE != 0) {
                    int lastSegmentOffset = currentSize % SEGMENT_SIZE;
                    byte[] lastSegment = node.segments.get(lastSegmentIndex);
                    if (lastSegment == null) {
                        lastSegment = new byte[SEGMENT_SIZE];
                        node.segments.put(lastSegmentIndex, lastSegment);
                    }
                    Arrays.fill(lastSegment, lastSegmentOffset, SEGMENT_SIZE, (byte) 0);
                }
            }

            node.size = newSize;
            System.out.println("File truncated: " + name + " to size " + newSize);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}