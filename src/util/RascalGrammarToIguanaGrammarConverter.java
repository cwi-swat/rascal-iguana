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
        public String visitString(IString o) {
            return o.getValue();
        }

        @Override
        public Object visitReal(IReal o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object visitRational(IRational o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object visitList(IList o) throws Throwable {
            List<Object> list = new ArrayList<>();
            for (IValue elem : o) {
                Object res = elem.accept(this);
                list.add(res);
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
            throw new UnsupportedOperationException();
        }

        @Override
        public Object visitTuple(ITuple o) {
            throw new UnsupportedOperationException();
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
                        throw new RuntimeException("Unexpected type: " + child.getClass());
                    }
                }
            }
        }

        @Override
        public Object visitConstructor(IConstructor o) throws Throwable {
            switch (o.getName()) {
                // choice(Symbol def, set[Production] alternatives)
                case "choice": {
                    Nonterminal head = (Nonterminal) o.get("def").accept(this);
                    if (isLayout(o.get(0))) {
                        layouts.add(head.getName());
                    }

                    List<PriorityLevel> priorityLevels = new ArrayList<>();

                    Set<Object> alternatives = (Set<Object>) o.get("alternatives").accept(this);
                    addChildren(alternatives, priorityLevels);
                    return priorityLevels;
                }
                // prod(Symbol def, list[Symbol] symbols, set[Attr] attributes)
                case "prod": {
                    Sequence.Builder sequenceBuilder = new Sequence.Builder();

                    Symbol first = (Symbol) o.get("def").accept(this);
                    if (first.getLabel() != null) {
                        sequenceBuilder.setLabel(first.getLabel());
                    }

                    List<Symbol> symbols = (List<Symbol>) o.get("symbols").accept(this);
                    for (Symbol symbol : symbols) {
                        if (!layouts.contains(symbol.getName()))
                            sequenceBuilder.addSymbol(symbol);
                    }

                    // attributes
                    Set<Tuple<String, Object>> attributes = (Set<Tuple<String, Object>>) o.get("attributes").accept(this);
                    for (Tuple<String, Object> attribute : attributes) {
                        sequenceBuilder.addAttribute(attribute.getFirst(), attribute.getSecond());
                    }
                    return sequenceBuilder.build();
                }
                // parameterized-sort(str name, list[Symbol] parameters)
                case "parameterized-sort":
                // sort(str name)
                case "sort": {
                    String nonterminalName = (String) o.get("name").accept(this);
                    return Nonterminal.withName(nonterminalName);
                }
                // keywords(str name)
                case "keywords": {
                    String keywords = (String) o.get("name").accept(this);
                    return Identifier.fromName(keywords);
                }
                // parameterized-lex(str name, list[Symbol] parameters)
                case "parametrized-lex":
                // lex(str name)
                case "lex": {
                    String nonterminalName = (String) o.get("name").accept(this);
                    return Nonterminal.withName(nonterminalName);
                }
                // layouts(str name)
                case "layouts": {
                    String nonterminalName = (String) o.get("name").accept(this);
                    return Nonterminal.withName(nonterminalName);
                }
                // priority(Symbol def, list[Production] choices)
                case "priority": {
                    List<PriorityLevel> priorityLevels = new ArrayList<>();

                    List<Object> choices = (List<Object>) o.get("choices").accept(this);

                    for (Object child : choices) {
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
                                    throw new RuntimeException("Is it necessary here?");
//                                    PriorityLevel.Builder priorityLevelBuilder = new PriorityLevel.Builder();
//                                    List<Alternative> alternatives = (List<Alternative>) choices;
//                                    priorityLevelBuilder.addAlternatives(alternatives);
//                                    priorityLevels.add(priorityLevelBuilder.build());
                                }
                            }
                        }
                    }

                    return priorityLevels;
                }
                // assoc(Associativity assoc)
                case "assoc": {
                    return Tuple.of(o.getName(), o.get("assoc").accept(this));
                }
                case "empty": {
                    return new Nonterminal.Builder("empty").build();
                }
                // lit(str string)
                case "lit": {
                    String value = (String) o.get("string").accept(this);
                    RegularExpression regex = Seq.from(value);
                    return new Terminal.Builder(regex)
                        .setNodeType(TerminalNodeType.Regex)
                        .build();
                }
                case "cilit": {
                    throw new RuntimeException("Case-insensitive strings not supported yet");
                }
                // alt(set[Symbol] alternatives)
                case "alt": {
                   Set<Symbol> symbols = (Set<Symbol>) o.get("alternatives").accept(this);
                   return Alt.from(new ArrayList<>(symbols));
                }
                // opt(Symbol symbol)
                case "opt": {
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    return Opt.from(symbol);
                }
                // seq(list[Symbol] symbols)
                case "seq": {
                    List<Symbol> symbols = (List<Symbol>) o.get("symbols").accept(this);
                    return Group.from(symbols);
                }
                // iter(Symbol symbol)
                case "iter": {
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    return Plus.from(symbol);
                }
                // iter-seps(Symbol symbol, list[Symbol] separators)
                case "iter-seps": {
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    List<Symbol> separators = (List<Symbol>) o.get("separators").accept(this);
                    Plus.Builder plusBuilder = new Plus.Builder(symbol);
                    for (Symbol separator : separators) {
                        if (!layouts.contains(separator.getName())) {
                            plusBuilder.addSeparator(separator);
                        }
                    }
                    return plusBuilder.build();
                }
                // iter-star(Symbol symbol)
                case "iter-star": {
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    return Star.from(symbol);
                }
                // iter-star-seps(Symbol symbol, list[Symbol] separators)
                case "iter-star-seps": {
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    List<Symbol> separators = (List<Symbol>) o.get("separators").accept(this);
                    Star.Builder starBuilder = new Star.Builder(symbol);
                    for (Symbol separator : separators) {
                        if (!layouts.contains(separator.getName())) {
                            starBuilder.addSeparator(separator);
                        }
                    }
                    return starBuilder.build();
                }
                // associativity(Symbol def, Associativity assoc, set[Production] alternatives)
                case "associativity": {
                    Associativity associativity = (Associativity) o.get("assoc").accept(this);
                    Set<Sequence> seqs = (Set<Sequence>) o.get("alternatives").accept(this);
                    Alternative.Builder alternativeBuilder = new Alternative.Builder();
                    alternativeBuilder.addSequences(new ArrayList<>(seqs));
                    alternativeBuilder.setAssociativity(associativity);
                    return alternativeBuilder.build();
                }
                // left()
                case "left": {
                    return Associativity.LEFT;
                }
                // right()
                case "right": {
                    return Associativity.RIGHT;
                }
                // non-assoc()
                case "non-assoc": {
                    return Associativity.NON_ASSOC;
                }
                // start(Symbol symbol)
                case "start": {
                    Nonterminal nonterminal = (Nonterminal) o.get("symbol").accept(this);
                    start = Start.from(nonterminal.getName());
                    return nonterminal;
                }
                // label(str name, Symbol symbol)
                case "label": {
                    String label = (String) o.get("name").accept(this);
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    return symbol.copy().setLabel(label).build();
                }
                // range(int begin, int end)
                case "range": {
                    Integer start = (Integer) o.get("begin").accept(this);
                    Integer end = (Integer) o.get("end").accept(this);
                    return CharRange.in(start, end);
                }
                // char-class(list[CharRange] ranges)
                case "char-class": {
                    List<CharRange> ranges = (List<CharRange>) o.get("ranges").accept(this);
                    List<Symbol> terminals = ranges.stream().map(Terminal::from).collect(Collectors.toList());
                    return Alt.from(terminals);
                }
                // follow(Symbol symbol)
                case "follow": {
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    if (isRegex(symbol)) {
                        return RegularExpressionCondition.follow(getRegex(symbol));
                    } else {
                        throw new RuntimeException("Must only be a regular expression: " + symbol);
                    }
                }
                // not-follow(Symbol symbol)
                case "not-follow": {
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    if (isRegex(symbol)) {
                        return RegularExpressionCondition.notFollow(getRegex(symbol));
                    } else {
                        throw new RuntimeException("Must only be a regular expression: " + symbol);
                    }
                }
                // precede(Symbol symbol)
                case "precede": {
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    if (isRegex(symbol)) {
                        return RegularExpressionCondition.precede(getRegex(symbol));
                    } else {
                        throw new RuntimeException("Must only be a regular expression: " + symbol);
                    }
                }
                // not-precede(Symbol symbol)
                case "not-precede": {
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    if (isRegex(symbol)) {
                        return RegularExpressionCondition.notPrecede(getRegex(symbol));
                    } else {
                        throw new RuntimeException("Must only be a regular expression: " + symbol);
                    }
                }
                // delete(Symbol symbol)
                case "delete": {
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    if (isRegex(symbol)) {
                        return RegularExpressionCondition.notMatch(getRegex(symbol));
                    } else {
                        throw new RuntimeException("Must only be a regular expression: " + symbol);
                    }
                }
                // at-column(int column)
                case "at-column": {
                    throw new RuntimeException("at-column conditional is not supported yet");
                }
                // end-of-line()
                case "end-of-line": {
                    return new PositionalCondition(ConditionType.END_OF_LINE);
                }
                // begin-of-line()
                case "begin-of-line": {
                    return new PositionalCondition(ConditionType.START_OF_LINE);
                }
                // except(str label)
                case "except": {
                    String except = (String) o.get("label").accept(this);
                    return Identifier.fromName(except);
                }
                // conditional(Symbol symbol, set[Condition] conditions)
                case "conditional": {
                    Symbol symbol = (Symbol) o.get("symbol").accept(this);
                    Set<Object> conditions = (Set<Object>) o.get("conditions").accept(this);
                    for (Object obj : conditions) {
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
                            assert symbol instanceof Nonterminal;
                            Nonterminal nonterminal = (Nonterminal) symbol;
                            return nonterminal.copy().addExcept(((Identifier) obj).getName()).build();
                        }
                    }
                }
                // tag(value tag)
                case "tag": {
                    return o.get("tag").accept(this);
                }
                // bracket()
                case "bracket": {
                    return Tuple.of("bracket", null);
                }
                // parameter(str name, Symbol bound)
                case "parameter": {
                    String name = (String) o.get("name").accept(this);
                    Symbol bound = (Symbol) o.get("bound").accept(this);
                    return Tuple.of(name, bound);
                }
                // adt(str name, list[Symbol] parameters)
                case "adt": {
                    String name = (String) o.get("name").accept(this);
                    List<Symbol> parameters = (List<Symbol>) o.get("parameters").accept(this);
                    return Tuple.of(name, parameters);
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
