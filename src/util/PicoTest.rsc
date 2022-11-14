module util::PicoTest

import util::Iguana;
import lang::pico::\syntax::Main;
import ParseTree; 
import IO;

test bool picoExampleFac() = sameTreeTest(#start[Program], |std:///demo/lang/Pico/programs/Fac.pico|);
   
@synopsis{for reuse in all regression tests between the old Rascal parser and generated Iguana parsers}
bool sameTreeTest(type[&T <: Tree] symbol, loc file) {
   oldParser = parser(symbol); 
   newParser = createParser(expand(symbol));

   Tree old = oldParser(readFile(file), file); 
   Tree new = newParser(symbol, readFile(file)); 

   if (old != new) {
      if (prods(old) != prods(new)) {
        println("old has these unique productions:
                '  <prods(old) - prods(new)>
                'and new has these unique productions:
                '  <prods(new) - prods(old)>");
      }
      return false;
   }

   return true;
}

set[Production] prods(Tree t) = {p | /Production p := t};