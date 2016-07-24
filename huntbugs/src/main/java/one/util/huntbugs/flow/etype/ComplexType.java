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
package one.util.huntbugs.flow.etype;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author shustkost
 *
 */
abstract class ComplexType implements EType {
    Set<SingleType> types;

    public ComplexType(Set<SingleType> types) {
        this.types = types;
    }

    abstract EType reduce();

    abstract EType append(SingleType st);
    
    abstract EType appendAny(EType type);
    
    @Override
    public int hashCode() {
        return types.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        return types.equals(((ComplexType) obj).types);
    }

    String toString(String delimiter) {
        return types.stream().map(EType::toString).map(typeStr -> typeStr.contains(" ") ? "(" + typeStr + ")" : typeStr)
                .sorted().collect(Collectors.joining(delimiter));
    }
}
