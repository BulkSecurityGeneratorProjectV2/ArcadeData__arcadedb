/*
 * Copyright 2010-2016 OrientDB LTD (http://orientdb.com)
 * Copyright 2013 Geomatys.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arcadedb.sql.function.text;

import com.arcadedb.database.PDatabase;
import com.arcadedb.database.PIdentifiable;
import com.arcadedb.sql.executor.OCommandContext;
import com.arcadedb.sql.method.misc.OAbstractSQLMethod;
import com.arcadedb.utility.PFileUtils;

/**
 * Appends strings. Acts as a concatenation.
 *
 * @author Johann Sorel (Geomatys)
 * @author Luca Garulli (l.garulli--(at)--orientdb.com)
 */
public class OSQLMethodAppend extends OAbstractSQLMethod {

  public static final String NAME = "append";

  public OSQLMethodAppend() {
    super(NAME, 1, -1);
  }

  @Override
  public String getSyntax() {
    return "append([<value|expression|field>]*)";
  }

  @Override
  public Object execute( final Object iThis, PIdentifiable iCurrentRecord, OCommandContext iContext,
      Object ioResult, Object[] iParams) {
    if (iThis == null || iParams[0] == null)
      return iThis;

    final StringBuilder buffer = new StringBuilder(iThis.toString());
    for (int i = 0; i < iParams.length; ++i) {
      if (iParams[i] != null) {
        buffer.append(PFileUtils.getStringContent(iParams[i]));
      }
    }

    return buffer.toString();
  }

}
