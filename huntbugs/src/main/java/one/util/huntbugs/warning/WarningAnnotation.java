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
package one.util.huntbugs.warning;

import java.util.Objects;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.Variable;

/**
 * @author lan
 *
 */
public class WarningAnnotation<T> {
    private final String role;
    private final T value;
    
    public WarningAnnotation(String role, T value) {
        super();
        this.role = role;
        this.value = value;
    }

    public String getRole() {
        return role;
    }

    public T getValue() {
        return value;
    }
    
    @Override
    public int hashCode() {
        return role.hashCode() * 31 + ((value == null) ? 0 : value.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        WarningAnnotation<?> other = (WarningAnnotation<?>) obj;
        return Objects.equals(role, other.role) && Objects.equals(value, other.value);
    }

    public static class Location {
        final int offset;
        final int sourceLine;

        public Location(int offset, int sourceLine) {
            this.offset = offset;
            this.sourceLine = sourceLine;
        }
        
        @Override
        public String toString() {
            if(sourceLine != -1)
                return "byteCode: "+offset+"; line: "+sourceLine;
            return "byteCode: "+offset;
        }
    }
    
    @Override
    public String toString() {
        return getRole()+": "+getValue();
    }
    
    public static WarningAnnotation<String> forType(TypeReference type) {
        return new WarningAnnotation<>("TYPE", type.getFullName());
    }

    public static WarningAnnotation<String> forMethod(MethodReference method) {
        return new WarningAnnotation<>("METHOD", method.getFullName());
    }
    
    public static WarningAnnotation<?> forReturnValue(MethodReference method) {
        return new WarningAnnotation<>("RETURN_VALUE_OF", method.getFullName());
	}

    public static WarningAnnotation<String> forField(FieldReference field) {
        return new WarningAnnotation<>("FIELD", field.getFullName());
    }
    
    public static WarningAnnotation<String> forVariable(Variable var) {
        return new WarningAnnotation<>("VARIABLE", var.getName());
    }
    
    public static WarningAnnotation<Number> forNumber(Number number) {
        return new WarningAnnotation<>("NUMBER", number);
    }
    
    public static WarningAnnotation<Location> forLocation(int offset, int line) {
        return forLocation(new Location(offset, line));
    }
    
    public static WarningAnnotation<Location> forLocation(Location loc) {
        return new WarningAnnotation<>("LOCATION", loc);
    }
    
    public static WarningAnnotation<Location> forAnotherInstance(Location loc) {
        return new WarningAnnotation<>("ANOTHER_INSTANCE", loc);
    }
    
    public static WarningAnnotation<String> forSourceFile(String file) {
        return new WarningAnnotation<>("FILE", file);
    }
    
    public static WarningAnnotation<String> forString(String str) {
        return new WarningAnnotation<>("STRING", str);
    }
}
