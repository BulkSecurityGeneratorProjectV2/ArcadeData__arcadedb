/*
 * Copyright 2013 Geomatys
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arcadedb.sql.function.misc;

import com.arcadedb.database.PDatabase;
import com.arcadedb.database.PIdentifiable;
import com.arcadedb.exception.PCommandSQLParsingException;
import com.arcadedb.sql.executor.OCommandContext;
import com.arcadedb.sql.function.OSQLFunctionAbstract;

import java.util.Base64;

/**
 * Encode a string in various format (only base64 for now)
 *
 * @author Johann Sorel (Geomatys)
 */
public class OSQLFunctionDecode extends OSQLFunctionAbstract {

  public static final String NAME = "decode";

  /**
   * Get the date at construction to have the same date for all the iteration.
   */
  public OSQLFunctionDecode() {
    super(NAME, 2, 2);
  }

  @Override
  public Object execute( final Object iThis, PIdentifiable iCurrentRecord, Object iCurrentResult,
      final Object[] iParams, final OCommandContext iContext) {

    final String candidate = iParams[0].toString();
    final String format = iParams[1].toString();

    if (OSQLFunctionEncode.FORMAT_BASE64.equalsIgnoreCase(format)) {
      return Base64.getDecoder().decode(candidate);
    } else {
      throw new PCommandSQLParsingException("Unknowned format :" + format);
    }
  }

  @Override
  public String getSyntax() {
    return "decode(<binaryfield>, <format>)";
  }
}
