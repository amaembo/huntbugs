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
package one.util.huntbugs.registry;

import java.util.Collections;
import java.util.List;

import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.Location;

import com.strobel.assembler.ir.attributes.LineNumberTableAttribute;
import com.strobel.assembler.ir.attributes.SourceAttribute;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Loop;
import com.strobel.decompiler.ast.LoopType;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Switch;
import com.strobel.decompiler.languages.java.LineNumberTableConverter;
import com.strobel.decompiler.languages.java.OffsetToLineNumberConverter;

/**
 * @author Tagir Valeev
 *
 */
final class MethodData {
    final MethodDefinition mainMethod;
    List<WarningAnnotation<?>> annot;
    // May differ from mainMethod when inside lambda
    MethodDefinition realMethod;
    NodeChain parents;

    private OffsetToLineNumberConverter ltc;
    List<Expression> origParams;

    MethodData(MethodDefinition md) {
        this.mainMethod = this.realMethod = md;
    }

    int getLineNumber(int offset) {
        int line = getConverter().getLineForOffset(offset);
        return line == OffsetToLineNumberConverter.UNKNOWN_LINE_NUMBER ? -1 : line;
    }

    List<WarningAnnotation<?>> getMethodSpecificAnnotations() {
        if (annot == null) {
            annot = Collections.singletonList(Roles.METHOD.create(mainMethod));
        }
        return annot;
    }

    private OffsetToLineNumberConverter getConverter() {
        if (realMethod != mainMethod)
            return createConverter(realMethod);
        if (ltc == null)
            ltc = createConverter(mainMethod);
        return ltc;
    }

    private static OffsetToLineNumberConverter createConverter(MethodDefinition md) {
        for (SourceAttribute sa : md.getSourceAttributes()) {
            if (sa instanceof LineNumberTableAttribute) {
                return new LineNumberTableConverter((LineNumberTableAttribute) sa);
            }
        }
        return OffsetToLineNumberConverter.NOOP_CONVERTER;
    }
    
    private static int getOffset(Node node) {
        if (node instanceof Expression) {
            Expression expr = (Expression) node;
            return expr.getOffset();
        } else if (node instanceof Condition) {
            return ((Condition) node).getCondition().getOffset();
        } else if (node instanceof Block) {
            List<Node> body = ((Block) node).getBody();
            return body.stream().mapToInt(MethodData::getOffset).filter(off -> off != Expression.MYSTERY_OFFSET).findFirst().orElse(
                Expression.MYSTERY_OFFSET);
        } else if (node instanceof Loop) {
            Loop loop = (Loop)node;
            return loop.getLoopType() == LoopType.PreCondition && loop.getCondition() != null ? loop.getCondition()
                    .getOffset() : getOffset(loop.getBody());
        } else if (node instanceof Switch) {
            return ((Switch)node).getCondition().getOffset();
        }
        return Expression.MYSTERY_OFFSET;
    }

    Location getLocation(Node node) {
        NodeChain nc = parents;
        while(true) {
            int offset = getOffset(node);
            if(offset != Expression.MYSTERY_OFFSET)
                return new Location(offset, getLineNumber(offset));
            if(nc == null || nc.getNode() instanceof Lambda)
                return new Location(0, getLineNumber(0));
            // TODO: better support of empty blocks
            node = nc.getNode();
            nc = nc.getParent();
        }
    }

    @Override
    public String toString() {
        return mainMethod.getDeclaringType() + "." + mainMethod;
    }
}
