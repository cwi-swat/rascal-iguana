module util::Iguana

extend ParseTree;
import IO;

syntax A = "a" D C | B* | {C ","}* | D+;

syntax B = "b";

syntax C = "c";

lexical D = "d";

layout L = " ";

alias Parser[&T] = &T (str input, loc origin);

@javaClass{util.ParserGenerator}
java Parser[&T] createParser(type[&T] grammar);

void main() {
    Parser[A] p = createParser(#A);

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