package util;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.type.Type;
import org.iguana.grammar.runtime.RuntimeRule;
import org.iguana.grammar.symbol.Symbol;
import org.iguana.grammar.symbol.Terminal;
import org.iguana.parsetree.ParseTreeBuilder;
import org.iguana.regex.Char;
import org.iguana.regex.CharRange;
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

    public RascalParseTreeBuilder(IRascalValueFactory vf, Input input) {
        this.vf = vf;
        this.input = input;
    }

    @Override
    public ITree terminalNode(Terminal terminal, int leftExtent, int rightExtent) {
        RegularExpression regex = terminal.getRegularExpression();
        if (regex instanceof CharRange || regex instanceof Char || regex instanceof org.iguana.regex.Alt<?>) {
            return vf.character(input.charAt(leftExtent));
        } else {
            throw new RuntimeException("Regex should be a char, char range or char class");
        }
    }

    @Override
    public ITree nonterminalNode(RuntimeRule rule, List<ITree> children, int leftExtent, int rightExtent) {
        IConstructor prod = (IConstructor) rule.getAttributes().get("prod");
        if (prod == null) throw new RuntimeException("prod should not be null for");
        return vf.appl(prod, vf.list(children.toArray(ITree[]::new)));
    }

    @Override
    public ITree ambiguityNode(Set<ITree> node) {
        return vf.amb(vf.set(node.toArray(ITree[]::new)));
    }

    @Override
    public ITree metaSymbolNode(Symbol symbol, List<ITree> children, int leftExtent, int rightExtent) {
        IConstructor definition = (IConstructor) symbol.getAttributes().get("definition");
        Type regular = RascalValueFactory.Production_Regular;
        IConstructor prod = vf.constructor(regular, definition);
        return vf.appl(prod, children.toArray(ITree[]::new));
    }
}
