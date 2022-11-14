module util::PicoTest

import util::Iguana;
import lang::pico::\syntax::Main;
import ParseTree; 
import IO;

test bool picoExampleFac() {
   oldParser = parser(#start[Program]); 
   newParser = createParser(expand(#start[Program]));

   Tree old = oldParser(readFile(|std:///demo/lang/Pico/programs/Fac.pico|), |std:///demo/lang/Pico/programs/Fac.pico|); 
   Tree new = newParser(#start[Program], readFile(|std:///demo/lang/Pico/programs/Fac.pico|)); 

   return old == new;
}

@synopsis{for reuse in all regression tests between the old Rascal parser and generated Iguana parsers}
bool sameTreeTest(type[&T <: Tree] symbol, loc file) {
   oldParser = parser(symbol); 
   newParser = createParser(expand(symbol));

   Tree old = oldParser(readFile(file), file); 
   Tree new = newParser(symbol, readFile(file)); 

   return old == new;
}