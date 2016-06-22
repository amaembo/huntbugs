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

import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author lan
 *
 */
public class PurityAnnotator extends Annotator<PurityAnnotator.Purity> {
    PurityAnnotator() {
        super("purity", Purity.HEAP_MOD);
    }

    public static enum Purity {
        /**
         * Does not modify program state and always produces the same (or practically indistinguishable) result.
         */
        CONST,
        /**
         * Does not modify program state and produces the same (or practically indistinguishable) result
         * if local variables directly involved into expression don't change.
         */
        LOCAL_DEP,
        /**
         * Does not modify program state and produces the same (or practically indistinguishable) result
         * if heap does not change.
         */
        HEAP_DEP, 
        /**
         * Does not modify program state.
         */
        SIDE_EFFECT_FREE,
        /**
         * May modify heap.
         */
        HEAP_MOD, 
        /**
         * May modify heap and/or local variables.
         */
        LOCAL_MOD;
        
        Purity merge(Purity other) {
            return this.ordinal() > other.ordinal() ? this : other;
        }
        
        public boolean atLeast(Purity other) {
            return this.ordinal() <= other.ordinal();
        }
    }

    void annotatePurity(Node node, FrameContext fc) {
        for(Node child : Nodes.getChildren(node)) {
            if(child instanceof Expression) {
                annotatePurity((Expression)child, fc);
            } else {
                annotatePurity(child, fc);
            }
        }
    }
    
    private Purity getOwnPurity(Expression expr, FrameContext fc) {
        switch (expr.getCode()) {
            case PreIncrement:
            case PostIncrement:
            case Store:
            case CompoundAssignment:
                return Purity.LOCAL_MOD;
            case InvokeDynamic:
            case StoreElement:
            case PutField:
            case PutStatic:
                return Purity.HEAP_MOD;
            case InitObject: {
                MethodReference mr = (MethodReference) expr.getOperand();
                if (!Methods.isSideEffectFree(mr))
                    return Purity.HEAP_MOD;
                if (Types.isImmutable(mr.getDeclaringType()))
                    return Purity.CONST;
                return Purity.SIDE_EFFECT_FREE;
            }
            case NewArray:
            case InitArray:
                return Purity.SIDE_EFFECT_FREE;
            case GetField:
                if(fc.cf.isKnownEffectivelyFinal(new MemberInfo((MemberReference) expr.getOperand()))
                        && !fc.md.isConstructor()) {
                    return Purity.CONST;
                }
                return Purity.HEAP_DEP;
            case GetStatic:
                if(fc.cf.isKnownEffectivelyFinal(new MemberInfo((MemberReference) expr.getOperand()))
                        && !fc.md.isTypeInitializer()) {
                    return Purity.CONST;
                }
                return Purity.HEAP_DEP;
            case LoadElement:
                return Purity.HEAP_DEP;
            case InvokeSpecial:
            case InvokeStatic:
            case InvokeVirtual:
            case InvokeInterface: {
                MethodReference mr = (MethodReference) expr.getOperand();
                if (Methods.isPure(mr)) {
                    boolean indistinguishable = mr.getReturnType().isVoid() || mr.getReturnType().isPrimitive() || Types.isImmutable(mr.getReturnType());
                    if(indistinguishable) {
                        return Purity.CONST;
                    }
                    return Purity.SIDE_EFFECT_FREE;
                }
                if (Methods.isSideEffectFree(mr)) {
                    return Purity.SIDE_EFFECT_FREE;
                }
                return Purity.HEAP_MOD;
            }
            default:
                return Purity.CONST;
        }
    }

    private Purity annotatePurity(Expression expr, FrameContext fc) {
        Purity purity = Purity.CONST;
        for(Expression child : expr.getArguments()) {
            purity = purity.merge(annotatePurity(child, fc));
        }
        if(ValuesFlow.getValue(expr) != null) {
            // statically known constant
            purity = Purity.CONST;
        } else {
            purity = purity.merge(getOwnPurity(expr, fc));
        }
        putIfAbsent(expr, purity);
        return purity;
    }
    
    @Override
    public Purity get(Expression expr) {
        return super.get(expr);
    }
    
    public boolean isPure(Expression expr) {
        return get(expr).atLeast(Purity.LOCAL_DEP);
    }
    
    public boolean isSideEffectFree(Expression expr) {
        return get(expr).atLeast(Purity.SIDE_EFFECT_FREE);
    }
}
