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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import one.util.huntbugs.flow.ValuesFlow.ThrowTargets;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Maps;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

class Frame {
    static final TypeDefinition runtimeException;
    static final TypeDefinition nullPointerException;
    static final TypeDefinition arrayIndexOutOfBoundsException;
    static final TypeDefinition arrayStoreException;
    static final TypeDefinition outOfMemoryError;
    static final TypeDefinition linkageError;
    static final TypeDefinition error;
    static final TypeDefinition throwable;
    static final TypeDefinition exception;
    
    static {
        MetadataSystem ms = MetadataSystem.instance();
        throwable = getException(ms, "java/lang/Throwable");
        exception = getException(ms, "java/lang/Exception");
        runtimeException = getException(ms, "java/lang/RuntimeException");
        nullPointerException = getException(ms, "java/lang/NullPointerException");
        arrayIndexOutOfBoundsException = getException(ms, "java/lang/ArrayIndexOutOfBoundsException");
        arrayStoreException = getException(ms, "java/lang/ArrayStoreException");
        outOfMemoryError = getException(ms, "java/lang/OutOfMemoryError");
        linkageError = getException(ms, "java/lang/LinkageError");
        error = getException(ms, "java/lang/Error");
    }

    static TypeDefinition getException(MetadataSystem ms, String internalName) {
        TypeReference tr = ms.lookupType(internalName);
        if(tr == null) {
            throw new InternalError("Unable to lookup exception "+internalName);
        }
        TypeDefinition td = tr.resolve();
        if(td == null) {
            throw new InternalError("Unable to resolve exception "+internalName);
        }
        return td;
    }
    
    private final Map<Variable, Expression> sources;
    private final FrameContext fc;
    final Map<MemberInfo, Expression> fieldValues;
    final Map<ParameterDefinition, Expression> initial;
    static final AstCode PHI_TYPE = AstCode.Wrap;
    static final AstCode UPDATE_TYPE = AstCode.Nop;
    
    Expression get(Variable var) {
        Expression expr = sources.get(var);
        if(expr != null)
            return expr;
        return initial.get(var.getOriginalParameter());
    }

    Frame(FrameContext fc, Frame closure) {
        this.sources = new IdentityHashMap<>();
        this.fieldValues = fc.getInitialFields();
        this.fc = fc;
        this.initial = new IdentityHashMap<>();
        for(ParameterDefinition pd : fc.md.getParameters()) {
            putInitial(pd);
        }
        ParameterDefinition thisParam = fc.md.getBody().getThisParameter();
        if(thisParam != null) {
            putInitial(thisParam);
        }
        if(closure != null) {
            initial.putAll(closure.initial);
            sources.putAll(closure.sources);
        }
    }

    private void putInitial(ParameterDefinition thisParam) {
        Expression pde = new Expression(AstCode.Load, thisParam, 0);
        pde.setExpectedType(thisParam.getParameterType());
        pde.setInferredType(thisParam.getParameterType());
        initial.put(thisParam, pde);
    }

    Frame processChildren(Expression expr, ThrowTargets targets) {
        Frame result = this;
        for (Expression child : expr.getArguments()) {
            result = result.process(child, targets);
        }
        return result;
    }

