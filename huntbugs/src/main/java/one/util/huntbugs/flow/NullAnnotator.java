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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.flow.Nullness.NullState;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Methods;

/**
 * @author Tagir Valeev
 *
 */
public class NullAnnotator extends Annotator<Nullness> {

    NullAnnotator() {
        super("null", null);
    }

    boolean build(CFG cfg) {
        return cfg.<ContextNulls, Nullness> runDFA(this, (md, closure) -> new NullDataflow(md,
                closure == null ? ContextNulls.DEFAULT : closure), 7);
    }

    public Nullness resolve(Expression expr) {
        Nullness nullability = super.get(expr);
        return nullability == null ? Nullness.UNKNOWN : nullability;
    }

    static class ContextNulls {
        static final ContextNulls DEFAULT = new ContextNulls(null);

        final Map<Variable, Nullness> values;

        private ContextNulls(Map<Variable, Nullness> values) {
            this.values = values;
        }

        ContextNulls merge(ContextNulls other) {
            if (this == other)
                return this;
            if (values == null || other.values == null)
                return DEFAULT;
            Set<Variable> vars = new HashSet<>(values.keySet());
            vars.addAll(other.values.keySet());
            Map<Variable, Nullness> newNulls = new HashMap<>();
            for(Variable v : vars) {
                Nullness n1 = get(values, v);
                Nullness n2 = get(other.values, v);
                if(n1 != null && n2 != null) {
                    Nullness n = n1.or(n2);
                    if(n != null)
                        newNulls.put(v, n);
                }
            }
            return newNulls.isEmpty() ? DEFAULT : new ContextNulls(newNulls);
        }

        private static Nullness get(Map<Variable, Nullness> map, Variable v) {
            Nullness nullness = map.get(v);
            if(nullness != null)
                return nullness;
            ParameterDefinition pd = v.getOriginalParameter();
            if(pd != null) {
                return Nullness.UNKNOWN_AT_ENTRY;
            }
            return null;
        }

        ContextNulls add(Variable var, Nullness value) {
            if (values == null) {
                return new ContextNulls(Collections.singletonMap(var, value));
            }
            Nullness oldNullability = values.get(var);
            if (Objects.equals(value, oldNullability))
                return this;
            Map<Variable, Nullness> newNulls = new HashMap<>(values);
            newNulls.put(var, value);
            return new ContextNulls(newNulls);
        }

        ContextNulls remove(Variable var) {
            if (values != null && values.containsKey(var)) {
                if (values.size() == 1)
                    return DEFAULT;
                Map<Variable, Nullness> newNulls = new HashMap<>(values);
                newNulls.remove(var);
                return new ContextNulls(newNulls);
            }
            return this;
        }

        ContextNulls transfer(Expression expr) {
            if (expr.getCode() == AstCode.Store) {
                Nullness value = Inf.NULL.get(expr.getArguments().get(0));
                if(value == Nullness.UNKNOWN)
                    value = Nullness.createAt(expr, NullState.UNKNOWN);
                return add((Variable) expr.getOperand(), value);
            }
            return this;
        }

        Nullness resolve(Expression expr) {
            Object oper = expr.getOperand();
            Nullness result = oper instanceof Variable && values != null ? values.get(oper) : null;
            return result == null ? Nullness.UNKNOWN : result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            ContextNulls other = (ContextNulls) obj;
            return Objects.equals(values, other.values);
        }

        @Override
        public String toString() {
            return values == null ? "{}" : values.toString();
        }

    }

    class NullDataflow implements Dataflow<Nullness, ContextNulls> {
        private final ContextNulls initial;
        private final MethodDefinition md;

        NullDataflow(MethodDefinition md, ContextNulls initial) {
            this.initial = initial;
            this.md = md;
        }

        @Override
        public ContextNulls makeEntryState() {
            return initial;
        }

