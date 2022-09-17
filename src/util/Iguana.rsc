module util::Iguana

extend ParseTree;
import IO;

// import lang::rascal::\syntax::Rascal;
import demo::lang::Pico::Syntax;

alias Parser[&T] = &T (str input, loc origin);

@javaClass{util.ParserGenerator}
java Parser[&T] createParser(type[&T] grammar);

void main() {
    Parser[Program] parser = createParser(#Program);

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
        println(parser(input));
    }
    catch ParseError(loc l): {
        printn("parse error <l>");
    } 
    catch Ambiguity(): {
        println("ambiguity at <l>");
    }
}