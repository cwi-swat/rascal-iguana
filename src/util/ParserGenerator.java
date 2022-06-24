package util;

import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.RascalValueFactory;

import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;

public class ParserGenerator {
    private final IRascalValueFactory vf;
    private final TypeFactory tf;
    private final Type ftype;

    public ParserGenerator(IRascalValueFactory vf, TypeFactory tf) {
        this.vf = vf;
        this.tf = tf;
        this.ftype = tf.functionType(RascalValueFactory.Tree, tf.tupleType(tf.stringType()), tf.tupleEmpty());
    }

    public IValue parser(IValue grammar) {
        // generate the parser here
        Object parser = null;

        return vf.function(ftype, (args, kwArgs) -> {
            // input is a string for now 
            IString input = (IString) args[0];

            // use the parser here to turn the string into a tree
            System.err.println(parser);

            return null/*parse tree*/;
        });
    }

}
