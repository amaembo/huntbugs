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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MetadataHelper;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.flow.etype.EType;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

/**
 * @author shustkost
 *
 */
public class ETypeAnnotator extends Annotator<EType> {

    ETypeAnnotator() {
        super("etype", null);
    }

    boolean build(CFG cfg) {
        return cfg.<ContextTypes, EType> runDFA(this, (md, closure) -> new ETypeDataflow(closure == null
                ? ContextTypes.DEFAULT : closure), 7);
    }

    public EType resolve(Expression expr) {
        EType eType = super.get(expr);
        return eType == null ? EType.UNKNOWN : eType;
    }

    static class ContextTypes {
        static final ContextTypes DEFAULT = new ContextTypes(null);

        final Map<Variable, EType> values;

        private ContextTypes(Map<Variable, EType> values) {
            this.values = values;
        }

        ContextTypes merge(ContextTypes other) {
            if (this == other)
                return this;
            if (this == DEFAULT || other == DEFAULT)
                return DEFAULT;
            Map<Variable, EType> newTypes = new HashMap<>(values);
            newTypes.keySet().retainAll(other.values.keySet());
            if (newTypes.isEmpty())
                return DEFAULT;
            other.values.forEach((k, v) -> newTypes.compute(k, (oldK, oldV) -> oldV == null ? null
                    : EType.or(v, oldV)));
            return new ContextTypes(newTypes);
        }

        ContextTypes and(Variable var, EType value) {
            if (values == null) {
                return new ContextTypes(Collections.singletonMap(var, value));
            }
            EType oldType = values.get(var);
            if (Objects.equals(value, oldType))
                return this;
            EType newType = EType.and(oldType, value);
            if (Objects.equals(newType, oldType))
                return this;
            Map<Variable, EType> newTypes = new HashMap<>(values);
            newTypes.put(var, newType);
            return new ContextTypes(newTypes);
        }

        ContextTypes remove(Variable var) {
            if (values != null && values.containsKey(var)) {
                if (values.size() == 1)
                    return DEFAULT;
                Map<Variable, EType> newTypes = new HashMap<>(values);
                newTypes.remove(var);
                return new ContextTypes(newTypes);
            }
            return this;
        }

        ContextTypes transfer(Expression expr) {
            Variable var = Nodes.getWrittenVariable(expr);
            return var == null ? this : remove(var);
        }

        EType resolve(Expression expr) {
            Object oper = expr.getOperand();
            EType result = oper instanceof Variable && values != null ? values.get(oper) : null;
            return result == EType.UNKNOWN ? null : result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            ContextTypes other = (ContextTypes) obj;
            return Objects.equals(values, other.values);
        }

        @Override
        public String toString() {
            return values == null ? "{}" : values.toString();
        }

    }

    class ETypeDataflow implements Dataflow<EType, ContextTypes> {
        private final ContextTypes initial;

        ETypeDataflow(ContextTypes initial) {
            this.initial = initial;
        }

        @Override
        public ContextTypes makeEntryState() {
            return initial;
        }

        @Override
        public ContextTypes transferState(ContextTypes src, Expression expr) {
            if (expr.getCode() == AstCode.CheckCast) {
                Expression arg = expr.getArguments().get(0);
                if (arg.getCode() == AstCode.Load) {
                    Variable var = (Variable) arg.getOperand();
                    EType type = EType.subType((TypeReference) expr.getOperand());
                    return src.and(var, type);
                }
            }
            return src.transfer(expr);
        }

        @Override
        public ContextTypes transferExceptionalState(ContextTypes src, Expression expr) {
            if (expr.getCode() == AstCode.CheckCast) {
                Expression arg = expr.getArguments().get(0);
                if (arg.getCode() == AstCode.Load) {
                    Variable var = (Variable) arg.getOperand();
                    EType type = EType.subType((TypeReference) expr.getOperand()).negate();
                    return src.and(var, type);
                }
            }
            return src.transfer(expr);
        }

