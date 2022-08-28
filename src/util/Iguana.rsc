module util::Iguana

extend ParseTree;
import IO;

start syntax S
  = A
  ;

syntax A
  = "a" D C
  | B*
  | {C ","}*
  | {D "%"}+
  ;

syntax E
  = E "-"
  > right E "^" E
  > left (E "*" E
  |       E "/" E)
  > left E "+" E
  | "a"
  ;

syntax B = "b";

syntax C = "c";

lexical D = "d";

layout Layout = WhitespaceAndComment* !>> [\ \t\n\r%];

lexical WhitespaceAndComment
   = [\ \t\n\r]
   | @category="Comment" "%" ![%]+ "%"
   | @category="Comment" "%%" ![\n]* $
   ;

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