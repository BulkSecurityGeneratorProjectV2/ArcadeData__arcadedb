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
package com.arcadedb.sql.method.misc;

import com.arcadedb.database.PDatabase;
import com.arcadedb.database.PIdentifiable;
import com.arcadedb.schema.OType;
import com.arcadedb.sql.executor.OCommandContext;

/**
 * Returns the value's OrientDB Type.
 *
 * @author Luca Garulli (l.garulli--(at)--orientdb.com)
 */
public class OSQLMethodType extends OAbstractSQLMethod {

  public static final String NAME = "type";

  public OSQLMethodType() {
    super(NAME);
  }

  @Override
  public Object execute( final Object iThis, final PIdentifiable iCurrentRecord, final OCommandContext iContext,
      final Object ioResult, final Object[] iParams) {
    if (ioResult == null)
      return null;

    final OType t = OType.getTypeByValue(ioResult);

    if (t != null)
      return t.toString();

    return null;
  }
}
