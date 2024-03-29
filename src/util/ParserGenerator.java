package util;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import org.iguana.grammar.Grammar;
import org.iguana.grammar.symbol.Start;
import org.iguana.grammar.symbol.Symbol;
import org.iguana.parser.IguanaParser;
import org.iguana.result.ParserResultOps;
import org.iguana.traversal.DefaultSPPFToParseTreeVisitor;
import org.iguana.utils.input.Input;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.RascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import java.util.Map;

public class ParserGenerator {
    private final IRascalValueFactory vf;
    private final TypeFactory tf;
    private final Type ftype;

    public ParserGenerator(IRascalValueFactory vf, TypeFactory tf) {
        this.vf = vf;
        this.tf = tf;
        Type typeOfTree = RascalValueFactory.Type.instantiate(Map.of(RascalValueFactory.TypeParam, RascalValueFactory.Tree));
        this.ftype = tf.functionType(RascalValueFactory.Tree, tf.tupleType(typeOfTree, tf.stringType(), tf.sourceLocationType()), tf.tupleEmpty());
    }

    public IValue createParser(IValue grammar) {
        RascalGrammarToIguanaGrammarConverter converter = new RascalGrammarToIguanaGrammarConverter();
        Grammar iguanaGrammar = converter.convert((IConstructor) grammar);
        IguanaParser parser = new IguanaParser(iguanaGrammar);

        return vf.function(ftype, (args, kwArgs) -> {
            // input is a string for now
            IConstructor type = (IConstructor) args[0]; // the reified type
            IConstructor symbol = (IConstructor) type.get(0); // the symbol

            Symbol start;
            try {
                start = (Symbol) symbol.accept(new RascalGrammarToIguanaGrammarConverter.ValueVisitor());
                // This is a hack for now, we need to change the Iguana symbol hierarchy to allow start accept a
                // symbol instead of string to make this unified.
                if (symbol.getName().equals("start")) {
                    start = Start.from(start.getName());
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            IString inputString = (IString) args[1];
            Input input = Input.fromString(inputString.getValue());
            parser.parse(input, start);

            ISourceLocation src = (ISourceLocation) args[2];

            RascalParseTreeBuilder parseTreeBuilder = new RascalParseTreeBuilder(vf, input, src);
            DefaultSPPFToParseTreeVisitor<ITree> visitor = new DefaultSPPFToParseTreeVisitor<>(parseTreeBuilder, input, false, new ParserResultOps());
            return parser.getSPPF().accept(visitor);
        });
    }
}
