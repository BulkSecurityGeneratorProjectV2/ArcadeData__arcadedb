/*
 * Copyright (c) - Arcade Data LTD (https://arcadedata.com)
 */

package com.arcadedb.exception;

import java.io.IOException;

public class DatabaseIsReadOnlyException extends ArcadeDBException {
  public DatabaseIsReadOnlyException(final String s) {
    super(s);
  }

  public DatabaseIsReadOnlyException(String s, IOException e) {
    super(s, e);
  }
}
