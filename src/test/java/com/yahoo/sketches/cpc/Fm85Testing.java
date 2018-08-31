/*
 * Copyright 2018, Yahoo! Inc. Licensed under the terms of the
 * Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.cpc;

import static com.yahoo.sketches.Util.DEFAULT_UPDATE_SEED;
import static com.yahoo.sketches.hash.MurmurHash3.hash;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
/**
 * @author Lee Rhodes
 */
public class Fm85Testing {
  static final long golden64 = 0x9e3779b97f4a7c13L;  // the golden ratio
  long counter0 = 35538947;  // some arbitrary random number

  void getTwoRandomHashes(long[] twoHashes) {
    twoHashes[0] = counter0 += golden64;
    twoHashes = hash(twoHashes, DEFAULT_UPDATE_SEED);
  }

  //This is used for testing, especially of the merging code.

  @SuppressWarnings("null")
  static void assertSketchesEqual (Fm85 sk1, Fm85 sk2, boolean sk2WasMerged) {
    //  if (sk1.lgK != sk2.lgK) { printf ("%d vs %d\n", (int) sk1.lgK, (int) sk2.lgK); fflush (stdout); }
    assertEquals(sk1.lgK, sk2.lgK);
    //int k = 1 << sk1.lgK;
    assertEquals(sk1.isCompressed, sk2.isCompressed);
    assertEquals(sk1.numCoupons, sk2.numCoupons);
    assertEquals(sk1.windowOffset, sk2.windowOffset);
    assertEquals(sk1.cwLength, sk2.cwLength);
    assertEquals(sk1.csvLength, sk2.csvLength);
    assertEquals(sk1.numCompressedSurprisingValues, sk2.numCompressedSurprisingValues);
    PairTable table1 = sk1.surprisingValueTable;
    PairTable table2 = sk2.surprisingValueTable;
    if ((table1 != null) || (table2 != null)) {
      assertTrue((table1 != null) && (table2 != null));
      int numPairs1 = table1.numItems;
      int numPairs2 = table2.numItems;
      int[] pairs1 = PairTable.unwrappingGetItems(table1, numPairs1);
      int[] pairs2 = PairTable.unwrappingGetItems(table1, numPairs2);
      PairTable.introspectiveInsertionSort(pairs1, 0, numPairs1 - 1);
      PairTable.introspectiveInsertionSort(pairs2, 0, numPairs2 - 1);
      assertEquals(numPairs1, numPairs2);
      assertEquals(pairs1, pairs2);
    }

    byte[] win1 = sk1.slidingWindow;
    byte[] win2 = sk2.slidingWindow;

    if ((win1 != null) || (win2 != null)) {
      assert ((win1 != null) && (win2 != null));
      assertEquals(win1, win2);
    }

    int[] cwin1 = sk1.compressedWindow;
    int[] cwin2 = sk2.compressedWindow;

    if ((cwin1 != null) || (cwin2 != null)) {
      assert ((cwin1 != null) && (cwin2 != null));
      assertEquals(cwin1, cwin2);
    }

    int[] csv1 = sk1.compressedSurprisingValues;
    int[] csv2 = sk2.compressedSurprisingValues;

    if ((csv1 != null) || (csv2 != null)) {
      assert ((csv1 != null) && (csv2 != null));
      assertEquals(csv1, csv2);
    }

    int ficol1 = sk1.firstInterestingColumn;
    int ficol2 = sk2.firstInterestingColumn;

    if (sk2WasMerged) {
      assertTrue((sk1.mergeFlag == false) && (sk2.mergeFlag == true));
      // firstInterestingColumn is only updated occasionally while stream processing.
      // NB: While not very likely, it is possible for the difference to exceed 2.
      assert (((ficol1 + 0) == ficol2) ||
              ((ficol1 + 1) == ficol2) ||
              ((ficol1 + 2) == ficol2));
    }
    else {
      assertEquals(sk1.mergeFlag, sk2.mergeFlag);
      assertEquals(ficol1, ficol2);
      assertEquals(sk1.kxp, sk2.kxp);                 // TODO: deal with the
      assertEquals(sk1.hipEstAccum, sk2.hipEstAccum); // floating point issues
      assertEquals(sk1.hipErrAccum, sk2.hipErrAccum); // involving these three.
    }
  }

  /**
   * @param s value to print
   */
  static void println(String s) {
    System.out.println(s); //disable here
  }

}