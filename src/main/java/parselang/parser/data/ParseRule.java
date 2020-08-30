package parselang.parser.data;

import parselang.util.Sanitizer;

import java.util.*;

import static parselang.parser.ParseRuleStorage.bound;
import static parselang.parser.ParseRuleStorage.nonTerm;

public class ParseRule {

    private final NonTerminal lhs;
    private List<Node> rhs = new LinkedList<>();
    private ParseRule origin = this;

    public ParseRule(NonTerminal lhs) {
        this.lhs = lhs;
    }

    public ParseRule(String lhs) {
        this.lhs = new NonTerminal(lhs);
    }

    public NonTerminal getLHS() {
        return lhs;
    }

    public ParseRule addRhs(Node node) {
        rhs.add(node);
        return this;
    }

    public ParseRule addRhs(Node... nodes) {
        rhs.addAll(Arrays.asList(nodes));
        return this;
    }

    public List<Node> getRHS() {
        return rhs;
    }



    public String toString() {
        StringBuilder sb = new StringBuilder(lhs.toString());
        sb.append(" = ");
        for (Node node : rhs) {
            sb.append(Sanitizer.replaceSpecials(node.toString())).append(" ");
        }
        return sb.toString();
    }

    public ParseRule getOrigin() {
        ParseRule oldPR = null;
        ParseRule newPR = origin;
        while (!newPR.equals(oldPR)) {
            oldPR = newPR;
            newPR = oldPR.origin;
        }
        return newPR;
    }

    public List<ParseRule> convertStarNodes() {
        List<ParseRule> res = new ArrayList<>();
        ParseRule copy = this.copy();
        copy.origin = this;
        Deque<Node> toConsider = new ArrayDeque<>(getRHS());
        while (!toConsider.isEmpty()) {
            Node rhsNode = toConsider.pop();
            if (rhsNode instanceof StarNode) {
                NonTerminal replacement = new NonTerminal("(" + rhsNode.toString() + ")");//NonTerminal.getNext();
                replacement.setSpecialStatus(NonTerminal.SpecialStatus.AUTOGENERATED_STAR);
                copy.replaceRHSNodes(rhsNode, replacement);
                ParseRule recursiveReplacement = new ParseRule(replacement);
                recursiveReplacement.origin = this;
                for (Node starContent : ((StarNode) rhsNode).contents()) {
                    recursiveReplacement.addRhs(starContent);
                }
                recursiveReplacement.addRhs(replacement);
                res.addAll(recursiveReplacement.convertStarNodes());
                ParseRule inheritanceRule = new ParseRule(replacement);
                inheritanceRule.origin = this;
                res.add(inheritanceRule);
            } else if (rhsNode instanceof BoundNonTerminal) {
                List<ParseRule> test = new ParseRule("_").addRhs(((BoundNonTerminal) rhsNode).getContent()).convertStarNodes();
                for (ParseRule generated : test) {
                    if (generated.getLHS().equals(nonTerm("_"))) {
                        assert generated.getRHS().size() == 1; //Since bound nodes are of only one node, this should also just be one.
                        copy.replaceRHSNodes(rhsNode, bound(generated.getRHS().get(0), ((BoundNonTerminal) rhsNode).getName(), ((BoundNonTerminal) rhsNode).isLazy()));
                    } else {
                        res.add(generated);
                    }
                }
                toConsider.push(((BoundNonTerminal) rhsNode).getContent());
            }
        }
        res.add(copy);
        return res;

    }

    private void replaceRHSNodes(Node rhsNode, Node replacement) {
        for (int i = 0; i < rhs.size(); i++) {
            if (rhs.get(i).equals(rhsNode)) {
                rhs.set(i, replacement);
            }
        }
    }

    ParseRule copy() {
        ParseRule res = new ParseRule((NonTerminal) this.lhs.copy());
        res.addRhs(this.rhs.stream().map(Node::copy).toArray(Node[]::new));
        if (origin == this) {
            res.origin = res;
        } else {
            res.origin = origin.copy();
        }
        assert this.equals(res);
        return res;
    }

    @Override
    public int hashCode() {
        return lhs.hashCode() + 3*rhs.hashCode();
    }

    public boolean equals(Object other) {
        if (!(other instanceof ParseRule)) {
            return false;
        }
        if (!lhs.equals(((ParseRule) other).lhs)) {
            return false;
        }
        return rhs.equals(((ParseRule) other).rhs);
    }

}
