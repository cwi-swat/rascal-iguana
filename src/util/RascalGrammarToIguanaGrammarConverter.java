package util;

import io.usethesource.vallang.*;
import io.usethesource.vallang.visitors.IValueVisitor;
import org.iguana.grammar.Grammar;
import org.iguana.grammar.condition.Condition;
import org.iguana.grammar.condition.ConditionType;
import org.iguana.grammar.condition.PositionalCondition;
import org.iguana.grammar.condition.RegularExpressionCondition;
import org.iguana.grammar.symbol.*;
import org.iguana.regex.CharRange;
import org.iguana.regex.RegularExpression;
import org.iguana.util.Tuple;

import java.util.*;
import java.util.stream.Collectors;

import static org.iguana.utils.string.StringUtil.listToString;
import static util.IValueUtils.*;

public class RascalGrammarToIguanaGrammarConverter {

    public Grammar convert(IConstructor grammar) {
        Grammar.Builder grammarBuilder = new Grammar.Builder();
        IMap definitions = (IMap) grammar.get("definitions");

        Iterator<Map.Entry<IValue, IValue>> entryIterator = definitions.entryIterator();
        Identifier layout = getLayoutDefinition(definitions);
        ValueVisitor visitor = new ValueVisitor(layout);

        while (entryIterator.hasNext()) {
            Map.Entry<IValue, IValue> next = entryIterator.next();
            IValue value = next.getValue();

            try {
                Rule rule = (Rule) value.accept(visitor);
                // Skipped rules, e.g., start rule, are skipped.
                if (rule != null) {
                    grammarBuilder.addRule(rule);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return grammarBuilder
            .setStartSymbols(new ArrayList<>(visitor.starts.values()))
            .setLayout(layout)
            .build();
    }

    // We need to first get the layout definition and then skip them when creating the Iguana grammar rules.
    // Iguana handles layout in a later stage.
    private static Identifier getLayoutDefinition(IMap definitions) {
        Iterator<IValue> it = definitions.iterator();
        // There is a default layout definition in Rascal grammars: $default = epsilon.
        Identifier layout = Identifier.fromName("$default$");
        while (it.hasNext()) {
            IValue next = it.next();
            if (isLayout(next)) {
                String value = ((IString) ((IConstructor) next).get(0)).getValue();
                if (!value.equals("$default$")) {
                    layout = Identifier.fromName(value);
                }
            }
        }
        return layout;
    }

    private static boolean isLayout(IValue value) {
        if (!(value instanceof IConstructor)) return false;
        return ((IConstructor) value).getName().equals("layouts");
    }

    static class ValueVisitor implements IValueVisitor<Object, Throwable> {

        private final Identifier layout;
        private final Map<IValue, Start> starts = new HashMap<>();

        public ValueVisitor() {
            this(null);
        }

        public ValueVisitor(Identifier layout) {
            this.layout = layout;
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
                set.add(res);
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

        @SuppressWarnings("unchecked")
        private void addChildren(Collection<Object> children, List<PriorityLevel> levels) {
            List<Object> alternativesOrSequences = children.stream().filter(c -> c instanceof Alternative || c instanceof Sequence).collect(Collectors.toList());
            List<Object> priorityLevels = children.stream().filter(c -> c instanceof PriorityLevel).collect(Collectors.toList());
            List<Object> lists = children.stream().filter(c -> c instanceof List<?>).collect(Collectors.toList());

            for (Object priorityLevel : priorityLevels) {
                levels.add((PriorityLevel) priorityLevel);
            }
            if (!alternativesOrSequences.isEmpty()) {
                PriorityLevel.Builder priorityLevelBuilder = new PriorityLevel.Builder();
                for (Object obj : alternativesOrSequences) {
                    if (obj instanceof Alternative) {
                        priorityLevelBuilder.addAlternative((Alternative) obj);
                    } else {
                        Alternative.Builder alternativeBuilder = new Alternative.Builder();
                        alternativeBuilder.addSequence((Sequence) obj);
                        priorityLevelBuilder.addAlternative(alternativeBuilder.build());
                    }
                }
                levels.add(priorityLevelBuilder.build());
            }

            if (!lists.isEmpty()) {
                for (Object o : lists) {
                    addChildren((Collection<Object>) o, levels);
                }
            }
        }

        @Override
        public Object visitConstructor(IConstructor cons) throws Throwable {
            switch (cons.getName()) {
                case "choice": return convertChoice(cons);
                case "prod": return convertProd(cons);
                case "sort":
                case "lex":
                case "layouts":
                    return convertSort(cons);
                case "keywords": return convertKeywords(cons);
                case "parameterized-sort": return convertParametrizedSort(cons);
                case "parameterized-lex": return convertParametrizedLex(cons);
                case "priority": return convertPriority(cons);
                case "empty": return convertEmpty(cons);
                case "lit": return convertLit(cons);
                case "cilit": convertCilit(cons);
                case "alt": return convertAlt(cons);
                case "opt": return convertOpt(cons);
                case "seq": return convertSeq(cons);
                case "iter": return convertIter(cons);
                case "iter-seps": return convertIterSeps(cons);
                case "iter-star": return convertIterStar(cons);
                case "iter-star-seps": return convertIterStarSeps(cons);
                case "associativity": return convertAssociativity(cons);
                case "assoc": return convertAssoc(cons);
                case "left": return convertLeft(cons);
                case "right": return convertRight(cons);
                case "non-assoc": return convertNonAssoc(cons);
                case "start": return convertStart(cons);
                case "label": return convertLabel(cons);
                case "char-class": return convertCharClass(cons);
                case "range": return convertRange(cons);
                case "conditional": return convertConditional(cons);
                case "follow": return convertFollow(cons);
                case "not-follow": return convertNotFollow(cons);
                case "precede": return convertPrecede(cons);
                case "not-precede": return convertNotPrecede(cons);
                case "delete": return convertDelete(cons);
                case "at-column": convertAtColumn(cons);
                case "begin-of-line": return convertBeginOfLine(cons);
                case "end-of-line": return convertEndOfLine(cons);
                case "except": return convertExcept(cons);
                case "tag": return convertTag(cons);
                case "bracket": return convertBracket(cons);
                case "parameter": convertParameter(cons);
                case "adt": return convertADT(cons);
                default: throw new RuntimeException("Unknown constructor name: " + cons.getName());
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

        // choice(Symbol def, set[Production] alternatives)
        private Rule convertChoice(IConstructor cons) throws Throwable {
            IValue headDef = cons.get("def");

            Symbol head = (Symbol) headDef.accept(this);
            Rule.Builder ruleBuilder = new Rule.Builder(Nonterminal.withName(head.getName()));

            Set<Object> alternatives = visit(cons, "alternatives");

            List<PriorityLevel> priorityLevels = new ArrayList<>();
            addChildren(alternatives, priorityLevels);

            ruleBuilder.addPriorityLevels(priorityLevels);

            LayoutStrategy layoutStrategy;
            // Do not insert layout for the lexical, literal or layout definitions
            if (isLexical(headDef) || isLayout(headDef) || isLiteral(headDef)) {
                layoutStrategy = LayoutStrategy.NO_LAYOUT;
            } else {
                layoutStrategy = LayoutStrategy.INHERITED;
            }

            ruleBuilder.setLayoutStrategy(layoutStrategy);

            // Skip the start rule, the start rule is generated in Iguana, as it may need some variable threading.
            if (isStart(headDef)) return null;

            return ruleBuilder.build();
        }

        // sort(str name)
        // lex(str name)
        // layouts(str name)
        private Nonterminal convertSort(IConstructor cons) throws Throwable {
            String nonterminalName = (String) cons.get("name").accept(this);
            return Nonterminal.withName(nonterminalName);
        }

        // keywords(str name)
        private Identifier convertKeywords(IConstructor cons) throws Throwable {
            String keywords = (String) cons.get("name").accept(this);
            return Identifier.fromName(keywords);
        }

        // parameterized-sort(str name, list[Symbol] parameters)
        private Nonterminal convertParametrizedSort(IConstructor cons) throws Throwable {
            String name = (String) cons.get("name").accept(this);
            List<Symbol> parameters = visit(cons, "parameters");
            return Nonterminal.withName(name + "_" + listToString(parameters, "_"));
        }

        // parameterized-lex(str name, list[Symbol] parameters)
        private Nonterminal convertParametrizedLex(IConstructor cons) throws Throwable {
            return convertParametrizedSort(cons);
        }

        // prod(Symbol def, list[Symbol] symbols, set[Attr] attributes)
        private Sequence convertProd(IConstructor cons) throws Throwable {
            Sequence.Builder sequenceBuilder = new Sequence.Builder();

            // The label for the alternative
            Symbol def = (Symbol) cons.get("def").accept(this);
            if (def.getLabel() != null) {
                sequenceBuilder.setLabel(def.getLabel());
            }

            List<Symbol> symbols = visit(cons, "symbols");
            for (Symbol symbol : symbols) {
                if (!isLayoutSymbol(symbol.getName())) {
                    sequenceBuilder.addSymbol(symbol);
                }
            }

            // attributes
            // TODO: check if this is necessary, or just having the Rascal definition is enough to retrieve these info.
            Set<Tuple<String, Object>> attributes = visit(cons, "attributes");
            for (Tuple<String, Object> attribute : attributes) {
                sequenceBuilder.addAttribute(attribute.getFirst(), attribute.getSecond());
            }
            sequenceBuilder.addAttribute("prod", cons);

            for (Tuple<String, Object> entry : attributes) {
                if (entry.getFirst().equals("assoc")) {
                    Associativity associativity = (Associativity) entry.getSecond();
                    sequenceBuilder.setAssociativity(associativity);
                    break;
                }
            }

            if (isStart(cons.get("def"))) {
                Start start = starts.get(cons.get("def"));
                assert start != null;
                // We need to store the start prod definition here for building the parse tree.
                starts.put(cons.get("def"), start.copy().addAttribute("prod", cons).build());
            }

            return sequenceBuilder.build();
        }

        // priority(Symbol def, list[Production] choices)
        private List<PriorityLevel> convertPriority(IConstructor cons) throws Throwable {
            List<PriorityLevel> priorityLevels = new ArrayList<>();

            List<Object> choices = visit(cons, "choices");

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
                } else if (child instanceof Rule) {
                    priorityLevels.addAll(((Rule) child).getPriorityLevels());
                }
            }

            return priorityLevels;
        }

        private Terminal convertEmpty(IConstructor cons) {
            return new Terminal.Builder(Terminal.epsilon()).setName("empty").build();
        }

        // lit(str string)
        private Nonterminal convertLit(IConstructor cons) throws Throwable {
            String nonterminalName = (String) cons.get("string").accept(this);
            return Nonterminal.withName("\"" + nonterminalName + "\"");
        }

        // lit(str string)
        private void convertCilit(IConstructor cons) {
            throw new RuntimeException("Case-insensitive strings not supported yet");
        }

        // alt(set[Symbol] alternatives)
        private Alt convertAlt(IConstructor cons) throws Throwable {
            Set<Symbol> symbols = visit(cons, "alternatives");
            return new Alt.Builder(new ArrayList<>(symbols)).addAttribute("definition", cons).build();
        }

        // opt(Symbol symbol)
        private Opt convertOpt(IConstructor cons) throws Throwable {
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            return new Opt.Builder(symbol).addAttribute("definition", cons).build();
        }

        // seq(list[Symbol] symbols)
        private Group convertSeq(IConstructor cons) throws Throwable {
            List<Symbol> symbols = visit(cons, "symbols");
            return new Group.Builder(symbols).addAttribute("definition", cons).build();
        }

        // iter(Symbol symbol)
        private Plus convertIter(IConstructor cons) throws Throwable {
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            return new Plus.Builder(symbol).addAttribute("definition", cons).build();
        }

        // iter-seps(Symbol symbol, list[Symbol] separators)
        private Plus convertIterSeps(IConstructor cons) throws Throwable {
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            List<Symbol> separators = visit(cons, "separators");
            Plus.Builder plusBuilder = new Plus.Builder(symbol);
            for (Symbol separator : separators) {
                if (!isLayoutSymbol(separator.getName())) {
                    plusBuilder.addSeparator(separator);
                }
            }
            plusBuilder.addAttribute("definition", cons);
            return plusBuilder.build();
        }

        // iter-star(Symbol symbol)
        private Star convertIterStar(IConstructor cons) throws Throwable {
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            return new Star.Builder(symbol).addAttribute("definition", cons).build();
        }

        // iter-star-seps(Symbol symbol, list[Symbol] separators)
        private Star convertIterStarSeps(IConstructor cons) throws Throwable {
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            List<Symbol> separators = visit(cons, "separators");
            Star.Builder starBuilder = new Star.Builder(symbol);
            for (Symbol separator : separators) {
                if (!isLayoutSymbol(separator.getName())) {
                    starBuilder.addSeparator(separator);
                }
            }
            starBuilder.addAttribute("definition", cons);
            return starBuilder.build();
        }

        // associativity(Symbol def, Associativity assoc, set[Production] alternatives)
        private Alternative convertAssociativity(IConstructor cons) throws Throwable {
            Associativity associativity = (Associativity) cons.get("assoc").accept(this);
            Set<Sequence> seqs = visit(cons, "alternatives");
            Alternative.Builder alternativeBuilder = new Alternative.Builder();
            alternativeBuilder.addSequences(new ArrayList<>(seqs));
            // Iguana only supports associativity for groups of size at least 2.
            // Associativity groups of size 0 or 1 are simple sequences which can have their own associativity.
            if (seqs.size() > 1) {
                alternativeBuilder.setAssociativity(associativity);
            }
            return alternativeBuilder.build();
        }

        // assoc(Associativity assoc)
        private Tuple<String, Object> convertAssoc(IConstructor cons) throws Throwable {
            return Tuple.of(cons.getName(), cons.get("assoc").accept(this));
        }

        // left()
        private Associativity convertLeft(IConstructor cons) {
            return Associativity.LEFT;
        }

        // right()
        private Associativity convertRight(IConstructor cons) {
            return Associativity.RIGHT;
        }

        // non-assoc()
        private Associativity convertNonAssoc(IConstructor cons) {
            return Associativity.NON_ASSOC;
        }

        // start(Symbol symbol)
        private Nonterminal convertStart(IConstructor cons) throws Throwable {
            Nonterminal nonterminal = (Nonterminal) cons.get("symbol").accept(this);
            starts.put(cons, new Start.Builder(nonterminal.getName()).addAttribute("definition", cons).build());
            return nonterminal;
        }

        // label(str name, Symbol symbol)
        private Symbol convertLabel(IConstructor cons) throws Throwable {
            String label = (String) cons.get("name").accept(this);
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            return symbol.copy().setLabel(label).build();
        }

        // char-class(list[CharRange] ranges)
        private Terminal convertCharClass(IConstructor cons) throws Throwable {
            List<CharRange> ranges = visit(cons, "ranges");
            return Terminal.from(org.iguana.regex.Alt.from(ranges));
        }

        // range(int begin, int end)
        private CharRange convertRange(IConstructor cons) throws Throwable {
            Integer start = (Integer) cons.get("begin").accept(this);
            Integer end = (Integer) cons.get("end").accept(this);
            return CharRange.in(start, end);
        }

        // conditional(Symbol symbol, set[Condition] conditions)
        private Symbol convertConditional(IConstructor cons) throws Throwable {
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            Set<Object> conditions = visit(cons, "conditions");
            SymbolBuilder<? extends Symbol> builder = symbol.copy();
            for (Object obj : conditions) {
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
                } else {
                    assert obj instanceof Identifier;
                    assert symbol instanceof Nonterminal;
                    ((Nonterminal.Builder) builder).addExcept(((Identifier) obj).getName());
                }
            }
            return builder.build();
        }

        // follow(Symbol symbol)
        private RegularExpressionCondition convertFollow(IConstructor cons) throws Throwable {
            return RegularExpressionCondition.follow(getRegex(cons.get("symbol")));
        }

        // not-follow(Symbol symbol)
        private RegularExpressionCondition convertNotFollow(IConstructor cons) throws Throwable {
            return RegularExpressionCondition.notFollow(getRegex(cons.get("symbol")));
        }

        // precede(Symbol symbol)
        private RegularExpressionCondition convertPrecede(IConstructor cons) throws Throwable {
            return RegularExpressionCondition.precede(getRegex(cons.get("symbol")));
        }

        // not-precede(Symbol symbol)
        private RegularExpressionCondition convertNotPrecede(IConstructor cons) throws Throwable {
            return RegularExpressionCondition.notPrecede(getRegex(cons.get("symbol")));
        }

        // delete(Symbol symbol)
        private RegularExpressionCondition convertDelete(IConstructor cons) throws Throwable {
            return RegularExpressionCondition.notMatch(getRegex(cons.get("symbol")));
        }

        // at-column(int column)
        private static void convertAtColumn(IConstructor cons) {
            throw new RuntimeException("at-column conditional is not supported yet");
        }

        // begin-of-line()
        private static PositionalCondition convertBeginOfLine(IConstructor cons) {
            return new PositionalCondition(ConditionType.START_OF_LINE);
        }

        // end-of-line()
        private PositionalCondition convertEndOfLine(IConstructor cons) {
            return new PositionalCondition(ConditionType.END_OF_LINE);
        }

        // except(str label)
        private Identifier convertExcept(IConstructor cons) throws Throwable {
            String except = (String) cons.get("label").accept(this);
            return Identifier.fromName(except);
        }

        // tag(value tag)
        private Object convertTag(IConstructor cons) throws Throwable {
            return cons.get("tag").accept(this);
        }

        // bracket()
        private static Tuple<String, Object> convertBracket(IConstructor cons) {
            return Tuple.of("bracket", null);
        }

        // parameter(str name, Symbol bound)
        private void convertParameter(IConstructor cons) {
            throw new RuntimeException("Parameters are expanded before conversion.");
        }

        // adt(str name, list[Symbol] parameters)
        private Tuple<String, List<Symbol>> convertADT(IConstructor cons) {
            throw new RuntimeException("Parametrized sorts are expanded before conversion.");
        }

        @SuppressWarnings("unchecked")
        private <T> T visit(IConstructor cons, String name) throws Throwable {
            return (T) cons.get(name).accept(this);
        }

        private boolean isLayoutSymbol(String name) {
            return layout != null && layout.getName().equals(name);
        }

        private RegularExpression getRegex(IValue value) throws Throwable {
            // String literals are expanded, so here we have to explicitly create a regular expression out of them.
            if (isLiteral(value)) {
                IString literalValue = (IString) ((IConstructor) value).get("string");
                int length = literalValue.length();
                List<org.iguana.regex.Char> chars = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    chars.add(org.iguana.regex.Char.from(literalValue.charAt(i)));
                }
                return org.iguana.regex.Seq.from(chars);
            }

            return getRegex((Symbol) value.accept(this));
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
            throw new RuntimeException("Should be a regular expression, but was: " + symbol.getClass());
        }
    }
}
