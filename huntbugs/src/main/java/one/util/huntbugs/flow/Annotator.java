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
package one.util.huntbugs.flow;

import com.strobel.componentmodel.Key;
import com.strobel.decompiler.ast.Expression;

/**
 * @author shustkost
 *
 */
abstract class Annotator<T> {
    private final T defValue;
    private final int idx;

    protected Annotator(String name, T defValue) {
        this.defValue = defValue;
        this.idx = Annotators.register(name);
    }

    protected T get(Expression expr) {
        @SuppressWarnings("unchecked")
        T data = (T) Annotators.get(expr, idx);
        return data == null ? defValue : data;
    }
    
    protected void putIfAbsent(Expression expr, T data) {
        Annotators.replace(expr, idx, null, data);
    }
    
    protected void put(Expression expr, T data) {
        Annotators.put(expr, idx, data);
    }
    
    protected void remove(Expression expr) {
        Annotators.remove(expr, idx);
    }
}
