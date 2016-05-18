/*
 * Copyright 2016 HuntBugs contributors
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

import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.Variable;

/**
 * @author Tagir Valeev
 *
 */
public class WarningAnnotation<T> {
    private final Role<T> role;
    private final T value;

    WarningAnnotation(Role<T> role, T value) {
        super();
        this.role = Objects.requireNonNull(role);
        this.value = Objects.requireNonNull(value);
    }

    public Role<T> getRole() {
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
        
        public String getJavaName() {
            return typeName.replace('/', '.');
        }

        public String getSimpleName() {
            String type = typeName;
            String suffix = "";
            while (type.startsWith("[")) {
                type = type.substring(1);
                suffix += "[]";
            }
            switch (type) {
            case "B":
                type = "byte";
                break;
            case "C":
                type = "char";
                break;
            case "J":
                type = "long";
                break;
            case "I":
                type = "int";
                break;
            case "S":
                type = "short";
                break;
            case "Z":
                type = "boolean";
                break;
            case "F":
                type = "float";
                break;
            case "D":
                type = "double";
                break;
            }
            int pos = type.lastIndexOf('/');
            if (pos > -1)
                type = type.substring(pos + 1).replace('$', '.');
            return type + suffix;
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
            while (type.startsWith("["))
                type = type.substring(1) + "[]";
            return type;
        }
    }

    public static class MemberInfo {
        private final TypeInfo type;
        private final String name;
        private final String signature;

        public MemberInfo(String typeName, String name, String signature) {
            this.type = new TypeInfo(Objects.requireNonNull(typeName));
            this.name = Objects.requireNonNull(name);
            this.signature = Objects.requireNonNull(signature);
        }

        public MemberInfo(MemberReference mr) {
            this.type = new TypeInfo(mr.getDeclaringType().getInternalName());
            this.name = mr.getName();
            this.signature = mr.getErasedSignature();
        }

        public String getTypeName() {
            return type.getTypeName();
        }
        
        public TypeInfo getType() {
            return type;
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
            if (isMethod()) {
                if (name.equals("<init>"))
                    return "new " + type.getTypeName() + signature;
                return type.getTypeName() + "." + name + signature;
            }
            return type.getTypeName() + "." + name + ":" + signature;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, signature);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            MemberInfo other = (MemberInfo) obj;
            return name.equals(other.name) && signature.equals(other.signature) && type.equals(other.type);
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

        @Override
        public int hashCode() {
            return offset * 31 + sourceLine;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            Location other = (Location) obj;
            return offset == other.offset && sourceLine == other.sourceLine;
        }
    }

    @Override
    public String toString() {
        return getRole() + ": " + getValue();
    }

    public static WarningAnnotation<String> forVariable(Variable var) {
        return Roles.VARIABLE.create(var.getName());
    }
}
