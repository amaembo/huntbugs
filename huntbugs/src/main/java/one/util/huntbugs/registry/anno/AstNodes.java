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
package one.util.huntbugs.registry.anno;

import one.util.huntbugs.util.NodeChain;

import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

/**
 * Type of the nodes visited by {@link AstVisitor}
 */
public enum AstNodes {
    /**
     * Visits all method nodes. Additional allowed argument types: {@link Node}, {@link NodeChain}.
     * May return void or boolean: if false then the rest of the method will be skipped.
     */
    ALL,
    /**
     * Visits only expressions. Additional allowed argument types: {@link Expression}, {@link NodeChain}.
     * May return void or boolean: if false then the rest of the method will be skipped.
     */
    EXPRESSIONS,
    /**
     * Visits only method root node. Additional allowed argument type: {@link Block}.
     * Returns void.
     */
    ROOT
}
