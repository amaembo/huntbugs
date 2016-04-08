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
package one.util.huntbugs.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.strobel.assembler.metadata.TypeReference;

/**
 * @author lan
 *
 */
public abstract class AbstractTypeDatabase<E> {
    private final Map<String, E> map = new HashMap<>();
    private final Function<String, E> fn;
    
    protected AbstractTypeDatabase(Function<String, E> elementSupplier) {
        this.fn = Objects.requireNonNull(elementSupplier);
    }
    
    protected E getOrCreate(TypeReference ref) {
        return map.computeIfAbsent(ref.getInternalName(), fn);
    }
    
    protected E getOrCreate(String internalName) {
        return map.computeIfAbsent(internalName, fn);
    }
    
    public E get(TypeReference ref) {
        return map.get(ref.getInternalName());
    }
    
    public E get(String internalName) {
        return map.get(internalName);
    }
    
    @Override
    public String toString() {
        return "Database <"+getClass().getName()+">";
    }
}
