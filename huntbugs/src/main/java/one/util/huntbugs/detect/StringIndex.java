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
package one.util.huntbugs.detect;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "RedundantCode", name = "UselessStringSubstring", maxScore = 50)
@WarningDefinition(category = "RedundantCode", name = "StringIndexIsLessThanZero", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "StringIndexIsGreaterThanAllowed", maxScore = 60)
public class StringIndex {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression node, NodeChain nc, MethodContext ctx, MethodDefinition curMethod) {
        if (Nodes.isInvoke(node) && node.getCode() != AstCode.InvokeDynamic) {
            check(node, (MethodReference) node.getOperand(), nc, ctx, curMethod);
        }
    }

    private void check(Expression node, MethodReference mr, NodeChain nc, MethodContext mc, MethodDefinition curMethod) {
        String typeName = mr.getDeclaringType().getInternalName();
        String name = mr.getName();
        String signature = mr.getSignature();
        boolean isString = typeName.equals("java/lang/String");
        if (isString) {
            Object val = Nodes.getConstant(node.getArguments().get(0));
            String str = val instanceof String ? (String)val : null;
            int strLen = str == null ? Integer.MAX_VALUE : str.length();
            if (name.equals("substring") || name.equals("subSequence")) {
                Object idxObj = Nodes.getConstant(node.getArguments().get(1));
                boolean twoArg = signature.startsWith("(II)");
                int len = strLen;
                if (twoArg) {
                    Object lenObj = Nodes.getConstant(node.getArguments().get(2));
                    if (lenObj instanceof Integer) {
                        len = (int) lenObj;
                        checkRange(node, strLen, len, mc);
                    }
                }
                if (idxObj instanceof Integer) {
                    int idx = (int) idxObj;
                    if (idx == 0 && !twoArg) {
                        mc.report("UselessStringSubstring", 0, node);
                    } else {
                        checkRange(node, len, idx, mc);
                    }
                }
            } else if((name.equals("charAt") || name.equals("codePointAt")) && signature.startsWith("(I)")) {
                Object idx = Nodes.getConstant(node.getArguments().get(1));
                if (idx instanceof Integer) {
                    int i = (int) idx;
                    checkRange(node, strLen - 1, i, mc);
                }
            }
        }
    }

    private void checkRange(Expression expr, int maxValue, int val, MethodContext mc) {
        if(val < 0) {
            mc.report("StringIndexIsLessThanZero", 0, expr, new WarningAnnotation<>("INDEX", val));
        } else if(val > maxValue) {
            mc.report("StringIndexIsGreaterThanAllowed", 0, expr, new WarningAnnotation<>("INDEX", val), new WarningAnnotation<>("MAX_VAL", maxValue));
        }
    }
}
