module util::RascalLibraryTest

import lang::rascal::\syntax::Rascal;
import util::Diagnose;
import util::FileSystem;
import util::Progress;

test bool allLibraryExamples() {
   s = spinner();

   for (loc ex <- find(|std:///|, "rsc")) {
      s("<ex>");
      if (!sameTreeTest(#start[Module], ex)) {
         return false;
      }
   }

   return true;
}


   
