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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;

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
    public String toString() {
        return getRole()+": "+getValue();
    }
    
    public static WarningAnnotation<String> forType(TypeReference type) {
        return new WarningAnnotation<String>("TYPE", type.getFullName());
    }

    public static WarningAnnotation<String> forMethod(MethodReference method) {
        return new WarningAnnotation<String>("METHOD", method.getFullName());
    }
    
    public static WarningAnnotation<Number> forNumber(Number number) {
        return new WarningAnnotation<Number>("NUMBER", number);
    }
    
    public static WarningAnnotation<Integer> forByteCodeOffset(int offset) {
        return new WarningAnnotation<Integer>("BYTECODE", offset);
    }
    
    public static WarningAnnotation<Integer> forSourceLine(int line) {
        return new WarningAnnotation<Integer>("LINE", line);
    }
    
    public static WarningAnnotation<String> forSourceFile(String file) {
        return new WarningAnnotation<String>("FILE", file);
    }
    
    public static WarningAnnotation<String> forString(String str) {
        return new WarningAnnotation<String>("STRING", str);
    }
}
