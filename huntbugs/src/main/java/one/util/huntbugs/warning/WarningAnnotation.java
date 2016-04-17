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

import one.util.huntbugs.util.Nodes;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
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
    
    public static class TypeInfo {
        private final String typeName;

        public TypeInfo(String typeName) {
            this.typeName = Objects.requireNonNull(typeName);
        }
        
        public TypeInfo(TypeReference ref) {
            this.typeName = ref.getInternalName();
        }
        
        public String getTypeName() {
            return typeName;
        }

        public String getSimpleName() {
            String type = typeName;
            while(type.startsWith("["))
                type = type.substring(1)+"[]";
            int pos = type.lastIndexOf('/');
            if(pos > -1)
                type = type.substring(pos+1).replace('$', '.');
            return type;
        }
        
        @Override
        public int hashCode() {
            return typeName.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj
                || (obj != null && getClass() == obj.getClass() && typeName.equals(((TypeInfo) obj).typeName));
        }

        @Override
        public String toString() {
            String type = typeName.replaceAll("[/$]", ".");
            while(type.startsWith("["))
                type = type.substring(1)+"[]";
            return type;
        }
    }
    
    public static class MemberInfo {
        private final String typeName;
        private final String name;
        private final String signature;

        public MemberInfo(String typeName, String name, String signature) {
            this.typeName = Objects.requireNonNull(typeName);
            this.name = Objects.requireNonNull(name);
            this.signature = Objects.requireNonNull(signature);
        }

        public MemberInfo(MemberReference mr) {
            this.typeName = mr.getDeclaringType().getInternalName();
            this.name = mr.getName();
            this.signature = mr.getErasedSignature();
        }

        public String getTypeName() {
            return typeName;
        }

        public String getName() {
            return name;
        }

        public String getSignature() {
            return signature;
        }
        
        public boolean isMethod() {
            return signature.startsWith("(");
        }

        @Override
        public String toString() {
            if(isMethod()) {
                if(name.equals("<init>"))
                    return typeName+signature;
                return typeName+"."+name+signature;
            }
            return typeName+"."+name+":"+signature;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, typeName, signature);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            MemberInfo other = (MemberInfo) obj;
            return name.equals(other.name) && signature.equals(other.signature) && typeName.equals(other.typeName);
        }
    }

    public static class Location {
        final int offset;
        final int sourceLine;

        public Location(int offset, int sourceLine) {
            this.offset = offset;
            this.sourceLine = sourceLine;
        }

        public int getOffset() {
            return offset;
        }

        public int getSourceLine() {
            return sourceLine;
        }

        @Override
        public String toString() {
            if (sourceLine != -1)
                return "byteCode: " + offset + "; line: " + sourceLine;
            return "byteCode: " + offset;
        }
    }

    @Override
    public String toString() {
        return getRole() + ": " + getValue();
    }

    public static WarningAnnotation<TypeInfo> forType(TypeReference type) {
        return forType("TYPE", type);
    }

    public static WarningAnnotation<TypeInfo> forType(String role, TypeReference type) {
        return new WarningAnnotation<>(role, new TypeInfo(type));
    }

    public static WarningAnnotation<MemberInfo> forMethod(MethodReference method) {
        return new WarningAnnotation<>("METHOD", new MemberInfo(method));
    }

    public static WarningAnnotation<MemberInfo> forReturnValue(MethodReference method) {
        return new WarningAnnotation<>("RETURN_VALUE_OF", new MemberInfo(method));
    }

    public static WarningAnnotation<MemberInfo> forField(FieldReference field) {
        return new WarningAnnotation<>("FIELD", new MemberInfo(field));
    }

    public static WarningAnnotation<String> forVariable(Variable var) {
        return new WarningAnnotation<>("VARIABLE", var.getName());
    }

    public static WarningAnnotation<Number> forNumber(Number number) {
        return new WarningAnnotation<>("NUMBER", number);
    }
    
    public static WarningAnnotation<MemberInfo> forMember(String role, String internalTypeName, String name, String signature) {
        return new WarningAnnotation<>(role, new MemberInfo(internalTypeName, name, signature));
    }

    public static WarningAnnotation<MemberInfo> forMember(String role, MemberReference mr) {
        return new WarningAnnotation<>(role, new MemberInfo(mr));
    }
    
    public static WarningAnnotation<Location> forLocation(int offset, int line) {
        return forLocation(new Location(offset, line));
    }

    public static WarningAnnotation<Location> forLocation(Location loc) {
        return forLocation("LOCATION", loc);
    }

    public static WarningAnnotation<Location> forLocation(String role, Location loc) {
        return new WarningAnnotation<>(role, loc);
    }
    
    public static WarningAnnotation<Location> forAnotherInstance(Location loc) {
        return forLocation("ANOTHER_INSTANCE", loc);
    }

    public static WarningAnnotation<String> forSourceFile(String file) {
        return new WarningAnnotation<>("FILE", file);
    }

    public static WarningAnnotation<String> forString(String str) {
        return new WarningAnnotation<>("STRING", str);
    }
    
    public static WarningAnnotation<String> forOperation(Expression expr) {
        return new WarningAnnotation<>("OPERATION", Nodes.getOperation(expr.getCode()));
    }
    
    public static WarningAnnotation<String> forOperation(AstCode code) {
        return new WarningAnnotation<>("OPERATION", Nodes.getOperation(code));
    }
}
