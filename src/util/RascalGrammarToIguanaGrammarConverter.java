package util;

import io.usethesource.vallang.*;
import io.usethesource.vallang.visitors.IValueVisitor;
import org.iguana.grammar.Grammar;
import org.iguana.grammar.condition.Condition;
import org.iguana.grammar.condition.ConditionType;
import org.iguana.grammar.condition.PositionalCondition;
import org.iguana.grammar.condition.RegularExpressionCondition;
import org.iguana.grammar.slot.TerminalNodeType;
import org.iguana.grammar.symbol.*;
import org.iguana.regex.CharRange;
import org.iguana.regex.RegularExpression;
import org.iguana.regex.Seq;
import org.iguana.util.Tuple;

import java.util.*;
import java.util.stream.Collectors;

public class RascalGrammarToIguanaGrammarConverter {

    private boolean isLexical(IValue value) {
        if (!(value instanceof IConstructor)) return false;
        return ((IConstructor) value).getName().equals("lex");
    }

    public Grammar convert(IConstructor grammar) {
        Grammar.Builder grammarBuilder = new Grammar.Builder();
        IMap definitions = (IMap) grammar.get("definitions");

        Iterator<Map.Entry<IValue, IValue>> entryIterator = definitions.entryIterator();
        Set<String> layouts = new HashSet<>();
        ValueVisitor visitor = new ValueVisitor(layouts);

        while (entryIterator.hasNext()) {
            Map.Entry<IValue, IValue> next = entryIterator.next();
            IValue key = next.getKey();
            IValue value = next.getValue();
            System.out.println(key + " = " + value + " " + value.getType() + " " + value.getType().getName());

            try {
                Nonterminal head = (Nonterminal) key.accept(visitor);
                Rule.Builder ruleBuilder = new Rule.Builder(head);
                List<PriorityLevel> priorityLevels = (List<PriorityLevel>) value.accept(visitor);
                ruleBuilder.addPriorityLevels(priorityLevels);
                LayoutStrategy layoutStrategy = LayoutStrategy.INHERITED;
                if (isLexical(key)) {
                    layoutStrategy = LayoutStrategy.NO_LAYOUT;
                }

                ruleBuilder.setLayoutStrategy(layoutStrategy);
                grammarBuilder.addRule(ruleBuilder.build());
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return grammarBuilder
            .setStartSymbol(visitor.start)
            .build();
    }

    static class ValueVisitor implements IValueVisitor<Object, Throwable> {

        private final Set<String> layouts;
        private Start start;

        public ValueVisitor(Set<String> layouts) {
            this.layouts = layouts;
        }

        @Override
        public String visitString(IString o) throws Throwable {
            return o.getValue();
        }

        @Override
        public Object visitReal(IReal o) throws Throwable {
            throw new RuntimeException();
        }

        @Override
        public Object visitRational(IRational o) throws Throwable {
            throw new RuntimeException();
        }

        @Override
        public Object visitList(IList o) throws Throwable {
            List<Object> list = new ArrayList<>();
            for (IValue elem : o) {
                Object res = elem.accept(this);
                if (res != null) list.add(res);
            }
            return list;
        }

        @Override
        public Object visitSet(ISet o) throws Throwable {
            Set<Object> set = new HashSet<>();
            for (IValue elem : o) {
                Object res = elem.accept(this);
                if (res != null) set.add(res);
            }
            return set;
        }

        @Override
        public Object visitSourceLocation(ISourceLocation o) {
            throw new RuntimeException(o.toString());
        }

        @Override
        public Object visitTuple(ITuple o) {
            throw new RuntimeException(o.toString());
        }

        @Override
        public Tuple<String, Object> visitNode(INode o) throws Throwable {
            if (o.arity() == 0) {
                return Tuple.of(o.getName(), null);
            }
            Object value = o.get(0).accept(this);
            return Tuple.of(o.getName(), value);
        }

        private boolean isLayout(IValue value) {
            if (!(value instanceof IConstructor)) return false;
            return ((IConstructor) value).getName().equals("layouts");
        }

        private void addChildren(Collection<Object> children, List<PriorityLevel> priorityLevels) {
            boolean allPriorityLevels = children.stream().allMatch(child -> child instanceof PriorityLevel);
            if (allPriorityLevels) {
                for (Object child : children) {
                    priorityLevels.add((PriorityLevel) child);
                }
            } else {
                for (Object child : children) {
                    PriorityLevel.Builder priorityLevelBuilder = new PriorityLevel.Builder();
                    if (child instanceof Alternative) {
                        priorityLevelBuilder.addAlternative((Alternative) child);
                        priorityLevels.add(priorityLevelBuilder.build());
                    } else if (child instanceof Sequence) {
                        Alternative.Builder alternativeBuilder = new Alternative.Builder();
                        alternativeBuilder.addSequence((Sequence) child);
                        priorityLevelBuilder.addAlternative(alternativeBuilder.build());
                        priorityLevels.add(priorityLevelBuilder.build());
                    } else if (child instanceof List<?>) {
                        addChildren((List<Object>) child, priorityLevels);
                    } else {
                        throw new RuntimeException(">>>>>>>>: " + child.getClass());
                    }
                }
            }
        }

        @Override
        public Object visitConstructor(IConstructor o) throws Throwable {
            List<Object> visitedChildren = new ArrayList<>();
            for (IValue child : o.getChildren()) {
                visitedChildren.add(child.accept(this));
            }
            switch (o.getName()) {
                case "choice": {
                    Nonterminal head = (Nonterminal) visitedChildren.get(0);
                    if (isLayout(o.get(0))) {
                        layouts.add(head.getName());
                    }

                    List<PriorityLevel> priorityLevels = new ArrayList<>();

                    Set<Object> children = (Set<Object>) visitedChildren.get(1);
                    addChildren(children, priorityLevels);
                    return priorityLevels;
                }
                case "prod": {
                    Sequence.Builder sequenceBuilder = new Sequence.Builder();

                    Symbol first = (Symbol) visitedChildren.get(0);
                    if (first.getLabel() != null) {
                        sequenceBuilder.setLabel(first.getLabel());
                    }

                    List<Symbol> symbols = (List<Symbol>) visitedChildren.get(1);
                    for (Symbol symbol : symbols) {
                        if (!layouts.contains(symbol.getName()))
                            sequenceBuilder.addSymbol(symbol);
                    }

                    // attributes
                    Set<Tuple<String, Object>> attributes = (Set<Tuple<String, Object>>) visitedChildren.get(2);
                    for (Tuple<String, Object> attribute : attributes) {
                        sequenceBuilder.addAttribute(attribute.getFirst(), attribute.getSecond());
                    }
                    return sequenceBuilder.build();
                }
                case "parameterized-sort":
                case "sort": {
                    String nonterminalName = (String) visitedChildren.get(0);
                    return new Nonterminal.Builder(nonterminalName).build();
                }
                case "keyword":
                case "lex": {
                    String nonterminalName = (String) visitedChildren.get(0);
                    return new Nonterminal.Builder(nonterminalName).build();
                }
                case "layouts": {
                    String nonterminalName = (String) visitedChildren.get(0);
                    return new Nonterminal.Builder(nonterminalName).build();
                }
                case "priority": {
                    List<PriorityLevel> priorityLevels = new ArrayList<>();

                    List<Object> children = (List<Object>) visitedChildren.get(1);

                    for (Object child : children) {
                        if (child instanceof Sequence) {
                            PriorityLevel.Builder priorityLevelBuilder = new PriorityLevel.Builder();
                            Alternative.Builder alternativeBuilder = new Alternative.Builder();
                            alternativeBuilder.addSequence((Sequence) child);
                            priorityLevelBuilder.addAlternative(alternativeBuilder.build());
                            priorityLevels.add(priorityLevelBuilder.build());
                        } else if (child instanceof Alternative) {
                            PriorityLevel.Builder priorityLevelBuilder = new PriorityLevel.Builder();
                            priorityLevelBuilder.addAlternative((Alternative) child);
                            priorityLevels.add(priorityLevelBuilder.build());
                        } else if (child instanceof PriorityLevel) {
                            priorityLevels.add((PriorityLevel) child);
                        } else if (child instanceof List<?>) {
                            for (Object c : (List<?>) child) {
                                if (c instanceof PriorityLevel) {
                                    priorityLevels.add((PriorityLevel) c);
                                } else {
                                    PriorityLevel.Builder priorityLevelBuilder = new PriorityLevel.Builder();
                                    List<Alternative> alternatives = (List<Alternative>) visitedChildren.get(1);
                                    priorityLevelBuilder.addAlternatives(alternatives);
                                    priorityLevels.add(priorityLevelBuilder.build());
                                }
                            }
                        }
                    }

                    return priorityLevels;
                }
                case "assoc": {
                    return Tuple.of(o.getName(), visitedChildren.get(0));
                }
                case "empty": {
                    return new Nonterminal.Builder("empty").build();
                }
                case "lit": {
                    String value = o.get(0).accept(this).toString();
                    RegularExpression regex = Seq.from(value);
                    return new Terminal.Builder(regex)
                        .setNodeType(TerminalNodeType.Regex)
                        .build();
                }
                case "alt": {
                   Alt.Builder altBuilder = new Alt.Builder();
                   for (Object child : visitedChildren) {
                       altBuilder.add((Symbol) child);
                   }
                   return altBuilder.build();
                }
                case "opt": {
                    Symbol symbol = (Symbol) o.get(0).accept(this);
                    return Opt.from(symbol);
                }
                case "seq": {
                    Group.Builder groupBuilder = new Group.Builder();
                    System.out.println("visited children: " + visitedChildren);
                    for (Object child : visitedChildren) {
                        System.out.println(">>>>> seq child: " + child);
                        groupBuilder.add((Symbol) child);
                    }
                    return groupBuilder.build();
                }
                case "iter": {
                    Symbol symbol = (Symbol) o.get(0).accept(this);
                    return new Plus.Builder(symbol).build();
                }
                case "iter-seps": {
                    Symbol symbol = (Symbol) o.get(0).accept(this);
                    List<Symbol> separators = (List<Symbol>) o.get(1).accept(this);
                    Plus.Builder plusBuilder = new Plus.Builder(symbol);
                    for (Symbol separator : separators) {
                        if (!layouts.contains(separator.getName())) {
                            plusBuilder.addSeparator(separator);
                        }
                    }
                    return plusBuilder.build();
                }
                case "iter-star": {
                    Symbol symbol = (Symbol) o.get(0).accept(this);
                    return new Star.Builder(symbol).build();
                }
                case "iter-star-seps": {
                    Symbol symbol = (Symbol) o.get(0).accept(this);
                    List<Symbol> separators = (List<Symbol>) o.get(1).accept(this);
                    Star.Builder starBuilder = new Star.Builder(symbol);
                    for (Symbol separator : separators) {
                        if (!layouts.contains(separator.getName())) {
                            starBuilder.addSeparator(separator);
                        }
                    }
                    return starBuilder.build();
                }
                case "associativity": {
                    Associativity associativity = (Associativity) visitedChildren.get(1);
                    Set<Sequence> seqs = (Set<Sequence>) visitedChildren.get(2);
                    Alternative.Builder alternativeBuilder = new Alternative.Builder();
                    alternativeBuilder.addSequences(new ArrayList<>(seqs));
                    alternativeBuilder.setAssociativity(associativity);
                    return alternativeBuilder.build();
                }
                case "left": {
                    return Associativity.LEFT;
                }
                case "right": {
                    return Associativity.RIGHT;
                }
                case "non-assoc": {
                    return Associativity.NON_ASSOC;
                }
                case "start": {
                    Nonterminal nonterminal = (Nonterminal) visitedChildren.get(0);
                    start = Start.from(nonterminal.getName());
                    return nonterminal;
                }
                case "label": {
                    String label = (String) visitedChildren.get(0);
                    Symbol symbol = (Symbol) visitedChildren.get(1);
                    return symbol.copy().setLabel(label).build();
                }
                case "range": {
                    Integer start = (Integer) visitedChildren.get(0);
                    Integer end = (Integer) visitedChildren.get(1);
                    return CharRange.in(start, end);
                }
                case "char-class": {
                    List<CharRange> ranges = (List<CharRange>) visitedChildren.get(0);
                    List<Symbol> terminals = ranges.stream().map(Terminal::from).collect(Collectors.toList());
                    return Alt.from(terminals);
                }
                case "follow": {
                    Symbol symbol = (Symbol) visitedChildren.get(0);
                    if (isRegex(symbol)) {
                        return RegularExpressionCondition.follow(getRegex(symbol));
                    } else {
                        throw new RuntimeException("Must only be a regular expression: " + symbol);
                    }
                }
                case "not-follow": {
                    Symbol symbol = (Symbol) visitedChildren.get(0);
                    if (isRegex(symbol)) {
                        return RegularExpressionCondition.notFollow(getRegex(symbol));
                    } else {
                        throw new RuntimeException("Must only be a regular expression: " + symbol);
                    }
                }
                case "not-precede": {
                    Symbol symbol = (Symbol) visitedChildren.get(0);
                    if (isRegex(symbol)) {
                        return RegularExpressionCondition.notPrecede(getRegex(symbol));
                    } else {
                        throw new RuntimeException("Must only be a regular expression: " + symbol);
                    }
                }
                case "precede": {
                    Symbol symbol = (Symbol) visitedChildren.get(0);
                    if (isRegex(symbol)) {
                        return RegularExpressionCondition.precede(getRegex(symbol));
                    } else {
                        throw new RuntimeException("Must only be a regular expression: " + symbol);
                    }
                }
                case "except": {
                    String except = (String) visitedChildren.get(0);
                    return Identifier.fromName(except);
                }
                case "keywords": {
                    String keywords = (String) visitedChildren.get(0);
                    return Identifier.fromName(keywords);
                }
                case "end-of-line": {
                    return new PositionalCondition(ConditionType.END_OF_LINE);
                }
                case "begin-of-line": {
                    return new PositionalCondition(ConditionType.START_OF_LINE);
                }
                case "conditional": {
                    Set<Object> conditions = (Set<Object>) visitedChildren.get(1);
                    for (Object obj : conditions) {
                        Symbol symbol = (Symbol) visitedChildren.get(0);
                        SymbolBuilder<? extends Symbol> builder = symbol.copy();
                        if (obj instanceof Condition) {
                            Condition condition = (Condition) obj;
                            switch (condition.getType()) {
                                case FOLLOW:
                                case NOT_FOLLOW:
                                case END_OF_FILE:
                                    builder.addPostCondition(condition);
                                    break;
                                case START_OF_LINE:
                                case PRECEDE:
                                case NOT_PRECEDE:
                                    builder.addPreCondition(condition);
                                    break;
                            }
                            return builder.build();
                        } else {
                            assert obj instanceof Identifier;
                            assert visitedChildren.get(0) instanceof Nonterminal;
                            Nonterminal nonterminal = (Nonterminal) visitedChildren.get(0);
                            return nonterminal.copy().addExcept(((Identifier) obj).getName()).build();
                        }
                    }
                }
                case "tag": {
                    return visitedChildren.get(0);
                }
                case "bracket": {
                    return Tuple.of("bracket", null);
                }
                default:
                    throw new RuntimeException("Unknown constructor name: " + o.getName());
            }
        }

        @Override
        public Integer visitInteger(IInteger o) {
            return o.intValue();
        }

        @Override
        public Object visitMap(IMap o) {
            throw new RuntimeException(o.toString());
        }

        @Override
        public Object visitBoolean(IBool boolValue) {
            throw new RuntimeException(boolValue.toString());
        }

        @Override
        public Object visitExternal(IExternalValue externalValue) {
            throw new RuntimeException(externalValue.toString());
        }

        @Override
        public Object visitDateTime(IDateTime o) {
            throw new RuntimeException(o.toString());
        }

        private static boolean isRegex(Symbol symbol) {
            if (symbol instanceof Terminal) {
                return true;
            }
            if (symbol instanceof Identifier) {
                return true;
            }
            if (symbol instanceof Alt) {
                Alt alt = (Alt) symbol;
                return alt.getChildren().stream().allMatch(ValueVisitor::isRegex);
            }
            if (symbol instanceof Star) {
                Star star = (Star) symbol;
                return isRegex(star.getSymbol());
            }
            if (symbol instanceof Plus) {
                Plus plus = (Plus) symbol;
                return isRegex(plus.getSymbol());
            }
            if (symbol instanceof Opt) {
                Opt opt = (Opt) symbol;
                return isRegex(opt.getSymbol());
            }
            if (symbol instanceof Group) {
                Group group = (Group) symbol;
                return group.getChildren().stream().allMatch(ValueVisitor::isRegex);
            }
            return false;
        }

        private static RegularExpression getRegex(Symbol symbol) {
            if (symbol instanceof Terminal) {
                return ((Terminal) symbol).getRegularExpression();
            }
            if (symbol instanceof Identifier) {
                return org.iguana.regex.Reference.from(symbol.getName());
            }
            if (symbol instanceof Alt) {
                Alt alt = (Alt) symbol;
                List<RegularExpression> regexes = alt.getChildren().stream().map(ValueVisitor::getRegex).collect(Collectors.toList());
                return org.iguana.regex.Alt.from(regexes);
            }
            if (symbol instanceof Star) {
                Star star = (Star) symbol;
                return org.iguana.regex.Star.from(getRegex(star.getSymbol()));
            }
            if (symbol instanceof Plus) {
                Plus plus = (Plus) symbol;
                return org.iguana.regex.Plus.from(getRegex(plus.getSymbol()));
            }
            if (symbol instanceof Opt) {
                Opt opt = (Opt) symbol;
                return org.iguana.regex.Opt.from(getRegex(opt.getSymbol()));
            }
            if (symbol instanceof Group) {
                Group group = (Group) symbol;
                List<RegularExpression> regexes = group.getChildren().stream().map(ValueVisitor::getRegex).collect(Collectors.toList());
                return org.iguana.regex.Seq.from(regexes);
            }
            throw new RuntimeException("Should not reach here");
        }
    }
}
