package parselang.parser;

import javafx.util.Pair;
import parselang.languages.Language;
import parselang.parser.data.*;
import parselang.parser.rulealgorithms.*;

import java.util.*;
import java.util.stream.Collectors;

public class ParseRuleStorage {

    private Set<Pair<ParseRule, Direction>> addedRules;

    private final Map<NonTerminal, List<ParseRule>> rules = new HashMap<>();
    private final Map<Node, Set<Character>> first = new HashMap<>();
    private final Map<Node, Set<Character>> follow = new HashMap<>();
    private final Map<NonTerminal, Map<Character, LinkedHashSet<ParseRule>>> rulesPlus = new HashMap<>();
    private final Set<NonTerminal> allNonterminals = new HashSet<>();

    private final FirstCalculator firstCalc = new NaiveFirstCalculator();
    private final FollowCalculator followCalc = new NaiveFollowCalculator();
    private final FirstPlusCalculator firstPlusCalc = new NaiveFirstPlusCalculator();




    public void prepare(Language lang, NonTerminal toplevel) throws UndefinedNontermException {
        addedRules = new HashSet<>();
        setDefaults(lang);
        calculateFirst();
        calculateFollow(toplevel);
        calculateFirstPlus();
    }

    public void addCustomRules(Collection<Pair<ParseRule, Direction>> customRules, NonTerminal toplevel) {
        addedRules.addAll(customRules);
        for (Pair<ParseRule, Direction> rule : customRules) {
            addRule(rule.getKey(), rule.getValue());
        }
        try {
            calculateFirst();
        } catch (UndefinedNontermException e) {
            //This should not happen??
            throw new RuntimeException();
        }
        calculateFollow(toplevel);
        calculateFirstPlus();
    }

    private void addRule(ParseRule rule, Direction dir) {
        addRules(rule.convertStarNodes(), dir);
    }

    private void addMissingNonterminals(Collection<Node> nodes) {
        for (Node node : nodes) {
            if (node instanceof NonTerminal) {
                allNonterminals.add((NonTerminal) node);
                this.rules.putIfAbsent((NonTerminal) node, new LinkedList<>());
            } else if (node instanceof StarNode) {
                addMissingNonterminals(((StarNode) node).contents());
            } else if (node instanceof BoundNonTerminal) {
                addMissingNonterminals(Collections.singleton(((BoundNonTerminal) node).getContent()));
            }
        }
    }

    private void addRules(Collection<ParseRule> rules, Direction dir) {
        for (ParseRule rule : rules) {
            NonTerminal nonTerminal = rule.getLHS();
            allNonterminals.add(nonTerminal);
            this.rules.computeIfAbsent(nonTerminal, nonTerminal1 -> new LinkedList<>());
            addMissingNonterminals(rule.getRHS());
            switch (dir) {
                case LEFT:
                    this.rules.get(nonTerminal).add(0, rule);
                    break;
                case RIGHT:
                    this.rules.get(nonTerminal).add(rule);
            }
        }
    }


    public LinkedHashSet<ParseRule> getByNonTerminal(Node nonTerminal, Character startsWith) {
        if (!(nonTerminal instanceof NonTerminal)) {
            return new LinkedHashSet<>();
        }
        if (rulesPlus.containsKey(nonTerminal)) {
            if (rulesPlus.get(nonTerminal).containsKey(startsWith)) {
                return rulesPlus.get(nonTerminal).get(startsWith);
            }
            return rulesPlus.get(nonTerminal).getOrDefault(null, new LinkedHashSet<>());
        } else {
            System.out.println("Warning! No such rule! => " + ((NonTerminal)nonTerminal).getName() + ", starts with: \"" + startsWith + "\"");
            return new LinkedHashSet<>();
        }
    }

    public static Node ws() {
        return new StarNode(nonTerm("WhiteSpace"));
    }

    public static NonTerminal nonTerm(String name) {
        return new NonTerminal(name);
    }

    public static Terminal term(String name) {
        return new Terminal(name);
    }

    public static BoundNonTerminal bound(NonTerminal nonTerm, String name, boolean lazy) {
        return new BoundNonTerminal(nonTerm, name, lazy);
    }

    public static Node star(Node... content) {
        return new StarNode(content);
    }

    public static Node star(List<Node> content) {
        return new StarNode(content.toArray(new Node[0]));
    }

    private void setDefaults(Language language) {
        List<ParseRule> originalRules = language.getRules();
        for (ParseRule rule : originalRules) {
            addRule(rule, Direction.RIGHT);
        }
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        List<NonTerminal> nonTerminalDomain = rules.keySet().stream().sorted(Comparator.comparing(NonTerminal::getName)).collect(Collectors.toList());
        for (NonTerminal i : nonTerminalDomain) {
            sb.append(i.getName()).append(" =\n");
            for (int p = 0; p < rules.get(i).size(); p++) {
                List<Node> rhs = rules.get(i).get(p).getRHS();
                sb.append("\t");
                for (int j = 0; j < rhs.size()-1; j++) {
                    sb.append(rhs.get(j)).append(" ");
                }
                if (!rhs.isEmpty()) {
                    sb.append(rhs.get(rhs.size() - 1));
                }
                if (p != rules.get(i).size() - 1) {
                    sb.append(" |");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private Set<Terminal> getAllTerminals() {
        Set<Terminal> res = new HashSet<>();
        for (List<ParseRule> rulesForNonTerminal : rules.values()) {
            for (ParseRule rule : rulesForNonTerminal) {
                for (Node token : rule.getRHS()) {
                    if (token instanceof Terminal) {
                        res.add((Terminal) token);
                    }
                }
            }
        }
        return res;
    }

    private Set<NonTerminal> getAllNonTerminals() {
        return allNonterminals;
    }

    private void calculateFirst() throws UndefinedNontermException {
        firstCalc.updateFirst(first, rules, getAllTerminals(), getAllNonTerminals());
    }

    private void calculateFirstPlus() {
        firstPlusCalc.updateFirstPlus(rulesPlus, rules, first, follow, getAllTerminals(), getAllNonTerminals());
    }

    private void calculateFollow(NonTerminal startSymbol) {
        followCalc.updateFollow(follow, startSymbol, first, rules, getAllTerminals(), getAllNonTerminals());
    }

    public Set<Pair<ParseRule, Direction>> getAddedRules() {
        return addedRules;
    }
}
