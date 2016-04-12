/*
 * Copyright 2015, 2016 Tagir Valeev
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
package one.util.huntbugs.analysis;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * @author lan
 *
 */
public class AnalysisOptions {
    public boolean addBootClassPath = true;
    public int maxMethodSize = 8000;
    public int minScore = 1;
    public int loopTraversalIterations = 5;

    public void set(String name, String valueString) {
        Objects.requireNonNull(valueString);
        try {
            Field field = getClass().getField(name);
            Class<?> type = field.getType();
            Object value;
            if (type == int.class) {
                try {
                    value = Integer.valueOf(valueString);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid value " + valueString + " for option " + name
                        + " (integer expected)");
                }
            } else if (type == boolean.class) {
                value = Boolean.valueOf(valueString);
            } else if (type == String.class) {
                value = valueString;
            } else
                throw new InternalError("Unexpected field type: " + type);
            field.set(this, value);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Unknown option: " + name);
        } catch (SecurityException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    public void report(PrintStream out) {
        for(Field field : getClass().getFields()) {
            String type = field.getType().getSimpleName();
            try {
                out.println(field.getName()+" ("+type+") = "+field.get(this));
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new InternalError(e);
            }
        }
    }
}
