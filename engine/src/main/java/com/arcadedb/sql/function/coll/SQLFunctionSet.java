/*
 *
 *  *  Copyright 2010-2016 OrientDB LTD (http://orientdb.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://orientdb.com
 *
 */
package com.arcadedb.sql.function.coll;

import com.arcadedb.database.Document;
import com.arcadedb.database.Identifiable;
import com.arcadedb.sql.executor.CommandContext;
import com.arcadedb.sql.executor.MultiValue;

import java.util.HashSet;
import java.util.Set;

/**
 * This operator add an item in a set. The set doesn't accept duplicates, so adding multiple times the same value has no effect: the
 * value is contained only once.
 *
 * @author Luca Garulli (l.garulli--(at)--orientdb.com)
 */
public class SQLFunctionSet extends SQLFunctionMultiValueAbstract<Set<Object>> {
  public static final String NAME = "set";

  public SQLFunctionSet() {
    super(NAME, 1, -1);
  }

  public Object execute( Object iThis, final Identifiable iCurrentRecord, Object iCurrentResult, final Object[] iParams,
      CommandContext iContext) {
    if (iParams.length > 1)
      // IN LINE MODE
      context = new HashSet<Object>();

    for (Object value : iParams) {
      if (value != null) {
        if (iParams.length == 1 && context == null)
          // AGGREGATION MODE (STATEFULL)
          context = new HashSet<Object>();

        if (value instanceof Document)
          context.add(value);
        else
          MultiValue.add(context, value);
      }
    }

    return context;
  }

  public String getSyntax() {
    return "set(<value>*)";
  }

  public boolean aggregateResults(final Object[] configuredParameters) {
    return configuredParameters.length == 1;
  }

}
