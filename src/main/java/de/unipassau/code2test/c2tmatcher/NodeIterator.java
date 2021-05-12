package de.unipassau.code2test.c2tmatcher;

import com.github.javaparser.ast.Node;

public class NodeIterator {

    public NodeIterator() {
    }

    public void explore(Node node) {
        System.out.print(node.toString());
        for (Node child : node.getChildNodes()) {
            explore(child);
        }
    }
}