        @Override
        public ContextNulls transferState(ContextNulls src, Expression expr) {
            switch (expr.getCode()) {
            case InvokeInterface:
            case InvokeSpecial:
            case InvokeStatic:
            case InvokeVirtual:
                MethodReference mr = (MethodReference) expr.getOperand();
                String lcName = mr.getName().toLowerCase(Locale.ENGLISH);
                if (lcName.contains("error") && !mr.getDeclaringType().getSimpleName().contains("Log")
                    || lcName.startsWith("throw") || lcName.startsWith("fail"))
                    return ContextNulls.DEFAULT;
            default:
            }
            switch (expr.getCode()) {
            case MonitorEnter:
            case MonitorExit:
            case GetField:
            case PutField:
            case InvokeInterface:
            case InvokeSpecial:
            case InvokeVirtual:
            case StoreElement:
            case LoadElement: {
                Expression arg = expr.getArguments().get(0);
                if (arg.getCode() == AstCode.Load) {
                    Variable var = (Variable) arg.getOperand();
                    return src.add(var, Nullness.createAt(expr, NullState.NONNULL_DEREF));
                }
                return src;
            }
            case InvokeStatic: {
                MethodReference mr = (MethodReference) expr.getOperand();
                String name = mr.getName();
                String typeName = mr.getDeclaringType().getInternalName();
                if (typeName.endsWith("/Assert") && name.equals("assertNotNull")
                    || typeName.equals("com/google/common/base/Preconditions") && name.equals("checkNotNull")
                    || typeName.equals("java/util/Objects") && name.equals("requireNonNull")) {
                    if (expr.getArguments().size() == 1) {
                        Expression arg = expr.getArguments().get(0);
                        if (arg.getCode() == AstCode.Load) {
                            Variable var = (Variable) arg.getOperand();
                            return src.add(var, Nullness.createAt(expr, NullState.NONNULL_CHECKED));
                        }
                    }
                    if (expr.getArguments().size() == 2) {
                        Expression arg = null;
                        if (mr.getErasedSignature().startsWith("(Ljava/lang/Object;")) {
                            arg = expr.getArguments().get(0);
                        } else if (mr.getErasedSignature().startsWith("(Ljava/lang/String;Ljava/lang/Object;)")) {
                            arg = expr.getArguments().get(1);
                        }
                        if (arg != null && arg.getCode() == AstCode.Load) {
                            Variable var = (Variable) arg.getOperand();
                            return src.add(var, Nullness.createAt(expr, NullState.NONNULL_CHECKED));
                        }
                    }
                }
                break;
            }
            default:
            }
            return src.transfer(expr);
        }

        @Override
        public ContextNulls transferExceptionalState(ContextNulls src, Expression expr) {
            return src;
        }

        @Override
        public TrueFalse<ContextNulls> transferConditionalState(ContextNulls src, Expression expr) {
            src = transferState(src, expr);
            boolean invert = false;
            while (expr.getCode() == AstCode.LogicalNot) {
                invert = !invert;
                expr = expr.getArguments().get(expr.getArguments().size() - 1);
            }
            Variable var;
            if (expr.getCode() == AstCode.InstanceOf) {
                Expression arg = expr.getArguments().get(0);
                if (arg.getCode() == AstCode.Load) {
                    var = (Variable) arg.getOperand();
                    return new TrueFalse<>(src.add(var, Nullness.createAt(expr, NullState.NONNULL_CHECKED)), src, invert);
                }
            } else if (expr.getCode() == AstCode.CmpEq || expr.getCode() == AstCode.CmpNe) {
                if (expr.getCode() == AstCode.CmpNe)
                    invert = !invert;
                Expression left = expr.getArguments().get(0);
                Expression right = expr.getArguments().get(1);
                ContextNulls trueSrc = src;
                if (left.getCode() == AstCode.Load) {
                    var = (Variable) left.getOperand();
                    Nullness nullness = get(right);
                    if (nullness != null && nullness.isNull())
                        return new TrueFalse<>(src.add(var, Nullness.nullAt(expr)), src.add(var, Nullness.createAt(
                            expr, NullState.NONNULL_CHECKED)), invert);
                    trueSrc = src.add(var, nullness);
                }
                if (right.getCode() == AstCode.Load) {
                    var = (Variable) right.getOperand();
                    Nullness nullness = get(left);
                    if (nullness != null && nullness.isNull())
                        return new TrueFalse<>(trueSrc.add(var, Nullness.nullAt(expr)), src.add(var, Nullness.createAt(
                            expr, NullState.NONNULL_CHECKED)), invert);
                    return new TrueFalse<>(trueSrc.add(var, nullness), src, invert);
                }
            } else if (expr.getCode() == AstCode.InvokeVirtual
                && Methods.isEqualsMethod((MethodReference) expr.getOperand())) {
                Expression arg = expr.getArguments().get(1);
                if (arg.getCode() == AstCode.Load) {
                    var = (Variable) arg.getOperand();
                    return new TrueFalse<>(src.add(var, Nullness.createAt(expr, NullState.NONNULL_CHECKED)), src,
                            invert);
                }
            }
            return new TrueFalse<>(src);
        }

