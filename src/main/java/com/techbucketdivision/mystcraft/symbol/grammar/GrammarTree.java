package com.techbucketdivision.mystcraft.symbol.grammar;

import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Parse tree used to expand a (possibly partial) list of written symbols into a complete Age (REQUIREMENTS §4.4.3).
 * Faithful port of the original {@code GrammarTree}: written terminals are attached bottom-up via shortest rule paths,
 * orphaned subtrees are connected to the main tree, and every remaining unexpanded non-terminal is expanded randomly and
 * inserted next to the nearest written terminal.
 */
public final class GrammarTree {

    private static final class Node {
        final String token;
        final boolean isTerminal;
        @Nullable Rule selected;
        @Nullable Node parent;
        List<Node> children = new ArrayList<>();
        @Nullable Integer leftPos;
        @Nullable Integer rightPos;

        Node(String token) {
            this.token = token;
            this.isTerminal = false;
        }

        Node(String token, int pos) {
            this.token = token;
            this.leftPos = pos;
            this.rightPos = pos;
            this.isTerminal = true;
        }

        @Nullable Integer getLeftPosition() {
            if (leftPos != null) return leftPos;
            for (Node child : children) {
                Integer pos = child.getLeftPosition();
                if (pos == null) continue;
                if (leftPos == null || leftPos > pos) leftPos = pos;
            }
            if (leftPos != null) return leftPos;
            if (parent != null) {
                for (int i = 0; i < parent.children.size(); ++i) {
                    if (parent.children.get(i) == this) {
                        if (parent.children.size() > i + 1) {
                            leftPos = parent.children.get(i + 1).getLeftPosition();
                        }
                        break;
                    }
                }
            }
            return leftPos;
        }

        @Nullable Integer getRightPosition() {
            if (rightPos != null) return rightPos;
            for (Node child : children) {
                Integer pos = child.getRightPosition();
                if (pos == null) continue;
                if (rightPos == null || rightPos < pos) rightPos = pos;
            }
            if (rightPos != null) return rightPos;
            if (parent != null) {
                for (int i = parent.children.size() - 1; i >= 0; --i) {
                    Node sibling = parent.children.get(i);
                    if (sibling == this) {
                        if (i > 0) {
                            rightPos = parent.children.get(--i).getRightPosition();
                        }
                    } else {
                        Integer sp = sibling.getRightPosition();
                        if (rightPos != null && sp != null && rightPos < sp) rightPos = sp;
                    }
                }
            }
            return rightPos;
        }

        void addChild(Node child) {
            children.add(child);
            child.parent = this;
        }

        void addChild(int index, Node child) {
            children.add(index, child);
            child.parent = this;
        }

        @Override
        public String toString() {
            return token + (selected != null ? ":" : "") + (isTerminal ? "*" : "") + " (" + children.size() + ")";
        }
    }

    private final Node root;
    /** Unexpanded non-terminals; most recently added on top (index 0). */
    private final LinkedList<Node> unexplored = new LinkedList<>();
    /** Roots of orphaned subtrees. */
    private List<Node> subroots = new ArrayList<>();
    private List<String> terminals = List.of();

    public GrammarTree(String rootToken) {
        this.root = new Node(rootToken);
    }

    /** Builds the base forest from the written symbols (processed last to first). */
    public void parseTerminals(List<String> terminals, RandomSource rand) {
        this.terminals = List.copyOf(terminals);
        for (int i = terminals.size(); i > 0; --i) {
            buildSubtree(new Node(terminals.get(i - 1), i - 1), rand);
        }
    }

    /** Merges subtrees, expands the rest and returns the ordered symbol list. */
    public List<String> getExpanded(RandomSource rand) {
        List<String> out = new ArrayList<>(terminals);

        unexplored.clear();
        if (root.selected == null) unexplored.add(root);
        // Expand unexplored nodes while they have exactly one rule
        for (int i = 0; i < unexplored.size(); ++i) {
            List<Rule> rules = Grammar.rules(unexplored.get(i).token);
            if (rules != null && rules.size() == 1) {
                expandUnexploredNode(i--, rules.getFirst());
            }
        }
        // Connect every subroot to the main tree
        List<Node> failed = new ArrayList<>();
        while (!subroots.isEmpty()) {
            if (unexplored.isEmpty()) {
                failed.addAll(subroots);
                break;
            }
            Node subroot = subroots.removeFirst();
            if (!connectSubtreeShortest(subroot, rand)) failed.add(subroot);
        }
        subroots = failed;

        Map<Integer, List<String>> insertLeft = new HashMap<>();
        Map<Integer, List<String>> insertRight = new HashMap<>();
        getInsertions(root, insertLeft, insertRight);

        for (int i = out.size() + 1; i > 0; --i) {
            List<String> products = insertRight.get(i - 1);
            if (products != null) {
                for (int j = products.size(); j > 0; --j) {
                    out.addAll(i, Grammar.explore(products.get(j - 1), rand));
                }
            }
            products = insertLeft.get(i - 1);
            if (products != null) {
                for (int j = products.size(); j > 0; --j) {
                    out.addAll(i - 1, Grammar.explore(products.get(j - 1), rand));
                }
            }
        }
        unexplored.clear();
        return out;
    }

