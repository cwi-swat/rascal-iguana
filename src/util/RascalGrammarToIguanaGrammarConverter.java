package util;

import io.usethesource.vallang.*;
import io.usethesource.vallang.visitors.IValueVisitor;
import org.iguana.grammar.Grammar;
import org.iguana.grammar.slot.TerminalNodeType;
import org.iguana.grammar.symbol.*;
import org.iguana.regex.RegularExpression;
import org.iguana.regex.Seq;

import java.util.*;

public class RascalGrammarToIguanaGrammarConverter {

    public Grammar convert(IConstructor grammar) {
        Grammar.Builder grammarBuilder = new Grammar.Builder();
        IMap definitions = (IMap) grammar.get("definitions");

        Iterator<Map.Entry<IValue, IValue>> entryIterator = definitions.entryIterator();
        Set<String> layouts = new HashSet<>();
        while (entryIterator.hasNext()) {
            Map.Entry<IValue, IValue> next = entryIterator.next();
            IValue key = next.getKey();
            IValue value = next.getValue();
            System.out.println(key + " = " + value + " " + value.getType() + " " + value.getType().getName());

            try {
                Rule rule = (Rule) value.accept(new ValueVisitor(layouts));
                grammarBuilder.addRule(rule);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return grammarBuilder.build();
    }

    static class ValueVisitor implements IValueVisitor<Object, Throwable> {

        private final Set<String> layouts;

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
        public Object visitSourceLocation(ISourceLocation o) throws Throwable {
            throw new RuntimeException();
        }

        @Override
        public Object visitTuple(ITuple o) throws Throwable {
            throw new RuntimeException();
        }

        @Override
        public Object visitNode(INode o) throws Throwable {
            throw new RuntimeException();
        }

        private boolean isLayout(IValue value) {
            if (!(value instanceof IConstructor)) return false;
            return ((IConstructor) value).getName().equals("layouts");
        }

        private boolean isLexical(IValue value) {
            if (!(value instanceof IConstructor)) return false;
            return ((IConstructor) value).getName().equals("lex");
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
                    Rule.Builder ruleBuilder = new Rule.Builder(head);
                    Set<Object> children = (Set<Object>) visitedChildren.get(1);
                    for (Object object : children) {
                        if (object instanceof Sequence) {
                            ruleBuilder.addPriorityLevel(PriorityLevel.from(
                                Collections.singletonList(new Alternative.Builder().addSequence((Sequence) object).build())));
                        } else if (object instanceof PriorityLevel) {
                            ruleBuilder.addPriorityLevel((PriorityLevel) object);
                        }
                    }
                    LayoutStrategy layoutStrategy = LayoutStrategy.INHERITED;
                    if (isLexical(o.get(0))) {
                        layoutStrategy = LayoutStrategy.NO_LAYOUT;
                    }
                    return ruleBuilder.setLayoutStrategy(layoutStrategy).build();
                }
                case "prod": {
                    Sequence.Builder sequenceBuilder = new Sequence.Builder();
                    List<Symbol> symbols = (List<Symbol>) visitedChildren.get(1);
                    for (Symbol symbol : symbols) {
                        if (!layouts.contains(symbol.getName()))
                            sequenceBuilder.addSymbol(symbol);
                    }
                    return sequenceBuilder.build();
                }
                case "sort": {
                    String nonterminalName = (String) visitedChildren.get(0);
                    return new Nonterminal.Builder(nonterminalName).build();
                }
                case "lex": {
                    String nonterminalName = (String) visitedChildren.get(0);
                    return new Nonterminal.Builder(nonterminalName).build();
                }
                case "layouts": {
                    String nonterminalName = (String) visitedChildren.get(0);
                    return new Nonterminal.Builder(nonterminalName).build();
                }
                case "priority": {
                    PriorityLevel.Builder priorityLevelBuilder = new PriorityLevel.Builder();
                    List<Alternative> alternatives = (List<Alternative>) visitedChildren.get(1);
                    priorityLevelBuilder.addAlternatives(alternatives);
                    return priorityLevelBuilder.build();
                }
                case "assoc": {
                    Alternative.Builder alternativeBuilder = new Alternative.Builder();
                    return alternativeBuilder.build();
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
                case "iter-seps": {
                    Symbol symbol = (Symbol) o.get(0).accept(this);
                    List<Symbol> separators = (List<Symbol>) o.get(1).accept(this);
                    Plus.Builder plusBuilder = new Plus.Builder(symbol);
                    for (Symbol separator : separators) {
                        if (!layouts.contains(separator.getName())) {
                            plusBuilder.addSeparators(separator);
                        }
                    }
                    return plusBuilder.build();
                }
                case "iter-star-seps": {
                    Symbol symbol = (Symbol) o.get(0).accept(this);
                    List<Symbol> separators = (List<Symbol>) o.get(1).accept(this);
                    Star.Builder starBuilder = new Star.Builder(symbol);
                    for (Symbol separator : separators) {
                        if (!layouts.contains(separator.getName())) {
                            starBuilder.addSeparators(separator);
                        }
                    }
                    return starBuilder.build();
                }
                default:
                    throw new RuntimeException("Unknown name: " + o.getName());
            }
        }

        @Override
        public Object visitInteger(IInteger o) throws Throwable {
            throw new RuntimeException();
        }

        @Override
        public Object visitMap(IMap o) throws Throwable {
            throw new RuntimeException();
        }

        @Override
        public Object visitBoolean(IBool boolValue) throws Throwable {
            throw new RuntimeException();
        }

        @Override
        public Object visitExternal(IExternalValue externalValue) throws Throwable {
            throw new RuntimeException();
        }

        @Override
        public Object visitDateTime(IDateTime o) throws Throwable {
            throw new RuntimeException();
        }
    }
}
