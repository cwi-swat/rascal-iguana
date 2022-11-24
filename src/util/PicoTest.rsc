module util::PicoTest

import util::Iguana;
import lang::pico::\syntax::Main;
import ParseTree; 
import IO;
import Node;

test bool picoExampleFac() = sameTreeTest(#start[Program], |std:///demo/lang/Pico/programs/Fac.pico|);
   
@synopsis{for reuse in all regression tests between the old Rascal parser and generated Iguana parsers}
bool sameTreeTest(type[&T <: Tree] symbol, loc file) {
   oldParser = parser(symbol); 
   newParser = createParser(expand(symbol));

   Tree old = oldParser(readFile(file), file); 
   Tree new = newParser(symbol, readFile(file), file); 

   if (old != new) {
      if (prods(old) != prods(new)) {
        println("old has these unique productions:
                '  <prods(old) - prods(new)>
                'and new has these unique productions:
                '  <prods(new) - prods(old)>");
      }

      if (prodList(old) != prodList(new)) {
        println("order of rules is different
                '   <prodList(old)>
                '   <prodList(new)>");

      }
      if (chars(old) != chars(new)) {
         println("length old: <size(chars(old))>");
         println("length new: <size(chars(new))>");
         println("different chars:
                 '   <chars(old)>
                 '   <chars(new)>");
      }

      if (unsetRec(old) == new) {
         println("the only difference is source locations:
                 '  old: <locations(old)>
                 '  new: <locations(new)>");
      }

      if (locations(old) != locations(new)) {
         println("Unique old <locations(old) - locations(new)>");
         println("Unique new <locations(new) - locations(old)>");
      }
      return false;
   }

   return true;
}

list[loc] locations(Tree t) = [u@\loc?|nothing:///| | /Tree u := t];
list[int] chars(Tree t) = [i | /char(i) := t];
set[Production] prods(Tree t) = {p | /Production p := t};
list[Production] prodList(Tree t) = [p | /Production p := t];