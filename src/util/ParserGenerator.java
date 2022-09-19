package util;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import org.iguana.grammar.Grammar;
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
        this.ftype = tf.functionType(RascalValueFactory.Tree, tf.tupleType(tf.stringType()), tf.tupleEmpty());
    }

    public IValue createParser(IValue grammar) {
        RascalGrammarToIguanaGrammarConverter converter = new RascalGrammarToIguanaGrammarConverter();
        Grammar iguanaGrammar = converter.convert((IConstructor) grammar);
        IguanaParser parser = new IguanaParser(iguanaGrammar);

        return vf.function(ftype, (args, kwArgs) -> {
            // input is a string for now 
            IString inputString = (IString) args[0];
            Input input = Input.fromString(inputString.getValue());
            parser.parse(input);

            RascalParseTreeBuilder parseTreeBuilder = new RascalParseTreeBuilder(vf, input);
            DefaultSPPFToParseTreeVisitor<ITree> visitor = new DefaultSPPFToParseTreeVisitor<>(parseTreeBuilder, input, false, new ParserResultOps());
            ITree result = parser.getSPPF().accept(visitor);
            System.out.println(result);
            return result;
        });
    }
}
