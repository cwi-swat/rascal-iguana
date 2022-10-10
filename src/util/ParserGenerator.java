package util;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import org.iguana.grammar.Grammar;
import org.iguana.grammar.symbol.Symbol;
import org.iguana.parser.IguanaParser;
import org.iguana.result.ParserResultOps;
import org.iguana.traversal.DefaultSPPFToParseTreeVisitor;
import org.iguana.utils.input.Input;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.RascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;

public class ParserGenerator {
    private final IRascalValueFactory vf;
    private final TypeFactory tf;
    private final Type ftype;

    public ParserGenerator(IRascalValueFactory vf, TypeFactory tf) {
        this.vf = vf;
        this.tf = tf;
        this.ftype = tf.functionType(RascalValueFactory.Tree, tf.tupleType(RascalValueFactory.Tree, tf.stringType()), tf.tupleEmpty());
    }

    public IValue createParser(IValue grammar) {
        RascalGrammarToIguanaGrammarConverter converter = new RascalGrammarToIguanaGrammarConverter();
        Grammar iguanaGrammar = converter.convert((IConstructor) grammar);
        System.out.println(iguanaGrammar);
        IguanaParser parser = new IguanaParser(iguanaGrammar);

        return vf.function(ftype, (args, kwArgs) -> {
            // input is a string for now
            IConstructor symbol = (IConstructor) args[0];
            Symbol start;
            try {
                start = (Symbol) symbol.accept(new RascalGrammarToIguanaGrammarConverter.ValueVisitor());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            IString inputString = (IString) args[1];
            Input input = Input.fromString(inputString.getValue());
            parser.parse(input, start);

            RascalParseTreeBuilder parseTreeBuilder = new RascalParseTreeBuilder(vf, input);
            DefaultSPPFToParseTreeVisitor<ITree> visitor = new DefaultSPPFToParseTreeVisitor<>(parseTreeBuilder, input, false, new ParserResultOps());
            return parser.getSPPF().accept(visitor);
        });
    }
}
