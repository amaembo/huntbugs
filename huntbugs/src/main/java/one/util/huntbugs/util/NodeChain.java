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
package one.util.huntbugs.util;

import java.util.Objects;

import com.strobel.decompiler.ast.Node;

/**
 * @author lan
 *
 */
public class NodeChain {
    private final NodeChain parent;
    private final Node cur;

    public NodeChain(NodeChain parent, Node cur) {
        this.parent = parent;
        this.cur = Objects.requireNonNull(cur);
    }

    public NodeChain getParent() {
        return parent;
    }

    public Node getNode() {
        return cur;
    }
    
    @Override
    public String toString() {
        if(parent == null)
            return cur.toString();
        return cur + " -> "+parent;
    }
}
