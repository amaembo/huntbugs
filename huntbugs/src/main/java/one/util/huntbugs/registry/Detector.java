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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import one.util.huntbugs.registry.anno.AstNodeVisitor;
import one.util.huntbugs.warning.WarningType;

/**
 * @author lan
 *
 */
public class Detector {
    private final Map<String, WarningType> wts;
    private final Class<?> clazz;
    final List<MethodHandle> astVisitors = new ArrayList<>();

    public Detector(Map<String, WarningType> wts, Class<?> clazz) throws IllegalAccessException {
        this.wts = Objects.requireNonNull(wts);
        this.clazz = Objects.requireNonNull(clazz);
        for(Method m : clazz.getMethods()) {
            if(m.getAnnotation(AstNodeVisitor.class) != null) {
                MethodHandle mh = MethodHandles.publicLookup().unreflect(m);
                //Class<?>[] types = m.getParameterTypes();
                //TODO: support various signatures
                //Currently supported: Node, MethodContext
                astVisitors.add(mh);
            }
        }
        // TODO Auto-generated constructor stub
    }
    
    
    
    public WarningType getWarningType(String typeName) {
        return wts.get(typeName);
    }
    
    @Override
    public String toString() {
        return clazz.getName();
    }
}
