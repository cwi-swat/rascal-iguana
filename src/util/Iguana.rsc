module util::Iguana

extend ParseTree;
import IO;

// import lang::rascal::\syntax::Rascal;
import demo::lang::Pico::Syntax;

alias Parser[&T] = &T (str input, loc origin);

@javaClass{util.ParserGenerator}
java Parser[&T] createParser(type[&T] grammar);

void main() {
    Parser[Program] p = createParser(#Program);

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