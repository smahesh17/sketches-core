/*
 * Copyright 2017, Yahoo! Inc. Licensed under the terms of the
 * Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.hll;

import static com.yahoo.memory.UnsafeUtil.unsafe;
import static com.yahoo.sketches.hll.HllUtil.AUX_TOKEN;
import static com.yahoo.sketches.hll.HllUtil.hiNibbleMask;
import static com.yahoo.sketches.hll.HllUtil.loNibbleMask;
import static com.yahoo.sketches.hll.HllUtil.noWriteAccess;
import static com.yahoo.sketches.hll.PreambleUtil.HLL_BYTE_ARR_START;
import static com.yahoo.sketches.hll.PreambleUtil.extractAuxCount;
import static com.yahoo.sketches.hll.PreambleUtil.extractCompactFlag;
import static com.yahoo.sketches.hll.PreambleUtil.insertCompactFlag;

import com.yahoo.memory.Memory;
import com.yahoo.memory.WritableMemory;

/**
 * @author Lee Rhodes
 */
class DirectHll4Array extends DirectHllArray {

  //Called by HllSketch.writableWrap(), DirectCouponList.promoteListOrSetToHll
  DirectHll4Array(final int lgConfigK, final WritableMemory wmem) {
    super(lgConfigK, TgtHllType.HLL_4, wmem);
    if (extractAuxCount(memObj, memAdd) > 0) {
      putAuxHashMap(new DirectAuxHashMap(this, false), false);
    }
  }

  //Called by HllSketch.wrap(Memory)
  DirectHll4Array(final int lgConfigK, final Memory mem) {
    super(lgConfigK, TgtHllType.HLL_4, mem);
    final int auxCount = extractAuxCount(memObj, memAdd);
    if (auxCount > 0) {
      final boolean compact = extractCompactFlag(memObj, memAdd);
      final AuxHashMap auxHashMap;
      if (compact) {
        final int auxStart = getAuxStart();
        auxHashMap = HeapAuxHashMap.heapify(mem, auxStart, lgConfigK, auxCount, compact);
      } else {
        auxHashMap =  new DirectAuxHashMap(this, false); //not compact
      }
      putAuxHashMap(auxHashMap, compact);
    }
  }

  @Override
  HllSketchImpl copy() {
    return Hll4Array.heapify(mem);
  }

  @Override
  HllSketchImpl couponUpdate(final int coupon) {
    if (wmem == null) { noWriteAccess(); }
    final int newValue = HllUtil.getValue(coupon);
    if (newValue <= getCurMin()) {
      return this; // super quick rejection; only works for large N
    }
    final int configKmask = (1 << getLgConfigK()) - 1;
    final int slotNo = HllUtil.getLow26(coupon) & configKmask;
    internalHll4Update(this, slotNo, newValue);
    return this;
  }

  @Override
  int getHllByteArrBytes() {
    return hll4ArrBytes(lgConfigK);
  }

  @Override
  PairIterator getIterator() {
    return new DirectHll4Iterator(1 << lgConfigK);
  }

  @Override
  int getSlot(final int slotNo) {
    final long unsafeOffset = memAdd + HLL_BYTE_ARR_START + (slotNo >>> 1);
    int theByte = unsafe.getByte(memObj, unsafeOffset);
    if ((slotNo & 1) > 0) { //odd?
      theByte >>>= 4;
    }
    return theByte & loNibbleMask;
  }

  @Override
  void putSlot(final int slotNo, final int newValue) {
    final long unsafeOffset = memAdd + HLL_BYTE_ARR_START + (slotNo >>> 1);
    final int oldValue = unsafe.getByte(memObj, unsafeOffset);
    final byte value = ((slotNo & 1) == 0) //even?
        ? (byte) ((oldValue & hiNibbleMask) | (newValue & loNibbleMask)) //set low nibble
        : (byte) ((oldValue & loNibbleMask) | ((newValue << 4) & hiNibbleMask)); //set high nibble
    unsafe.putByte(memObj, unsafeOffset, value);
  }


  @Override
  byte[] toCompactByteArray() {
    final boolean memIsCompact = extractCompactFlag(memObj, memAdd);
    final int totBytes = getCompactSerializationBytes();
    final byte[] byteArr = new byte[totBytes];
    final WritableMemory memOut = WritableMemory.wrap(byteArr);
    if (memIsCompact) {
      mem.copyTo(0, memOut, 0, totBytes);
      return byteArr;
    } else {
      final int auxStart = getAuxStart();
      mem.copyTo(0, memOut, 0, auxStart);
      if (auxHashMap != null) {
        insertAux(this, memOut, true);
      }
      //set the compact flag
      final Object memOutObj = memOut.getArray();
      final long memOutAdd = memOut.getCumulativeOffset(0L);
      insertCompactFlag(memOutObj, memOutAdd, true);
      return byteArr;
    }
  }

  @Override
  byte[] toUpdatableByteArray() {
    final boolean memIsCompact = extractCompactFlag(memObj, memAdd);
    final int totBytes = getUpdatableSerializationBytes();
    final byte[] byteArr = new byte[totBytes];
    final WritableMemory memOut = WritableMemory.wrap(byteArr);
    if (memIsCompact) {
      final int auxStart = getAuxStart();
      mem.copyTo(0, memOut, 0, auxStart);
      if (auxHashMap != null) {
        insertAux(this, memOut, false);
      }
      //clear the compact flag
      final Object memOutObj = memOut.getArray();
      final long memOutAdd = memOut.getCumulativeOffset(0L);
      insertCompactFlag(memOutObj, memOutAdd, false);
      return byteArr;
    } else {
      mem.copyTo(0, memOut, 0, totBytes);
      return byteArr;
    }
  }

  //ITERATOR

  final class DirectHll4Iterator extends HllPairIterator {

    DirectHll4Iterator(final int lengthPairs) {
      super(lengthPairs);
    }

    @Override
    int value() {
      final int nib = DirectHll4Array.this.getSlot(index);
      if (nib == AUX_TOKEN) {
        final AuxHashMap auxHashMap = getAuxHashMap();
        return auxHashMap.mustFindValueFor(index); //auxHashMap cannot be null here
      } else {
        return nib + getCurMin();
      }
    }
  }

}