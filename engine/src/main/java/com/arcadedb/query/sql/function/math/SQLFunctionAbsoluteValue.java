/*
 * Copyright © 2021-present Arcade Data Ltd (info@arcadedata.com)
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
 *
 * SPDX-FileCopyrightText: 2021-present Arcade Data Ltd (info@arcadedata.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package com.arcadedb.query.sql.function.math;

import com.arcadedb.database.Identifiable;
import com.arcadedb.query.sql.executor.CommandContext;

import java.math.*;

/**
 * Evaluates the absolute value for numeric types.  The argument must be a
 * BigDecimal, BigInteger, Integer, Long, Double or a Float, or null.  If
 * null is passed in the result will be null.  Otherwise the result will
 * be the mathematical absolute value of the argument passed in and will be
 * of the same type that was passed in.
 *
 * @author Michael MacFadden
 */
public class SQLFunctionAbsoluteValue extends SQLFunctionMathAbstract {
  public static final String NAME = "abs";
  private             Object result;

  public SQLFunctionAbsoluteValue() {
    super(NAME, 1, 1);
  }

  public Object execute( Object iThis, final Identifiable iRecord, final Object iCurrentResult,
      final Object[] iParams, CommandContext iContext) {
    Object inputValue = iParams[0];

    if (inputValue == null) {
      result = null;
    } else if (inputValue instanceof BigDecimal) {
      result = ((BigDecimal) inputValue).abs();
    } else if (inputValue instanceof BigInteger) {
      result = ((BigInteger) inputValue).abs();
    } else if (inputValue instanceof Integer) {
      result = Math.abs((Integer) inputValue);
    } else if (inputValue instanceof Long) {
      result = Math.abs((Long) inputValue);
    } else if (inputValue instanceof Short) {
      result = (short) Math.abs((Short) inputValue);
    } else if (inputValue instanceof Double) {
      result = Math.abs((Double) inputValue);
    } else if (inputValue instanceof Float) {
      result = Math.abs((Float) inputValue);
    } else {
      throw new IllegalArgumentException("Argument to absolute value must be a number.");
    }

    return getResult();
  }

  public boolean aggregateResults() {
    return false;
  }

  public String getSyntax() {
    return "abs(<number>)";
  }

  @Override
  public Object getResult() {
    return result;
  }
}
