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
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Warning;

@WarningDefinition(category="BadPractice", name="RoughConstantValue", maxScore=60)
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
        new BadConstant(Math.PI, 1, "Math.PI", 0),
        new BadConstant(Math.PI, 1/2.0, "Math.PI/2", 10),
        new BadConstant(Math.PI, 1/3.0, "Math.PI/3", 18),
        new BadConstant(Math.PI, 1/4.0, "Math.PI/4", 16),
        new BadConstant(Math.PI, 2, "2*Math.PI", 10),
        new BadConstant(Math.E, 1, "Math.E", 17)
    };

    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext ctx, NodeChain parents) {
        // Not use Nodes.getConstant here as direct usage should only be reported
        if(expr.getCode() != AstCode.LdC)
            return;
        Object constant = expr.getOperand();
        if(constant instanceof Float || constant instanceof Double) {
            Number constValue = (Number)constant;
            double candidate = constValue.doubleValue();
            if (Double.isNaN(candidate) || Double.isInfinite(candidate)) {
                return;
            }
            for (BadConstant badConstant : badConstants) {
                int priority = getPriority(badConstant, constValue, candidate);
                if(priority < Warning.MAX_SCORE) {
                    Node parent = parents.getNode();
                    if(Nodes.isBoxing(parent))
                        parent = parents.getParent().getNode();
                    if(Nodes.isOp(parent, AstCode.InitArray)) {
                        int children = ((Expression)parent).getArguments().size();
                        if(children > 100)
                            priority += 30;
                        else if(children > 10)
                            priority += 20;
                        else if(children > 5)
                            priority += 10;
                        else if(children > 1)
                            priority += 5;
                    }
                    ctx.report("RoughConstantValue", priority, expr, Roles.NUMBER.create(constValue),
                        Roles.REPLACEMENT_STRING.create(badConstant.replacement));
                }
            }
        }
    }

    private int getPriority(BadConstant badConstant, Number constValue, double candidate) {
        if (badConstant.exact(constValue)) {
            return Warning.MAX_SCORE;
        }
        double diff = badConstant.diff(candidate);
        if (diff > 1e-3) {
            return Warning.MAX_SCORE;
        }
        if (badConstant.equalPrefix(constValue)) {
            return diff > 3e-4 ? badConstant.basePriority+25 :
                diff > 1e-4 ? badConstant.basePriority+20 :
                diff > 1e-5 ? badConstant.basePriority+15 :
                diff > 1e-6 ? badConstant.basePriority+10 :
                    badConstant.basePriority;
        }
        if (diff > 1e-7) {
            return Warning.MAX_SCORE;
        }
        return badConstant.basePriority+20;
    }
}
