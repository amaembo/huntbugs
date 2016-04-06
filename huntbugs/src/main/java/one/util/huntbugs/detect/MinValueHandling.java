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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodeVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="AbsoluteValueOfHashCode", baseRank=90)
@WarningDefinition(category="Correctness", name="AbsoluteValueOfRandomInt", baseRank=80)
public class MinValueHandling {
    @AstNodeVisitor
    public void visit(Node node, NodeChain chain, MethodContext mc) {
        if(Nodes.isOp(node, AstCode.Rem)) {
            Node body = Nodes.getOperand(node, 0);
            Object modulus = Nodes.getConstant(Nodes.getOperand(node, 1));
            int priority = 0;
            if((modulus instanceof Integer || modulus instanceof Long) && Long.bitCount(((Number)modulus).longValue()) == 1) {
                priority -= 40;
            }
            checkAbs(mc, body, null, priority, true);
        } else if(chain != null) {
            checkAbs(mc, node, chain.getNode(), -10, false);
        }
    }

	private void checkAbs(MethodContext mc, Node body, Node parent, int priority, boolean forget) {
		if(Nodes.isOp(body, AstCode.InvokeStatic)) {
		    MethodReference absCandidate = (MethodReference)((Expression)body).getOperand();
		    if(absCandidate.getName().equals("abs") && absCandidate.getDeclaringType().getInternalName().equals("java/lang/Math")) {
		        Node source = Nodes.getOperand(body, 0);
		        if(Nodes.isOp(source, AstCode.InvokeVirtual)) {
		            MethodReference sourceCall = (MethodReference)((Expression)source).getOperand();
		            String methodName = sourceCall.getName();
					String methodSig = sourceCall.getSignature();
					if((methodName.equals("nextInt") || methodName.equals("nextLong"))
		                    && (methodSig.equals("()I") || methodSig.equals("()J"))
		                    && Types.isRandomClass(sourceCall.getDeclaringType())) {
					    if(methodSig.equals("()J"))
					        priority -= 5;
					    if(Nodes.isOp(parent, AstCode.Neg)) {
					        return;
					    }
					    if(forget)
					        mc.forgetLastBug();
					    mc.report("AbsoluteValueOfRandomInt", priority, source);
		            } else if(methodName.equals("hashCode") && methodSig.equals("()I")) {
		                if(Nodes.isOp(parent, AstCode.TernaryOp)) {
		                    Node comparison = Nodes.getOperand(parent, 0);
		                    Integer minValue = Integer.MIN_VALUE;
							if(Nodes.isComparison(comparison) && (minValue.equals(Nodes.getConstant(Nodes.getOperand(comparison, 0))) ||
		                            minValue.equals(Nodes.getConstant(Nodes.getOperand(comparison, 1)))))
		                        return;
		                }
		                if(Nodes.isOp(parent, AstCode.Neg)) {
		                    return;
		                }
                        if(forget)
                            mc.forgetLastBug();
		                mc.report("AbsoluteValueOfHashCode", priority, source);
		            }
		        }
		    }
		}
	}
}
