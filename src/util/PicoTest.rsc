module util::PicoTest

import lang::pico::\syntax::Main;
import util::Diagnose;

test bool allPicoExamples() {
   for (loc ex <- |project://rascal-iguana/examples/pico|.ls) {
      if (!sameTreeTest(#start[Program], ex)) {
         return false;
      }
   }

   return true;
}


   
