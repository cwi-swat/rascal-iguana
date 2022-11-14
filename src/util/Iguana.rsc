module util::Iguana

extend ParseTree;
import IO;
import Grammar;
import lang::rascal::grammar::definition::Literals;
import lang::rascal::grammar::definition::Parameters;

import lang::rascal::\syntax::Rascal;
//import demo::lang::Pico::Syntax;

alias Parser[&T] = &T (&T startSymbol, value input, loc origin);

@javaClass{util.ParserGenerator}
java Parser[&T] createParser(type[&T] grammar);

&T expand(&T t) {
    Grammar g = expandParameterizedSymbols(literals(grammar(t)));
    return type(t.symbol, g.rules);
}

void main() {
    Parser[Module] parser = createParser(expand(#Module));

    try {
        str inputPico =
            "begin
            '  declare
            '     x : natural,
            '     n : natural;
            '  x := 4;
            '  n := 10;
            '  while n do n := n - 1; x := x + x od
            'end
            ";
        str rascalInput =
            "module Syntax
            '
            'extend lang::std::Layout;
            'extend lang::std::Id;
            '
            'start syntax Machine = machine: State+ states;
            'syntax State = @Foldable state: \"state\" Id name Trans* out;
            'syntax Trans = trans: Id event \":\" Id to;
            ";
        iprintln(parser(#start[Module].symbol, rascalInput));
    }
    catch ParseError(loc l): {
        printn("parse error <l>");
    } 
    catch Ambiguity(): {
        println("ambiguity at <l>");
    }
}

