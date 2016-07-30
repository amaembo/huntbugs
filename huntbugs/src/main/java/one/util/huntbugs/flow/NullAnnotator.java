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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Methods;

/**
 * @author shustkost
 *
 */
public class NullAnnotator extends Annotator<Nullness> {

    NullAnnotator() {
        super("null", null);
    }

    boolean build(CFG cfg) {
        return cfg.<ContextNulls, Nullness> runDFA(this, (md, closure) -> new NullDataflow(md, closure == null
                ? ContextNulls.DEFAULT : closure), 7);
    }

    public Nullness resolve(Expression expr) {
        Nullness nullability = super.get(expr);
        return nullability == null ? Nullness.UNKNOWN : nullability;
    }

    static class ContextNulls {
        static final ContextNulls DEFAULT = new ContextNulls(null, false);

        final Map<Variable, Nullness> values;
        final boolean exceptional;

        private ContextNulls(Map<Variable, Nullness> values, boolean exceptional) {
            this.values = values;
            this.exceptional = exceptional;
        }

        ContextNulls merge(ContextNulls other) {
            if (this == other)
                return this;
            if (values == null || other.values == null)
                return (exceptional && other.exceptional) ? new ContextNulls(null, true) : DEFAULT;
            Map<Variable, Nullness> newNulls = new HashMap<>(values);
            newNulls.keySet().retainAll(other.values.keySet());
            if (newNulls.isEmpty() && !exceptional)
                return DEFAULT;
            if(exceptional && !other.exceptional)
                other.values.forEach((k, v) -> newNulls.compute(k, (oldK, oldV) -> oldV == null ? null : v.orExceptional(oldV).unknownToNull()));
            else if(!exceptional && other.exceptional)
                other.values.forEach((k, v) -> newNulls.compute(k, (oldK, oldV) -> oldV == null ? null : oldV.orExceptional(v).unknownToNull()));
            else
                other.values.forEach((k, v) -> newNulls.compute(k, (oldK, oldV) -> oldV == null ? null : v.or(oldV).unknownToNull()));
            return new ContextNulls(newNulls, exceptional && other.exceptional);
        }

        ContextNulls and(Variable var, Nullness value) {
            if (values == null) {
                return new ContextNulls(Collections.singletonMap(var, value), exceptional);
            }
            Nullness oldNullability = values.get(var);
            if (Objects.equals(value, oldNullability))
                return this;
            Nullness newNullability = oldNullability == null ? value : oldNullability.and(value);
            if (newNullability == oldNullability)
                return this;
            Map<Variable, Nullness> newNulls = new HashMap<>(values);
            newNulls.put(var, newNullability);
            return new ContextNulls(newNulls, exceptional);
        }

        ContextNulls add(Variable var, Nullness value) {
            if (values == null) {
                return new ContextNulls(Collections.singletonMap(var, value), exceptional);
            }
            Nullness oldNullability = values.get(var);
            if (Objects.equals(value, oldNullability))
                return this;
            Map<Variable, Nullness> newNulls = new HashMap<>(values);
            newNulls.put(var, value);
            return new ContextNulls(newNulls, exceptional);
        }

        ContextNulls remove(Variable var) {
            if (values != null && values.containsKey(var)) {
                if (values.size() == 1)
                    return DEFAULT;
                Map<Variable, Nullness> newNulls = new HashMap<>(values);
                newNulls.remove(var);
                return new ContextNulls(newNulls, exceptional);
            }
            return this;
        }
        
        ContextNulls exceptional() {
            return exceptional ? this : new ContextNulls(values, true);
        }

