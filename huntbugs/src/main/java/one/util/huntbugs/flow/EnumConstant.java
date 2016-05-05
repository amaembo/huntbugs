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
package one.util.huntbugs.flow;

import java.util.Objects;

import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
public final class EnumConstant {
    private final String typeName;
    private final String name;

    EnumConstant(String typeName, String name) {
        super();
        this.typeName = Objects.requireNonNull(typeName);
        this.name = Objects.requireNonNull(name);
    }

    public String getTypeName() {
        return typeName;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return typeName.hashCode() * 31 + name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        EnumConstant other = (EnumConstant) obj;
        return name.equals(other.name) && typeName.equals(other.typeName);
    }

    @Override
    public String toString() {
        return new WarningAnnotation.TypeInfo(typeName).getSimpleName()+"."+name;
    }
}
