package util;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import org.iguana.grammar.Grammar;
import org.iguana.iggy.gen.IggyParser;
import org.iguana.parser.IguanaParser;
import org.iguana.parser.ParseErrorException;
import org.iguana.util.serialization.JsonSerializer;
import org.iguana.utils.input.Input;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.RascalValueFactory;

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
        System.out.println(iguanaGrammar);
        IguanaParser parser = new IguanaParser(iguanaGrammar);

        return vf.function(ftype, (args, kwArgs) -> {
            // input is a string for now 
            IString inputString = (IString) args[0];
            Input input = Input.fromString(inputString.getValue());
            try {
                parser.parse(input);
            } catch (ParseErrorException e) {
                throw e;
            }

            System.out.println(parser.getParseTree());

            // use the parser here to turn the string into a tree
            return vf.character('c')/*parse tree*/;
        });
    }

}
