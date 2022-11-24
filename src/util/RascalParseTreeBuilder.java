package util;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISourceLocation;
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

import static util.IValueUtils.isLiteral;

public class RascalParseTreeBuilder implements ParseTreeBuilder<ITree> {

    private final IRascalValueFactory vf;
    private final Input input;
    private final ISourceLocation src;

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
            return vf.character(input.charAt(leftExtent));
        }
        throw new RuntimeException("Regex should be a char, char range or char class, but was: " + regex);
    }

    @Override
    public ITree nonterminalNode(RuntimeRule rule, List<ITree> children, int leftExtent, int rightExtent) {
        IConstructor prod = (IConstructor) rule.getAttributes().get("prod");
        // Literals don't get the source annotation in Rascal.
        if (isLiteral(prod.get("def"))) {
            return vf.appl(prod, vf.list(children.toArray(ITree[]::new)));
        } else {
            return (ITree) vf.appl(prod, vf.list(children.toArray(ITree[]::new)))
                .asWithKeywordParameters()
                .setParameter("src", getSourceLocation(leftExtent, rightExtent));
        }
    }

    @Override
    public ITree ambiguityNode(Set<ITree> node) {
        return vf.amb(vf.set(node.toArray(ITree[]::new)));
    }

    @Override
    public ITree starNode(Star symbol, List<ITree> children, int leftExtent, int rightExtent) {
        return (ITree) vf.appl(getRegularDefinition(symbol), children.toArray(ITree[]::new))
            .asWithKeywordParameters()
            .setParameter("src", getSourceLocation(leftExtent, rightExtent));
    }

    @Override
    public ITree plusNode(Plus symbol, List<ITree> children, int leftExtent, int rightExtent) {
        return (ITree) vf.appl(getRegularDefinition(symbol), children.toArray(ITree[]::new))
            .asWithKeywordParameters()
            .setParameter("src", getSourceLocation(leftExtent, rightExtent));
    }

    @Override
    public ITree optNode(Opt symbol, ITree child, int leftExtent, int rightExtent) {
        return (ITree) vf.appl(getRegularDefinition(symbol), new ITree[] { child })
            .asWithKeywordParameters()
            .setParameter("src", getSourceLocation(leftExtent, rightExtent));
    }

    @Override
    public ITree altNode(Alt symbol, ITree child, int leftExtent, int rightExtent) {
        return (ITree) vf.appl(getRegularDefinition(symbol), new ITree[] { child })
            .asWithKeywordParameters()
            .setParameter("src", getSourceLocation(leftExtent, rightExtent));
    }

    @Override
    public ITree groupNode(Group symbol, List<ITree> children, int leftExtent, int rightExtent) {
        return (ITree) vf.appl(getRegularDefinition(symbol), children.toArray(ITree[]::new))
            .asWithKeywordParameters()
            .setParameter("src", getSourceLocation(leftExtent, rightExtent));
    }

    @Override
    public ITree startNode(Start symbol, List<ITree> children, int leftExtent, int rightExtent) {
        IConstructor definition = (IConstructor) symbol.getAttributes().get("prod");
        return (ITree) vf.appl(definition, children.toArray(ITree[]::new))
            .asWithKeywordParameters()
            .setParameter("src", getSourceLocation(leftExtent, rightExtent));
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

    private ISourceLocation getSourceLocation(int leftExtent, int rightExtent) {
        int beginLine = input.getLineNumber(leftExtent);
        int endLine = input.getLineNumber(rightExtent);
        // In Rascal columns start from 0, while in Iguana, they start from 1, that's why we decrement the returned
        // column number from iguana by 1.
        int beginColumn = input.getColumnNumber(leftExtent) - 1;
        int endColumn = input.getColumnNumber(rightExtent) - 1;
        return vf.sourceLocation(src, leftExtent, rightExtent - leftExtent, beginLine, endLine, beginColumn, endColumn);
    }

}
