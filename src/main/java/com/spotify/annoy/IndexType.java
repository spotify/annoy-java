package com.spotify.annoy;

public enum IndexType {
  ANGULAR(4, 12),
  EUCLIDEAN(8, 16),
  DOT(4, 16);

  private final int offset;
  private final int kNodeHeaderStyle;

  IndexType(int offset, int kNodeHeaderStyle) {
    this.offset = offset;
    this.kNodeHeaderStyle = kNodeHeaderStyle;
  }

  public int getOffset() {
    return offset;
  }

  public int getkNodeHeaderStyle() {
    return kNodeHeaderStyle;
  }
}
