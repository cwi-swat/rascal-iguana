module util::PicoTest

import util::Iguana;
import lang::pico::\syntax::Main;
import ParseTree; 
import IO;

test bool picoExampleFac() {
   oldParser = parser(#start[Program]); 
   newParser = createParser(expand(#start[Program]));

   Tree old = oldParser(readFile(|std:///demo/lang/Pico/programs/Fac.pico|), |std:///demo/lang/Pico/programs/Fac.pico|); 
   Tree new = newParser(readFile(|std:///demo/lang/Pico/programs/Fac.pico|), |std:///demo/lang/Pico/programs/Fac.pico|); 

   return old == new;
}