    Frame process(Expression expr, ThrowTargets targets) {
        if (expr.getCode() == AstCode.TernaryOp) {
            Expression cond = expr.getArguments().get(0);
            Expression left = expr.getArguments().get(1);
            Expression right = expr.getArguments().get(2);
            Frame target = process(cond, targets);
            Frame leftFrame = target.process(left, targets);
            Frame rightFrame = target.process(right, targets);
            return leftFrame.merge(rightFrame);
        }
        if (expr.getCode() == AstCode.LogicalAnd || expr.getCode() == AstCode.LogicalOr) {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            Frame target = process(left, targets);
            return target.merge(target.process(right, targets));
        }
        Frame target = processChildren(expr, targets);
        switch (expr.getCode()) {
        case Store: {
            Variable var = ((Variable) expr.getOperand());
            Expression arg = expr.getArguments().get(0);
            Expression source = ValuesFlow.getSource(arg);
            return target.replace(var, source);
        }
        case ArrayLength:
            targets.merge(nullPointerException, target);
            return target;
        case Load: {
            Variable var = ((Variable) expr.getOperand());
            // TODO: support transferring variables from outer method to lambda
            Expression source = get(var);
            if (source != null) {
                Inf.SOURCE.put(expr, source);
            }
            return this;
        }
        case Inc:
            if (expr.getOperand() instanceof Variable) {
                Variable var = ((Variable) expr.getOperand());
                Expression source = get(var);
                target = target.replace(var, expr);
                if(source != null)
                    Inf.SOURCE.put(expr, fc.makeUpdatedNode(source));
            }
            return target;
        case PostIncrement:
        case PreIncrement: {
            Expression arg = expr.getArguments().get(0);
            if (arg.getOperand() instanceof Variable) {
                Variable var = ((Variable) arg.getOperand());
                Expression source = get(var);
                Inf.SOURCE.put(expr, fc.makeUpdatedNode(source));
                return target.replace(var, expr);
            } else if (arg.getCode() == AstCode.GetField) {
                FieldReference fr = ((FieldReference) arg.getOperand());
                if(fc.isThis(Exprs.getChild(arg, 0))) {
                    MemberInfo mi = new MemberInfo(fr);
                    Expression prevExpr = fieldValues.get(mi);
                    if(prevExpr != null)
                        target = target.replaceField(fr, fc.makeUpdatedNode(prevExpr));
                }
                return target.replaceAll(src -> src.getCode() == AstCode.GetField
                    && fr.isEquivalentTo((FieldReference) src.getOperand()) ? fc.makeUpdatedNode(src) : src);
            } else if(arg.getCode() == AstCode.GetStatic) {
                FieldReference fr = ((FieldReference) arg.getOperand());
                MemberInfo mi = new MemberInfo(fr);
                Expression prevExpr = fieldValues.get(mi);
                if(prevExpr != null)
                    target = target.replaceField(fr, fc.makeUpdatedNode(prevExpr));
                return target.replaceAll(src -> src.getCode() == AstCode.GetStatic
                    && fr.isEquivalentTo((FieldReference) src.getOperand()) ? fc.makeUpdatedNode(src) : src);
            }
            return target;
        }
        case Bind:
            targets.merge(error, target);
            targets.merge(runtimeException, target);
            return target;
        case InitObject:
        case InvokeInterface:
        case InvokeSpecial:
        case InvokeStatic:
        case InvokeVirtual: {
            MethodReference mr = (MethodReference) expr.getOperand();
            if (!fc.cf.isSideEffectFree(mr, expr.getCode() == AstCode.InitObject || expr.getCode() == AstCode.InvokeSpecial)) {
                target = target.replaceAll(src -> src.getCode() == AstCode.GetField || src.getCode() == AstCode.GetStatic
                        || src.getCode() == AstCode.LoadElement ? fc.makeUpdatedNode(src) : src);
                // calling another constructor from current constructor will initialize all final fields
                if(expr.getCode() == AstCode.InvokeSpecial && fc.md.isConstructor() && mr.isConstructor() && 
                        Exprs.isThis(expr.getArguments().get(0)) && mr.getDeclaringType().isEquivalentTo(fc.md.getDeclaringType())) {
                    Map<MemberInfo, Expression> ctorFields = fc.getCtorFields(mr);
                    if(ctorFields != null) {
                        if(!ctorFields.isEmpty()) {
                            Map<MemberInfo,Expression> newFields = new HashMap<>(fieldValues);
                            newFields.putAll(ctorFields);
                            target = new Frame(target, sources, Maps.compactify(newFields));
                        }
                    }
                    else
                        target = target.deleteAllFields();
                }
                else
                    target = target.deleteFields();
            }
            if(!targets.isEmpty()) {
                targets.merge(error, target);
                targets.merge(runtimeException, target);
                MethodDefinition md = mr.resolve();
                if(md != null) {
                    for(TypeReference thrownType : md.getThrownTypes()) {
                        targets.merge(thrownType, target);
                    }
                } else {
                    targets.merge(exception, target);
                }
            }
            return target;
        }
        case LoadElement:
            targets.merge(arrayIndexOutOfBoundsException, target);
            targets.merge(nullPointerException, target);
            return target;
        case StoreElement: {
            targets.merge(arrayIndexOutOfBoundsException, target);
            targets.merge(arrayStoreException, target);
            targets.merge(nullPointerException, target);
            return target.replaceAll(src -> src.getCode() == AstCode.LoadElement ? fc.makeUpdatedNode(src) : src);
        }
        case __New:
        case NewArray:
        case InitArray:
        case MultiANewArray:
            targets.merge(outOfMemoryError, target);
            return target;
        case GetStatic: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if(!fr.getDeclaringType().isEquivalentTo(fc.md.getDeclaringType()))
                targets.merge(linkageError, target);
            Expression source = fieldValues.get(new MemberInfo(fr));
            if (source != null) {
                Inf.SOURCE.put(expr, source);
            }
            return target;
        }
        case GetField: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if(fc.isThis(Exprs.getChild(expr, 0))) {
                Expression source = fieldValues.get(new MemberInfo(fr));
                if (source != null) {
                    Inf.SOURCE.put(expr, source);
                }
            } else {
                targets.merge(nullPointerException, target);
                targets.merge(linkageError, target);
            }
            return target;
        }
        case PutField: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if(fc.isThis(Exprs.getChild(expr, 0))) {
                target = target.replaceField(fr, Exprs.getChild(expr, 1));
            } else {
                targets.merge(nullPointerException, target);
                targets.merge(linkageError, target);
            }
            return target.replaceAll(src -> src.getCode() == AstCode.GetField
                && fr.isEquivalentTo((FieldReference) src.getOperand()) ? fc.makeUpdatedNode(src) : src);
        }
        case PutStatic: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if(!fr.getDeclaringType().isEquivalentTo(fc.md.getDeclaringType()))
                targets.merge(linkageError, target);
            return target.replaceField(fr, Exprs.getChild(expr, 0)).replaceAll(src -> src
                    .getCode() == AstCode.GetStatic && fr.isEquivalentTo((FieldReference) src.getOperand())
                            ? fc.makeUpdatedNode(src) : src);
        }
        default:
            return target;
        }
    }

    Frame merge(Frame other) {
        Map<Variable, Expression> res = mergeSources(other);
        Map<MemberInfo, Expression> resFields = mergeFields(other.fieldValues);
        if(resFields == null && res == null)
            return this;
        if(resFields == null)
            resFields = fieldValues;
        if(res == null)
            res = sources;
        return new Frame(this, res, resFields);
    }

    private Map<MemberInfo, Expression> mergeFields(Map<MemberInfo, Expression> other) {
        Map<MemberInfo, Expression> resFields = null;
        for (Entry<MemberInfo, Expression> e : fieldValues.entrySet()) {
            Expression left = e.getValue();
            Expression right = other.get(e.getKey());
            Expression phi = left == null || right == null ? null : makePhiNode(left, right);
            if (phi == left)
                continue;
            if (resFields == null)
                resFields = new HashMap<>(fieldValues);
            resFields.put(e.getKey(), phi);
        }
        if(resFields == null)
            return null;
        resFields.values().removeIf(Objects::isNull);
        return Maps.compactify(resFields);
    }

    private Map<Variable, Expression> mergeSources(Frame other) {
        Map<Variable, Expression> res = null;
        for (Entry<Variable, Expression> e : sources.entrySet()) {
            Expression left = e.getValue();
            Expression right = other.get(e.getKey());
            Expression phi = makePhiNode(left, right);
            if (phi == left)
                continue;
            if (res == null)
                res = new IdentityHashMap<>(sources);
            res.put(e.getKey(), phi);
        }
        for(Entry<Variable, Expression> e : other.sources.entrySet()) {
            if(!sources.containsKey(e.getKey())) {
                if (res == null)
                    res = new IdentityHashMap<>(sources);
                res.put(e.getKey(), makePhiNode(e.getValue(), initial.get(e.getKey().getOriginalParameter())));
            }
        }
        return res;
    }

    static Frame combine(Frame left, Frame right) {
        if (left == null || left == right)
            return right;
        if (right == null)
            return left;
        return left.merge(right);
    }

    private static boolean isEqual(Expression left, Expression right) {
        if (left == right)
            return true;
        if (left == null || right == null)
            return false;
        if (left.getCode() == PHI_TYPE && right.getCode() == PHI_TYPE) {
            List<Expression> leftArgs = left.getArguments();
            List<Expression> rightArgs = right.getArguments();
            if (leftArgs.size() != rightArgs.size())
                return false;
            for (Expression arg : rightArgs) {
                if (!leftArgs.contains(arg))
                    return false;
            }
            return true;
        }
        return false;
    }

    static boolean isEqual(Frame left, Frame right) {
        if (left == right)
            return true;
        if (left == null || right == null)
            return false;
        Map<Variable, Expression> l = left.sources;
        Map<Variable, Expression> r = right.sources;
        if(l.size() != r.size())
            return false;
        for(Entry<Variable, Expression> e : l.entrySet()) {
            if(!isEqual(e.getValue(), r.get(e.getKey())))
                return false;
        }
        Map<MemberInfo, Expression> lf = left.fieldValues;
        Map<MemberInfo, Expression> rf = right.fieldValues;
        if(lf.size() != rf.size())
            return false;
        for(Entry<MemberInfo, Expression> e : lf.entrySet()) {
            if(!isEqual(e.getValue(), rf.get(e.getKey())))
                return false;
        }
        return true;
    }

    private Frame(Frame parent, Map<Variable, Expression> sources, Map<MemberInfo, Expression> fields) {
        this.fc = parent.fc;
        this.initial = parent.initial;
        this.fieldValues = fields;
        this.sources = sources;
    }

    private Frame replace(Variable var, Expression replacement) {
        Expression expression = get(var);
        if (expression != replacement) {
            Map<Variable, Expression> res = new IdentityHashMap<>(sources);
            res.put(var, replacement);
            return new Frame(this, res, this.fieldValues);
        }
        return this;
    }
    
    private Frame replaceField(FieldReference fr, Expression replacement) {
        MemberInfo mi = new MemberInfo(fr);
        if(fieldValues.isEmpty()) {
            return new Frame(this, this.sources, Collections.singletonMap(mi, replacement));
        }
        Expression expression = fieldValues.get(mi);
        if (expression != replacement) {
            if(expression != null && fieldValues.size() == 1) {
                return new Frame(this, this.sources, Collections.singletonMap(mi, replacement));
            }
            Map<MemberInfo, Expression> res = new HashMap<>(fieldValues);
            res.put(mi, replacement);
            return new Frame(this, this.sources, res);
        }
        return this;
    }
    
    private Frame deleteAllFields() {
        if(fieldValues.isEmpty())
            return this;
        Map<MemberInfo, Expression> res = new HashMap<>();
        fieldValues.forEach((mi, expr) -> {
            if(!fc.cf.isKnownFinal(mi)) {
                if(expr.getCode() == UPDATE_TYPE) {
                    res.put(mi, expr);
                } else {
                    res.put(mi, fc.makeUpdatedNode(expr));
                }
            }
        });
        return new Frame(this, this.sources, Maps.compactify(res));
    }
    
    private Frame deleteFields() {
        if(fieldValues.isEmpty())
            return this;
        Map<MemberInfo, Expression> res = new HashMap<>();
        fieldValues.forEach((mi, expr) -> {
            if(expr.getCode() == UPDATE_TYPE || fc.cf.isKnownEffectivelyFinal(mi)) {
                res.put(mi, expr);
            } else {
                res.put(mi, fc.makeUpdatedNode(expr));
            }
        });
        return new Frame(this, this.sources, Maps.compactify(res));
    }

    private Frame replaceAll(UnaryOperator<Expression> op) {
        Map<Variable, Expression> res = null;
        for (Entry<Variable, Expression> e : sources.entrySet()) {
            Expression expr = op.apply(e.getValue());
            if (expr != e.getValue()) {
                if (res == null)
                    res = new IdentityHashMap<>(sources);
                res.put(e.getKey(), expr);
            }
        }
        return res == null ? this : new Frame(this, res, this.fieldValues);
    }

    static Stream<Expression> children(Set<Expression> visited, Expression parent) {
        if(parent.getCode() == PHI_TYPE) {
            return parent.getArguments().stream();
        } else if(parent.getCode() == AstCode.TernaryOp) {
            if(!visited.add(parent))
                return Stream.empty();
            return IntStream.of(1, 2).mapToObj(i -> ValuesFlow.getSource(parent.getArguments().get(i)))
                    .flatMap(ch -> children(visited, ch));
        } else
            return Stream.of(parent);
    }

    static Expression makePhiNode(Expression left, Expression right) {
        if (left == null)
            return right;
        if (right == null || left == right)
            return left;
        if (left.getCode() == UPDATE_TYPE) {
            Expression leftContent = left.getArguments().get(0);
            if (leftContent == right || right.getCode() == UPDATE_TYPE && leftContent == right.getArguments().get(0))
                return left;
        } else if (right.getCode() == UPDATE_TYPE && right.getArguments().get(0) == left) {
            return right;
        }
        List<Expression> children = new ArrayList<>();
        children(new HashSet<>(), left).forEach(children::add);
        int baseSize = children.size();
        children(new HashSet<>(), right).forEach(child -> {
            if(!children.contains(child))
                children.add(child);
        });
        if (children.size() == baseSize) {
            return left;
        }
        return new Expression(PHI_TYPE, null, 0, children);
    }
}