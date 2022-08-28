module util::Iguana

extend ParseTree;
import IO;

import lang::rascal::\syntax::Rascal;

alias Parser[&T] = &T (str input, loc origin);

@javaClass{util.ParserGenerator}
java Parser[&T] createParser(type[&T] grammar);

void main() {
    Parser[Module] p = createParser(#Module);

    try {
     println(p("a"));
    }
    catch ParseError(loc l): {
        printn("parse error <l>");
    } 
    catch Ambiguity(): {
        println("ambiguity at <l>");
    }
}