        ContextNulls transfer(Expression expr) {
            if (expr.getCode() == AstCode.Store) {
                return add((Variable) expr.getOperand(), Inf.NULL.get(expr.getArguments().get(0)));
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
            return exceptional == other.exceptional && Objects.equals(values, other.values);
        }

        @Override
        public String toString() {
            return (values == null ? "{}" : values) + (exceptional ? "*" : "");
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
                if (lcName.contains("error") && !mr.getDeclaringType().getSimpleName().contains("Log") || lcName
                        .contains("throw"))
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
                    return src.and(var, Nullness.NONNULL_DEREF);
                }
                return src;
            }
            case InvokeStatic: {
                MethodReference mr = (MethodReference) expr.getOperand();
                String name = mr.getName();
                String typeName = mr.getDeclaringType().getInternalName();
                if (typeName.endsWith("/Assert") && name.equals("assertNotNull") || typeName.equals(
                    "com/google/common/base/Preconditions") && name.equals("checkNotNull") || typeName.equals(
                        "java/util/Objects") && name.equals("requireNonNull")) {
                    if (expr.getArguments().size() == 1) {
                        Expression arg = expr.getArguments().get(0);
                        if (arg.getCode() == AstCode.Load) {
                            Variable var = (Variable) arg.getOperand();
                            return src.and(var, Nullness.NONNULL_CHECKED);
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
                            return src.and(var, Nullness.NONNULL_CHECKED);
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
            return src.exceptional();
        }

        @Override
        public TrueFalse<ContextNulls> transferConditionalState(ContextNulls src, Expression expr) {
            boolean invert = false;
            while (expr.getCode() == AstCode.LogicalNot) {
                invert = !invert;
                expr = expr.getArguments().get(expr.getArguments().size() - 1);
            }
            Variable var = null;
            if (expr.getCode() == AstCode.InstanceOf) {
                Expression arg = expr.getArguments().get(0);
                if (arg.getCode() == AstCode.Load) {
                    var = (Variable) arg.getOperand();
                    return new TrueFalse<>(src.and(var, Nullness.NONNULL_CHECKED), src, invert);
                }
            } else if (expr.getCode() == AstCode.CmpEq || expr.getCode() == AstCode.CmpNe) {
                if (expr.getCode() == AstCode.CmpNe)
                    invert = !invert;
                Expression left = expr.getArguments().get(0);
                Expression right = expr.getArguments().get(1);
                ContextNulls trueSrc = src;
                if (left.getCode() == AstCode.Load) {
                    var = (Variable) left.getOperand();
                    Nullness nullness = Inf.NULL.get(right);
                    if (nullness != null && nullness.isNull())
                        return new TrueFalse<>(src.and(var, Nullness.nullAt(expr)), src.and(var, Nullness.NONNULL_CHECKED),
                                invert);
                    trueSrc = src.add(var, nullness);
                }
                if (right.getCode() == AstCode.Load) {
                    var = (Variable) right.getOperand();
                    Nullness nullness = Inf.NULL.get(left);
                    if (nullness != null && nullness.isNull())
                        return new TrueFalse<>(trueSrc.and(var, Nullness.nullAt(expr)), src.and(var, Nullness.NONNULL_CHECKED),
                                invert);
                    return new TrueFalse<>(trueSrc.and(var, nullness), src, invert);
                }
            } else if (expr.getCode() == AstCode.InvokeVirtual && Methods.isEqualsMethod((MethodReference) expr
                    .getOperand())) {
                Expression arg = expr.getArguments().get(1);
                if (arg.getCode() == AstCode.Load) {
                    var = (Variable) arg.getOperand();
                    return new TrueFalse<>(src.and(var, Nullness.NONNULL_CHECKED), src, invert);
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
                return Nullness.NONNULL;
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
                return left.or(right);
            }
            case Load:
                if (!md.isStatic() && Exprs.isThis(expr))
                    return Nullness.NONNULL;
                return state.resolve(expr);
            case GetField:
                return Nullness.UNKNOWN;
            // Cannot reliably make facts from fields until they are directly supported by Context
            //return fromSource(state, expr);
            case GetStatic: {
                FieldReference fr = (FieldReference) expr.getOperand();
                FieldDefinition fd = fr.resolve();
                if (fd != null && fd.isEnumConstant())
                    return Nullness.NONNULL;
                return Nullness.UNKNOWN;
                //return fromSource(state, expr);
            }
            case InitObject:
            case InitArray:
            case MultiANewArray:
            case NewArray:
                return Nullness.NONNULL;
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

        private Nullness resolve(ContextNulls ctx, Expression expr) {
            if (expr.getCode() == AstCode.LdC) {
                return Nullness.NONNULL;
            }
            Nullness f1 = ctx.resolve(expr);
            Nullness f2 = get(expr);
            return f1 == null ? f2 : f1.and(f2);
        }

        private Nullness fromSource(ContextNulls ctx, Expression expr) {
            Expression src = ValuesFlow.getSource(expr);
            if (src == expr)
                return Nullness.UNKNOWN;
            Nullness value = resolve(ctx, src);
            if (value != null)
                return value;
            if (src.getCode() == SourceAnnotator.PHI_TYPE) {
                for (Expression child : src.getArguments()) {
                    Nullness newVal = resolve(ctx, child);
                    if (newVal == null) {
                        return Nullness.UNKNOWN;
                    }
                    if (value == null) {
                        value = newVal;
                    } else {
                        value = value.or(newVal);
                    }
                    if (value == Nullness.UNKNOWN)
                        return Nullness.UNKNOWN;
                }
                return value;
            }
            return Nullness.UNKNOWN;
        }
    }
}
