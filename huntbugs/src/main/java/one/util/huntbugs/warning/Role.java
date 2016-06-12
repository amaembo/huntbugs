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
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation.Location;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;
import one.util.huntbugs.warning.WarningAnnotation.TypeInfo;

/**
 * @author Tagir Valeev
 * 
 *         Annotation roles
 */
public class Role<T> {
    public enum Count {
        ANY, ZERO_ONE, ONE;
    }

    private final String name;
    private final Class<T> type;
    private final Count count;

    Role(String name, Class<T> type) {
        this(name, type, Count.ANY);
    }

    Role(String name, Class<T> type, Count count) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.count = Objects.requireNonNull(count);
    }

    /**
     * @return how many times such annotation could be used via single warning
     */
    public Count getCount() {
        return count;
    }

    public Class<T> getType() {
        return type;
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + type.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Role))
            return false;
        Role<?> other = (Role<?>) obj;
        return Objects.equals(name, other.name) && Objects.equals(type, other.type);
    }

    @Override
    public String toString() {
        return name;
    }

    public WarningAnnotation<T> create(T value) {
        return new WarningAnnotation<>(this, value);
    }

    public static class MemberRole extends Role<MemberInfo> {
        MemberRole(String name) {
            super(name, MemberInfo.class);
        }

        MemberRole(String name, Count count) {
            super(name, MemberInfo.class, count);
        }

        public WarningAnnotation<MemberInfo> create(MemberReference mr) {
            return create(new MemberInfo(mr));
        }

        public WarningAnnotation<MemberInfo> create(String internalTypeName, String name, String signature) {
            return create(new MemberInfo(internalTypeName, name, signature));
        }
        
        public static MemberRole forName(String name) {
            return new MemberRole(name);
        }
    }
    
    public static class NumberRole extends Role<Number> {
        NumberRole(String name) {
            super(name, Number.class);
        }

        NumberRole(String name, Count count) {
            super(name, Number.class, count);
        }
        
        public static NumberRole forName(String name) {
            return new NumberRole(name);
        }
    }
    
    public static class StringRole extends Role<String> {
        StringRole(String name) {
            super(name, String.class);
        }
        
        StringRole(String name, Count count) {
            super(name, String.class, count);
        }
        
        public WarningAnnotation<String> createFromConst(Object constant) {
            return create(Formatter.formatConstant(constant));
        }
        
        public static StringRole forName(String name) {
            return new StringRole(name);
        }
    }
    
    public static class TypeRole extends Role<TypeInfo> {
        TypeRole(String name) {
            super(name, TypeInfo.class);
        }

        TypeRole(String name, Count count) {
            super(name, TypeInfo.class, count);
        }
        
        public WarningAnnotation<TypeInfo> create(TypeReference tr) {
            return create(new TypeInfo(tr));
        }
        
        public WarningAnnotation<TypeInfo> create(String internalTypeName) {
            return create(new TypeInfo(internalTypeName));
        }
        
        public static TypeRole forName(String name) {
            return new TypeRole(name);
        }
    }
    
    public static class LocationRole extends Role<Location> {
        LocationRole(String name) {
            super(name, Location.class);
        }
        
        LocationRole(String name, Count count) {
            super(name, Location.class, count);
        }
        
        public WarningAnnotation<Location> create(MethodContext mc, Node node) {
            return create(mc.getLocation(node));
        }
        
        public static LocationRole forName(String name) {
            return new LocationRole(name);
        }
    }
    
    public static class OperationRole extends StringRole {
        public OperationRole(String name, Count count) {
            super(name, count);
        }

        public OperationRole(String name) {
            super(name);
        }
        
        public WarningAnnotation<String> create(AstCode code) {
            return create(Nodes.getOperation(code));
        }
        
        public WarningAnnotation<String> create(Expression expr) {
            if(expr.getCode() == AstCode.InvokeVirtual && Methods.isEqualsMethod((MethodReference) expr.getOperand()))
                return create("equals");
            return create(Nodes.getOperation(expr.getCode()));
        }
    }
}