        @Override
        public TrueFalse<ContextTypes> transferConditionalState(ContextTypes src, Expression expr) {
            boolean invert = false;
            while (expr.getCode() == AstCode.LogicalNot) {
                invert = !invert;
                expr = expr.getArguments().get(expr.getArguments().size() - 1);
            }
            Variable var = null;
            EType etype = null;
            if (expr.getCode() == AstCode.InstanceOf) {
                Expression arg = expr.getArguments().get(0);
                if (arg.getCode() == AstCode.Load) {
                    var = (Variable) arg.getOperand();
                    etype = EType.subType((TypeReference) expr.getOperand());
                }
            } else if (expr.getCode() == AstCode.CmpEq || expr.getCode() == AstCode.CmpNe || (expr
                    .getCode() == AstCode.InvokeVirtual && Methods.isEqualsMethod((MethodReference) expr
                            .getOperand()))) {
                if(expr.getCode() == AstCode.CmpNe)
                    invert = !invert;
                Expression left = expr.getArguments().get(0);
                Expression right = expr.getArguments().get(1);
                Object clazz = Inf.CONST.getValue(right);
                Expression arg = null;
                if(clazz instanceof TypeReference) {
                    arg = left;
                } else {
                    clazz = Inf.CONST.getValue(left);
                    if(clazz instanceof TypeReference) {
                        arg = right;
                    }
                }
                if(arg != null && arg.getCode() == AstCode.InvokeVirtual && Methods.isGetClass((MethodReference) arg.getOperand())) {
                    Expression target = arg.getArguments().get(0);
                    if(target.getCode() == AstCode.Load) {
                        var = (Variable) target.getOperand();
                        etype = EType.exact((TypeReference) clazz);
                    }
                }
            } else if (expr.getCode() == AstCode.InvokeVirtual) {
                MethodReference mr = (MethodReference) expr.getOperand();
                if(mr.getName().equals("isInstance") && Types.is(mr.getDeclaringType(), Class.class)) {
                    Object clazz = Inf.CONST.getValue(expr.getArguments().get(0));
                    Expression target = expr.getArguments().get(1);
                    if(clazz instanceof TypeReference && target.getCode() == AstCode.Load) {
                        var = (Variable) target.getOperand();
                        etype = EType.subType((TypeReference) clazz);
                    }
                }
            }
            if (var != null) {
                return new TrueFalse<>(src.and(var, etype), src.and(var, etype.negate()), invert);
            }
            return new TrueFalse<>(src);
        }

        @Override
        public ContextTypes mergeStates(ContextTypes s1, ContextTypes s2) {
            return s1.merge(s2);
        }

        @Override
        public boolean sameState(ContextTypes s1, ContextTypes s2) {
            return s1.equals(s2);
        }

        @Override
        public EType makeFact(ContextTypes state, Expression expr) {
            switch (expr.getCode()) {
            case TernaryOp: {
                Object cond = Inf.CONST.get(expr.getArguments().get(0));
                EType left = get(expr.getArguments().get(1));
                EType right = get(expr.getArguments().get(2));
                if (Integer.valueOf(1).equals(cond) || Boolean.TRUE.equals(cond)) {
                    return left;
                }
                if (Integer.valueOf(0).equals(cond) || Boolean.FALSE.equals(cond)) {
                    return right;
                }
                return EType.or(left, right);
            }
            case Load: {
                Variable v = (Variable) expr.getOperand();
                return EType.and(fromSource(state, expr), EType.subType(MetadataHelper.erase(v.getType())));
            }
            case GetField:
            case GetStatic: {
                return EType.and(fromSource(state, expr), EType.subType(((FieldReference) expr.getOperand())
                        .getFieldType()));
            }
            case InitObject:
            case InitArray:
            case MultiANewArray:
            case NewArray:
                return EType.exact(expr.getInferredType());
            case InvokeVirtual:
            case InvokeStatic:
            case InvokeSpecial:
            case InvokeInterface: {
                MethodReference mr = (MethodReference) expr.getOperand();
                return EType.subType(MetadataHelper.erase(mr).getReturnType());
            }
            case CheckCast:
                return EType.and(EType.subType(MetadataHelper.erase((TypeReference) expr.getOperand())), get(expr
                        .getArguments().get(0)));
            case Store:
            case PutStatic:
                return get(expr.getArguments().get(0));
            case PutField:
                return get(expr.getArguments().get(1));
            case StoreElement:
                return get(expr.getArguments().get(2));
            case LoadElement:
                return EType.subType(expr.getInferredType());
            default:
                return EType.UNKNOWN;
            }
        }

        @Override
        public EType makeUnknownFact() {
            return EType.UNKNOWN;
        }

        @Override
        public EType mergeFacts(EType f1, EType f2) {
            return EType.or(f1, f2);
        }

        @Override
        public boolean sameFact(EType f1, EType f2) {
            return Objects.equals(f1, f2);
        }

        private EType resolve(ContextTypes ctx, Expression expr) {
            if (expr.getCode() == AstCode.LdC) {
                return EType.exact(expr.getInferredType());
            }
            EType val = get(expr);
            if (val != EType.UNKNOWN && val != null) {
                return val;
            }
            EType resolved = ctx.resolve(expr);
            if (resolved != null)
                return resolved;
            return val;
        }

        private EType fromSource(ContextTypes ctx, Expression expr) {
            EType value = ctx.resolve(expr);
            if (value != null)
                return value;
            Expression src = ValuesFlow.getSource(expr);
            if (src == expr)
                return EType.UNKNOWN;
            value = resolve(ctx, src);
            if (value != null)
                return value;
            if (src.getCode() == SourceAnnotator.PHI_TYPE) {
                for (Expression child : src.getArguments()) {
                    EType newVal = resolve(ctx, child);
                    if (newVal == null) {
                        if (Exprs.isParameter(child) || child.getCode() == SourceAnnotator.UPDATE_TYPE) {
                            return EType.UNKNOWN;
                        }
                    } else if (value == null) {
                        value = newVal;
                    } else {
                        value = EType.or(value, newVal);
                    }
                    if (value == EType.UNKNOWN)
                        return EType.UNKNOWN;
                }
                return value;
            }
            return EType.UNKNOWN;
        }
    }
}
