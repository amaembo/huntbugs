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

import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.CFG.EdgeType;

/**
 * @author lan
 *
 */
public interface GraphSearch<T> {
    T markStart(Expression expr, boolean isEntry);
    T transfer(T orig, Expression from, EdgeType edge, Expression to);
    T merge(T f1, T f2);
}
