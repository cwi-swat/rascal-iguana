module util::Iguana

extend ParseTree;
import IO;
import Grammar;
import lang::rascal::grammar::definition::Literals;


// import lang::rascal::\syntax::Rascal;
//import demo::lang::Pico::Syntax;

start syntax A = B*;
syntax B = "b";

alias Parser[&T] = &T (str input, loc origin);


@javaClass{util.ParserGenerator}
java Parser[&T] createParser(type[&T] grammar);

&T expand(&T t) {
    Grammar g = literals(grammar(t));
    return type(t.symbol, g.rules);
}

void main() {
    Parser[A] parser = createParser(expand(#A));

    try {
        str input =
            "begin
            '  declare
            '     x : natural,
            '     n : natural;
            '  x := 4;
            '  n := 10;
            '  while n do n := n - 1; x := x + x od
            'end
            ";
        println(parser("b"));
    }
    catch ParseError(loc l): {
        printn("parse error <l>");
    } 
    catch Ambiguity(): {
        println("ambiguity at <l>");
    }
}