    private void getInsertions(Node node, Map<Integer, List<String>> insertLeft, Map<Integer, List<String>> insertRight) {
        for (Node child : node.children) getInsertions(child, insertLeft, insertRight);
        if (node.children.isEmpty() && node.selected == null && !node.isTerminal) {
            Integer left = node.getLeftPosition();
            if (left == null) {
                Integer right = node.getRightPosition();
                if (right == null) {
                    left = terminals.size();
                } else {
                    insertRight.computeIfAbsent(right, k -> new ArrayList<>()).add(node.token);
                }
            }
            if (left != null) {
                insertLeft.computeIfAbsent(left, k -> new ArrayList<>()).add(node.token);
            }
        }
    }

    /**
     * Attaches the node to any unexplored node via the shortest rule path, else reverse-expands upward while the token
     * has exactly one producing rule (stopping before the root) and keeps it as a detached subroot.
     */
    private void buildSubtree(Node subroot, RandomSource rand) {
        for (Node node : unexplored) {
            List<Rule> path = getShortestPath(subroot, node, rand);
            if (path != null) {
                for (Rule rule : path) subroot = reverseExpand(subroot, rule);
                replaceNodeWithTree(node, subroot);
                return;
            }
        }
        List<Rule> rules = Grammar.parentRules(subroot.token);
        while (rules.size() == 1) {
            if (rules.getFirst().parent().equals(root.token)) break;
            subroot = reverseExpand(subroot, rules.getFirst());
            rules = Grammar.parentRules(subroot.token);
        }
        subroots.add(subroot);
        addUnexploredNodes(subroot);
    }

    private @Nullable List<Rule> getShortestPath(Node subroot, Node node, RandomSource rand) {
        List<List<Rule>> paths = Grammar.shortestPaths(subroot.token, node.token);
        if (paths == null || paths.isEmpty()) return null;
        return Grammar.pickEvenly(rand, paths);
    }

    /**
     * Puts the subtree into the main tree: same-token match on an unexplored node, else a weighted direct parent rule
     * from an unexplored node, else the shortest path to one.
     */
    private boolean connectSubtreeShortest(Node subroot, RandomSource rand) {
        List<Rule> rules = Grammar.parentRules(subroot.token);
        if (rules.isEmpty()) return false;
        for (Node node : unexplored) {
            if (node.token.equals(subroot.token)) {
                replaceNodeWithTree(node, subroot);
                return true;
            }
            List<Rule> options = new ArrayList<>();
            for (Rule rule : rules) {
                if (node.token.equals(rule.parent())) options.add(rule);
            }
            if (!options.isEmpty() && Grammar.totalWeight(options, Rule::weight) > 0f) {
                Rule pick = Grammar.pickWeighted(rand, options, Rule::weight);
                if (pick != null) {
                    subroot = reverseExpand(subroot, pick);
                    replaceNodeWithTree(node, subroot);
                    return true;
                }
            }
            List<Rule> path = getShortestPath(subroot, node, rand);
            if (path != null) {
                for (Rule rule : path) subroot = reverseExpand(subroot, rule);
                replaceNodeWithTree(node, subroot);
                return true;
            }
        }
        return false;
    }

    private void replaceNodeWithTree(Node node, Node subroot) {
        unexplored.remove(node);
        node.selected = subroot.selected;
        node.children = subroot.children;
        for (Node child : node.children) child.parent = node;
        node.leftPos = null;
        node.rightPos = null;
        addUnexploredNodes(node);
    }

    /** Adds the unexpanded non-terminal descendants to the front of the unexplored list. */
    private void addUnexploredNodes(Node subroot) {
        LinkedList<Node> nodes = new LinkedList<>();
        nodes.add(subroot);
        while (!nodes.isEmpty()) {
            Node node = nodes.removeFirst();
            for (Node child : node.children) {
                if (child.selected != null) {
                    nodes.add(child);
                } else if (!child.isTerminal) {
                    unexplored.addFirst(child);
                }
            }
        }
    }

    private void expandUnexploredNode(int index, Rule rule) {
        Node node = unexplored.remove(index);
        node.selected = rule;
        List<String> products = rule.values();
        for (int i = products.size(); i > 0; --i) {
            Node created = new Node(products.get(i - 1));
            node.addChild(0, created);
            unexplored.add(index, created);
        }
    }

    /** Builds a new parent for {@code subroot} from the rule; the right-most matching token is replaced by it. */
    private Node reverseExpand(@Nullable Node subroot, Rule rule) {
        Node newRoot = new Node(rule.parent());
        newRoot.selected = rule;
        List<String> products = rule.values();
        for (int i = products.size(); i > 0; --i) {
            String product = products.get(i - 1);
            if (subroot != null && product.equals(subroot.token)) {
                newRoot.addChild(0, subroot);
                subroot = null;
            } else {
                newRoot.addChild(0, new Node(product));
            }
        }
        return newRoot;
    }

    /** Debug dump. */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        describe(root, ">", lines);
        lines.add("With " + subroots.size() + " subtrees");
        for (Node s : subroots) describe(s, "  >", lines);
        return Collections.unmodifiableList(lines);
    }

    private void describe(Node node, String prefix, List<String> out) {
        out.add(prefix + node + " " + node.getLeftPosition() + "|" + node.getRightPosition());
        for (Node child : node.children) describe(child, prefix + "  ", out);
    }
}
