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
package one.util.huntbugs.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.CatchBlock;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.TryCatchBlock;

/**
 * @author Tagir Valeev
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
    
    public Block getRoot() {
        NodeChain nc = this;
        while(nc.getParent() != null) {
            nc = nc.getParent();
        }
        return (Block) nc.getNode();
    }
    
    public boolean isSynchronized() {
        NodeChain chain = this;
        while(chain != null) {
            if(Nodes.isSynchorizedBlock(chain.getNode()))
                return true;
            chain = chain.getParent();
        }
        return false;
    }
    
    public boolean isInTry(String... wantedExceptions) {
        NodeChain nc = this;
        while(nc != null) {
            if(nc.getNode() instanceof Block && nc.getParent() != null && nc.getParent().getNode() instanceof TryCatchBlock) {
                TryCatchBlock tcb = (TryCatchBlock) nc.getParent().getNode();
                for(CatchBlock catchBlock : tcb.getCatchBlocks()) {
                    TypeReference exType = catchBlock.getExceptionType();
                    if(exType != null && Arrays.stream(wantedExceptions).anyMatch(exType.getInternalName()::equals))
                        return true;
                    if(catchBlock.getCaughtTypes().stream().anyMatch(t -> 
                        Arrays.stream(wantedExceptions).anyMatch(t.getInternalName()::equals)))
                        return true;
                }
            }
            nc = nc.getParent();
        }
        return false;
    }
    
    public boolean isInCatch(String wantedException) {
        NodeChain nc = this;
        while(nc != null) {
            if(nc.getNode() instanceof CatchBlock) {
                CatchBlock catchBlock = (CatchBlock)nc.getNode();
                TypeReference exType = catchBlock.getExceptionType();
                if(exType != null && Types.isInstance(exType, wantedException))
                    return true;
                if(catchBlock.getCaughtTypes().stream().anyMatch(t -> Types.isInstance(t, wantedException)))
                    return true;
            }
            nc = nc.getParent();
        }
        return false;
    }
    
    public MethodDefinition getLambdaMethod() {
        NodeChain nc = this;
        while(nc != null) {
            if(nc.getNode() instanceof Lambda) {
                return Nodes.getLambdaMethod((Lambda) nc.getNode());
            }
            nc = nc.getParent();
        }
        return null;
    }

    public boolean isOnlyChild(Node node) {
        Iterator<Node> iterator = Nodes.getChildren(getNode()).iterator();
        return iterator.hasNext() && iterator.next() == node && !iterator.hasNext();
    }
}
