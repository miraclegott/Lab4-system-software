package org.storage;

import java.util.HashMap;
import java.util.Map;

public class Node {
    enum NodeType { REGULAR, DIRECTORY }

    String name;
    int size = 0;
    int links = 1;
    NodeType nodeType;
    Map<Integer, byte[]> segments = new HashMap<>();

    Node(String name, NodeType nodeType) {
        this.name = name;
        this.nodeType = nodeType;
    }

    void increaseLinks() {
        links++;
    }

    void decreaseLinks() {
        links--;
    }

    int getLinks() {
        return links;
    }

    @Override
    public String toString() {
        return "Name: " + name + ", Size: " + size + ", Links: " + links + ", Type: " + nodeType;
    }
}
