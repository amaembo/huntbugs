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
public abstract class WarningAnnotation<T> {
    abstract public String getRole();

    abstract public T getValue();
    
    @Override
    public String toString() {
        return getRole()+": "+getValue();
    }

    public static class TypeWarningAnnotation extends WarningAnnotation<String> {
        private final String type;

        public TypeWarningAnnotation(TypeReference type) {
            this.type = type.getFullName();
        }

        @Override
        public String getRole() {
            return "TYPE";
        }

        @Override
        public String getValue() {
            return type;
        }
    }

    public static class MethodWarningAnnotation extends WarningAnnotation<String> {
        private final String method;
        
        public MethodWarningAnnotation(MethodReference method) {
            this.method = method.getFullName();
        }
        
        @Override
        public String getRole() {
            return "METHOD";
        }
        
        @Override
        public String getValue() {
            return method;
        }
    }
    
    public static class NumberWarningAnnotation extends WarningAnnotation<Number> {
        private final Number number;
        
        public NumberWarningAnnotation(Number number) {
            this.number = number;
        }
        
        @Override
        public String getRole() {
            return "NUMBER";
        }

        @Override
        public Number getValue() {
            return number;
        }
    }
}
