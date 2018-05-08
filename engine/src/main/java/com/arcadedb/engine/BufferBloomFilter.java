package com.arcadedb.engine;

import com.arcadedb.database.Binary;

public class BufferBloomFilter {
  private final Binary buffer;
  private final int    hashSeed;
  private final int    capacity;

  public BufferBloomFilter(final Binary buffer, final int slots, final int hashSeed) {
    if (slots % 8 > 0)
      throw new IllegalArgumentException("Slots must be a multiplier of 8");
    this.buffer = buffer;
    this.hashSeed = hashSeed;
    this.capacity = slots;
  }

  public void add(final int value) {
    final byte[] b = new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
    final int hash = MurmurHash.hash32(b, 4, hashSeed);
    final int h = hash != Integer.MIN_VALUE ? Math.abs(hash) : Integer.MAX_VALUE;

    final int bit2change = h > capacity ? h % capacity : h;
    final int byte2change = bit2change / 8;
    final int bitInByte2change = bit2change % 8;

    final byte v = buffer.getByte(byte2change);
    buffer.putByte(byte2change, (byte) (v | (1 << bitInByte2change)));
  }

  public boolean mightContain(final int value) {
    final byte[] b = new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
    final int hash = MurmurHash.hash32(b, 4, hashSeed);
    final int h = hash != Integer.MIN_VALUE ? Math.abs(hash) : Integer.MAX_VALUE;

    final int bit2change = h > capacity ? h % capacity : h;
    final int byte2change = bit2change / 8;
    final int bitInByte2change = bit2change % 8;

    final byte v = buffer.getByte(byte2change);
    final boolean found = ((v >> bitInByte2change) & 1) == 1;
    return found;
  }
}