        @Override
        public ContextNulls mergeStates(ContextNulls s1, ContextNulls s2) {
            return s1.merge(s2);
        }

        @Override
        public boolean sameState(ContextNulls s1, ContextNulls s2) {
            return s1.equals(s2);
        }

        @Override
        public Nullness makeFact(ContextNulls state, Expression expr) {
            if (Inf.CONST.getValue(expr) != null)
                return Nullness.createAt(expr, NullState.NONNULL);
            switch (expr.getCode()) {
            case TernaryOp: {
                Object cond = Inf.CONST.get(expr.getArguments().get(0));
                Nullness left = get(expr.getArguments().get(1));
                Nullness right = get(expr.getArguments().get(2));
                if (Integer.valueOf(1).equals(cond) || Boolean.TRUE.equals(cond)) {
                    return left;
                }
                if (Integer.valueOf(0).equals(cond) || Boolean.FALSE.equals(cond)) {
                    return right;
                }
                if(left == right)
                    return left;
                if(left == Nullness.UNKNOWN)
                    left = Nullness.createAt(expr.getArguments().get(1), NullState.UNKNOWN); 
                if(right == Nullness.UNKNOWN)
                    right = Nullness.createAt(expr.getArguments().get(2), NullState.UNKNOWN); 
                return left.or(right);
            }
            case Load:
                if (!md.isStatic() && Exprs.isThis(expr))
                    return Nullness.createAt(expr, NullState.NONNULL);
                return state.resolve(expr);
            case GetField:
                return Nullness.UNKNOWN;
            case GetStatic: {
                FieldReference fr = (FieldReference) expr.getOperand();
                FieldDefinition fd = fr.resolve();
                if (fd != null && fd.isEnumConstant())
                    return Nullness.createAt(expr, NullState.NONNULL);
                return Nullness.UNKNOWN;
            }
            case InitObject:
            case InitArray:
            case MultiANewArray:
            case NewArray:
                return Nullness.createAt(expr, NullState.NONNULL);
            case CheckCast:
            case Store:
            case PutStatic:
                return get(expr.getArguments().get(0));
            case PutField:
                return get(expr.getArguments().get(1));
            case StoreElement:
                return get(expr.getArguments().get(2));
            case AConstNull:
                return Nullness.nullAt(expr);
            default:
                return Nullness.UNKNOWN;
            }
        }

        @Override
        public Nullness makeUnknownFact() {
            return Nullness.UNKNOWN;
        }

        @Override
        public Nullness mergeFacts(Nullness f1, Nullness f2) {
            if (f1 == null)
                return f2;
            return f1.or(f2);
        }

        @Override
        public boolean sameFact(Nullness f1, Nullness f2) {
            return Objects.equals(f1, f2);
        }
    }
}
