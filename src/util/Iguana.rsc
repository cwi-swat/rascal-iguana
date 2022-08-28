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

start syntax Program
  = program: "begin" Declarations decls {Statement  ";"}* body "end" ;

syntax Declarations
  = "declare" {IdType ","}* decls ";" ;

syntax IdType = idtype: Id id ":" Type t;

syntax Statement
  = assign: Id var ":="  Expression val
  | cond: "if" Expression cond "then" {Statement ";"}*  thenPart "else" {Statement ";"}* elsePart "fi"
  | cond: "if" Expression cond "then" {Statement ";"}*  thenPart "fi"
  | loop: "while" Expression cond "do" {Statement ";"}* body "od"
  ;

syntax Type
  = natural:"natural"
  | string :"string"
  | nil    :"nil-type"
  ;

syntax Expression
  = id: Id name
  | strcon: String string
  | natcon: Natural natcon
  | bracket "(" Expression e ")"
  > left concat: Expression lhs "||" Expression rhs
  > left ( add: Expression lhs "+" Expression rhs
         | min: Expression lhs "-" Expression rhs
         )
  ;

lexical Id  = [a-z][a-z0-9]* !>> [a-z0-9];
lexical Natural = [0-9]+ ;
lexical String = "\"" ![\"]*  "\"";

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