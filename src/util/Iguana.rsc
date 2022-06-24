module util::Iguana

extend ParseTree;
import IO;

syntax A = "a";

alias Parser[&T] = &T (str input, loc origin);

@javaClass{util.ParserGenerator}
java Parser[&T] parser(type[&T] grammar); 

void main() {
    Parser[A] p = parser(#A);

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