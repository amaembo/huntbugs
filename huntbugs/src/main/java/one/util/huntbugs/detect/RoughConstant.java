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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodeVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

@WarningDefinition(category="BadPractice", name="RoughConstantValue", baseRank=30)
public class RoughConstant {
    static class BadConstant {
        double base;
        double factor;
        String replacement;
        double value;
        int basePriority;

        Set<Number> approxSet = new HashSet<>();

        BadConstant(double base, double factor, String replacement, int basePriority) {
            this.base = base;
            this.factor = factor;
            this.value = this.base * this.factor;
            this.replacement = replacement;
            this.basePriority = basePriority;
            BigDecimal valueBig = BigDecimal.valueOf(value);
            BigDecimal baseBig = BigDecimal.valueOf(base);
            BigDecimal factorBig = BigDecimal.valueOf(factor);
            for (int prec = 0; prec < 14; prec++) {
                addApprox(baseBig.round(new MathContext(prec, RoundingMode.FLOOR)).multiply(factorBig));
                addApprox(baseBig.round(new MathContext(prec, RoundingMode.CEILING)).multiply(factorBig));
                addApprox(valueBig.round(new MathContext(prec, RoundingMode.FLOOR)));
                addApprox(valueBig.round(new MathContext(prec, RoundingMode.CEILING)));
            }
        }

        public boolean exact(Number candidate) {
            if (candidate instanceof Double) {
                return candidate.doubleValue() == value;
            }
            return candidate.floatValue() == (float) value;
        }

        public double diff(double candidate) {
            return Math.abs(value - candidate) / value;
        }

        public boolean equalPrefix(Number candidate) {
            return approxSet.contains(candidate);
        }

        private void addApprox(BigDecimal roundFloor) {
            double approxDouble = roundFloor.doubleValue();
            if (approxDouble != value && Math.abs(approxDouble - value) / value < 0.001) {
                approxSet.add(approxDouble);
            }
            float approxFloat = roundFloor.floatValue();
            if (Math.abs(approxFloat - value) / value < 0.001) {
                approxSet.add(approxFloat);
                approxSet.add((double) approxFloat);
            }
        }
    }

    private static final BadConstant[] badConstants = new BadConstant[] {
        new BadConstant(Math.PI, 1, "Math.PI", 20),
        new BadConstant(Math.PI, 1/2.0, "Math.PI/2", 10),
        new BadConstant(Math.PI, 1/3.0, "Math.PI/3", 2),
        new BadConstant(Math.PI, 1/4.0, "Math.PI/4", 4),
        new BadConstant(Math.PI, 2, "2*Math.PI", 10),
        new BadConstant(Math.E, 1, "Math.E", 3)
    };

    @AstNodeVisitor
    public void visit(Node node, MethodContext ctx, NodeChain parents) {
        // Not use Nodes.getConstant here as direct usage should only be reported
        if(!Nodes.isOp(node, AstCode.LdC))
            return;
        Object constant = ((Expression) node).getOperand();
        if(constant instanceof Float || constant instanceof Double) {
            Number constValue = (Number)constant;
            double candidate = constValue.doubleValue();
            if (Double.isNaN(candidate) || Double.isInfinite(candidate)) {
                return;
            }
            for (BadConstant badConstant : badConstants) {
                int priority = getPriority(badConstant, constValue, candidate);
                if(priority > -100) {
                    Node parent = parents.getNode();
                    if(Nodes.isBoxing(parent))
                        parent = parents.getParent().getNode();
                    if(Nodes.isOp(parent, AstCode.InitArray)) {
                        int children = parent.getChildren().size();
                        if(children > 100)
                            priority -= 30;
                        else if(children > 10)
                            priority -= 20;
                        else if(children > 5)
                            priority -= 10;
                        else if(children > 1)
                            priority -= 5;
                    }
                    ctx.report("RoughConstantValue", priority, node, WarningAnnotation.forNumber(constValue));
                }
            }
        }
    }

    private int getPriority(BadConstant badConstant, Number constValue, double candidate) {
        if (badConstant.exact(constValue)) {
            return -100;
        }
        double diff = badConstant.diff(candidate);
        if (diff > 1e-3) {
            return -100;
        }
        if (badConstant.equalPrefix(constValue)) {
            return diff > 3e-4 ? badConstant.basePriority-15 :
                diff > 1e-4 ? badConstant.basePriority-10 :
                diff > 1e-5 ? badConstant.basePriority-5 :
                diff > 1e-6 ? badConstant.basePriority :
                    badConstant.basePriority + 10;
        }
        if (diff > 1e-7) {
            return -100;
        }
        return badConstant.basePriority-10;
    }
}
