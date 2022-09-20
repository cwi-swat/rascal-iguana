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
            .setStartSymbol(visitor.start)
            .setLayout(layout)
            .build();
    }

    // We need to first get the layout definition and then skip them when creating the Iguana grammar rules.
    // Iguana handles layout in a later stage.
    private static Identifier getLayoutDefinition(IMap definitions) {
        Iterator<IValue> it = definitions.iterator();
        Identifier layout = null;
        while (it.hasNext()) {
            IValue next = it.next();
            if (isLayout(next)) {
                String value = ((IString) ((IConstructor) next).get(0)).getValue();
                // Skip the default layout definition.
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
        private Start start;

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
                case "sort": return convertSort(cons);
                case "lex": return convertSort(cons);
                case "layouts": return convertSort(cons);
                case "keywords": return convertKeywords(cons);
                case "parameterized-sort": return convertParametrizedSort(cons);
                case "parameterized-lex": convertParametrizedLex(cons);
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

        private static boolean isLexical(IValue value) {
            if (!(value instanceof IConstructor)) return false;
            return ((IConstructor) value).getName().equals("lex");
        }

        private static boolean isLayout(IValue value) {
            if (!(value instanceof IConstructor)) return false;
            return ((IConstructor) value).getName().equals("layouts");
        }

        private static boolean isStart(IValue value) {
            if (!(value instanceof IConstructor)) return false;
            return ((IConstructor) value).getName().equals("start");
        }

        // choice(Symbol def, set[Production] alternatives)
        private Rule convertChoice(IConstructor cons) throws Throwable {
            IValue headDef = cons.get("def");

            Symbol head = (Symbol) headDef.accept(this);
            Rule.Builder ruleBuilder = new Rule.Builder(Nonterminal.withName(head.getName()));

            Set<Object> alternatives = (Set<Object>) cons.get("alternatives").accept(this);

            List<PriorityLevel> priorityLevels = new ArrayList<>();
            addChildren(alternatives, priorityLevels);

            ruleBuilder.addPriorityLevels(priorityLevels);

            LayoutStrategy layoutStrategy;
            // Do not insert layout for the lexical or layout definitions
            if (isLexical(headDef) || isLayout(headDef)) {
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
        private Object convertParametrizedSort(IConstructor cons) {
            throw new RuntimeException("Parametrized sort is not supported yet.");
        }

        // parameterized-lex(str name, list[Symbol] parameters)
        private void convertParametrizedLex(IConstructor cons) {
            throw new RuntimeException("Parametrized lex is not supported yet.");
        }

        // prod(Symbol def, list[Symbol] symbols, set[Attr] attributes)
        private Sequence convertProd(IConstructor cons) throws Throwable {
            Sequence.Builder sequenceBuilder = new Sequence.Builder();

            // The label for the alternative
            Symbol def = (Symbol) cons.get("def").accept(this);
            if (def.getLabel() != null) {
                sequenceBuilder.setLabel(def.getLabel());
            }

            List<Symbol> symbols = (List<Symbol>) cons.get("symbols").accept(this);
            for (Symbol symbol : symbols) {
                if (!isLayout(symbol.getName())) {
                    sequenceBuilder.addSymbol(symbol);
                }
            }

            // attributes
            Set<Tuple<String, Object>> attributes = (Set<Tuple<String, Object>>) cons.get("attributes").accept(this);
            for (Tuple<String, Object> attribute : attributes) {
                sequenceBuilder.addAttribute(attribute.getFirst(), attribute.getSecond());
            }

            sequenceBuilder.addAttribute("prod", cons);

            if (isStart(cons.get("def"))) {
                assert start != null;
                // We need to store the start prod definition here for building the parse tree.
                start = start.copy().addAttribute("prod", cons).build();
            }

            return sequenceBuilder.build();
        }

        // priority(Symbol def, list[Production] choices)
        private List<PriorityLevel> convertPriority(IConstructor cons) throws Throwable {
            List<PriorityLevel> priorityLevels = new ArrayList<>();

            List<Object> choices = (List<Object>) cons.get("choices").accept(this);

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
            return Nonterminal.withName(nonterminalName);
        }

        // lit(str string)
        private void convertCilit(IConstructor cons) {
            throw new RuntimeException("Case-insensitive strings not supported yet");
        }

        // alt(set[Symbol] alternatives)
        private Alt convertAlt(IConstructor cons) throws Throwable {
            Set<Symbol> symbols = (Set<Symbol>) cons.get("alternatives").accept(this);
            return new Alt.Builder(new ArrayList<>(symbols)).addAttribute("definition", cons).build();
        }

        // opt(Symbol symbol)
        private Opt convertOpt(IConstructor cons) throws Throwable {
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            return new Opt.Builder(symbol).addAttribute("definition", cons).build();
        }

        // seq(list[Symbol] symbols)
        private Group convertSeq(IConstructor cons) throws Throwable {
            List<Symbol> symbols = (List<Symbol>) cons.get("symbols").accept(this);
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
            List<Symbol> separators = (List<Symbol>) cons.get("separators").accept(this);
            Plus.Builder plusBuilder = new Plus.Builder(symbol);
            for (Symbol separator : separators) {
                if (!isLayout(separator.getName())) {
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
            List<Symbol> separators = (List<Symbol>) cons.get("separators").accept(this);
            Star.Builder starBuilder = new Star.Builder(symbol);
            for (Symbol separator : separators) {
                if (!isLayout(separator.getName())) {
                    starBuilder.addSeparator(separator);
                }
            }
            starBuilder.addAttribute("definition", cons);
            return starBuilder.build();
        }

        // associativity(Symbol def, Associativity assoc, set[Production] alternatives)
        private Alternative convertAssociativity(IConstructor cons) throws Throwable {
            Associativity associativity = (Associativity) cons.get("assoc").accept(this);
            Set<Sequence> seqs = (Set<Sequence>) cons.get("alternatives").accept(this);
            Alternative.Builder alternativeBuilder = new Alternative.Builder();
            alternativeBuilder.addSequences(new ArrayList<>(seqs));
            alternativeBuilder.setAssociativity(associativity);
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
            start = new Start.Builder(nonterminal.getName()).addAttribute("definition", cons).build();
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
            List<CharRange> ranges = (List<CharRange>) cons.get("ranges").accept(this);
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
            Set<Object> conditions = (Set<Object>) cons.get("conditions").accept(this);
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
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            if (isRegex(symbol)) {
                return RegularExpressionCondition.follow(getRegex(symbol));
            } else {
                throw new RuntimeException("Must only be a regular expression: " + symbol);
            }
        }

        // not-follow(Symbol symbol)
        private RegularExpressionCondition convertNotFollow(IConstructor cons) throws Throwable {
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            if (isRegex(symbol)) {
                return RegularExpressionCondition.notFollow(getRegex(symbol));
            } else {
                throw new RuntimeException("Must only be a regular expression: " + symbol);
            }
        }

        // precede(Symbol symbol)
        private RegularExpressionCondition convertPrecede(IConstructor cons) throws Throwable {
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            if (isRegex(symbol)) {
                return RegularExpressionCondition.precede(getRegex(symbol));
            } else {
                throw new RuntimeException("Must only be a regular expression: " + symbol);
            }
        }

        // not-precede(Symbol symbol)
        private RegularExpressionCondition convertNotPrecede(IConstructor cons) throws Throwable {
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            if (isRegex(symbol)) {
                return RegularExpressionCondition.notPrecede(getRegex(symbol));
            } else {
                throw new RuntimeException("Must only be a regular expression: " + symbol);
            }
        }

        // delete(Symbol symbol)
        private RegularExpressionCondition convertDelete(IConstructor cons) throws Throwable {
            Symbol symbol = (Symbol) cons.get("symbol").accept(this);
            if (isRegex(symbol)) {
                return RegularExpressionCondition.notMatch(getRegex(symbol));
            } else {
                throw new RuntimeException("Must only be a regular expression: " + symbol);
            }
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
            throw new RuntimeException("Parameter is not supported yet");
        }

        // adt(str name, list[Symbol] parameters)
        private Tuple<String, List<Symbol>> convertADT(IConstructor cons) throws Throwable {
            String name = (String) cons.get("name").accept(this);
            List<Symbol> parameters = (List<Symbol>) cons.get("parameters").accept(this);
            return Tuple.of(name, parameters);
        }

        private boolean isLayout(String name) {
            return layout != null && layout.getName().equals(name);
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
