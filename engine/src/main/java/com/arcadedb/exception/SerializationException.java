/*
 * Copyright (c) - Arcade Data LTD (https://arcadedata.com)
 */

package com.arcadedb.exception;

import java.io.IOException;

public class SerializationException extends ArcadeDBException {
  public SerializationException(final String s) {
    super(s);
  }

  public SerializationException(String s, IOException e) {
    super(s, e);
  }
}
