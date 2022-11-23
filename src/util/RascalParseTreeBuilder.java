package util;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.type.Type;
import org.iguana.grammar.runtime.RuntimeRule;
import org.iguana.grammar.symbol.*;
import org.iguana.parsetree.ParseTreeBuilder;
import org.iguana.regex.Char;
import org.iguana.regex.CharRange;
import org.iguana.regex.Epsilon;
import org.iguana.regex.RegularExpression;
import org.iguana.utils.input.Input;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.RascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;

import java.util.List;
import java.util.Set;

public class RascalParseTreeBuilder implements ParseTreeBuilder<ITree> {

    private final IRascalValueFactory vf;
    private final Input input;
    private final ISourceLocation src;
    int line = 1;
    int col = 0;

    public RascalParseTreeBuilder(IRascalValueFactory vf, Input input, ISourceLocation src) {
        this.vf = vf;
        this.input = input;
        this.src = src;
    }

    @Override
    public ITree terminalNode(Terminal terminal, int leftExtent, int rightExtent) {
        RegularExpression regex = terminal.getRegularExpression();
        if (regex instanceof Epsilon) {
            return null;
        }
        if (regex instanceof CharRange || regex instanceof Char || regex instanceof org.iguana.regex.Alt<?>) {
            int ch = input.charAt(leftExtent);

            if (ch == '\n') {
                line++;
                col = 0;
            }

            return vf.character(ch);
        }
        throw new RuntimeException("Regex should be a char, char range or char class, but was: " + regex);
    }

    @Override
    public ITree nonterminalNode(RuntimeRule rule, List<ITree> children, int leftExtent, int rightExtent) {
        IConstructor prod = (IConstructor) rule.getAttributes().get("prod");
        return vf.appl(prod, vf.list(children.toArray(ITree[]::new)))
            .asWithKeywordParameters()
            .setParameter("src", vf.sourceLocation(src, leftExtent, rightExtent - leftExtent));
    }

    @Override
    public ITree ambiguityNode(Set<ITree> node) {
        return vf.amb(vf.set(node.toArray(ITree[]::new)));
    }

    @Override
    public ITree starNode(Star symbol, List<ITree> children, int leftExtent, int rightExtent) {
        return vf.appl(getRegularDefinition(symbol), children.toArray(ITree[]::new))
            .asWithKeywordParameters()
            .setParameter("src", vf.sourceLocation(src, leftExtent, rightExtent - leftExtent));
    }

    @Override
    public ITree plusNode(Plus symbol, List<ITree> children, int leftExtent, int rightExtent) {
        return vf.appl(getRegularDefinition(symbol), children.toArray(ITree[]::new))
            .asWithKeywordParameters()
            .setParameter("src", vf.sourceLocation(src, leftExtent, rightExtent - leftExtent));
    }

    @Override
    public ITree optNode(Opt symbol, ITree child, int leftExtent, int rightExtent) {
        return vf.appl(getRegularDefinition(symbol), new ITree[] { child })
            .asWithKeywordParameters()
            .setParameter("src", vf.sourceLocation(src, leftExtent, rightExtent - leftExtent));
    }

    @Override
    public ITree altNode(Alt symbol, ITree child, int leftExtent, int rightExtent) {
        return vf.appl(getRegularDefinition(symbol), new ITree[] { child })
            .asWithKeywordParameters()
            .setParameter("src", vf.sourceLocation(src, leftExtent, rightExtent - leftExtent));
    }

    @Override
    public ITree groupNode(Group symbol, List<ITree> children, int leftExtent, int rightExtent) {
        return vf.appl(getRegularDefinition(symbol), children.toArray(ITree[]::new))
            .asWithKeywordParameters()
            .setParameter("src", vf.sourceLocation(src, leftExtent, rightExtent - leftExtent))
            ;
    }

    @Override
    public ITree startNode(Start symbol, List<ITree> children, int leftExtent, int rightExtent) {
        IConstructor definition = (IConstructor) symbol.getAttributes().get("prod");
        return vf.appl(definition, children.toArray(ITree[]::new))
            .asWithKeywordParameters()
            .setParameter("src", vf.sourceLocation(src, leftExtent, rightExtent - leftExtent));
    }

    @Override
    public ITree errorNode(int leftExtent, int rightExtent) {
        throw new UnsupportedOperationException();
    }

    private IConstructor getRegularDefinition(Symbol symbol) {
        IConstructor definition = (IConstructor) symbol.getAttributes().get("definition");
        Type regular = RascalValueFactory.Production_Regular;
        return vf.constructor(regular, definition);
    }
}
