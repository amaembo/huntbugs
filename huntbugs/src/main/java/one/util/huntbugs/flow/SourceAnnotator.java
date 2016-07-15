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
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Maps;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author Tagir Valeev
 *
 */
public class SourceAnnotator extends Annotator<Expression> implements Dataflow<Expression, SourceAnnotator.Frame>{
    static final AstCode PHI_TYPE = AstCode.Wrap;
    static final AstCode UPDATE_TYPE = AstCode.Nop;

    static class Frame {
        private final Map<Variable, Expression> sources;
        private final FrameContext fc;
        final Map<MemberInfo, Expression> fieldValues;
        final Map<ParameterDefinition, Expression> initial;
        
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

        private Frame(Frame parent, Map<Variable, Expression> sources, Map<MemberInfo, Expression> fields) {
            this.fc = parent.fc;
            this.initial = parent.initial;
            this.fieldValues = fields;
            this.sources = sources;
        }

        Expression get(Variable var) {
            Expression expr = sources.get(var);
            if(expr != null)
                return expr;
            return initial.get(var.getOriginalParameter());
        }

        private void putInitial(ParameterDefinition thisParam) {
            Expression pde = new Expression(AstCode.Load, thisParam, 0);
            pde.setExpectedType(thisParam.getParameterType());
            pde.setInferredType(thisParam.getParameterType());
            initial.put(thisParam, pde);
        }

        Frame merge(Frame other, FrameContext fc) {
            Map<Variable, Expression> res = mergeSources(other, fc);
            Map<MemberInfo, Expression> resFields = mergeFields(other.fieldValues, fc);
            if(resFields == null && res == null)
                return this;
            if(resFields == null)
                resFields = fieldValues;
            if(res == null)
                res = sources;
            return new Frame(this, res, resFields);
        }

        private Map<MemberInfo, Expression> mergeFields(Map<MemberInfo, Expression> other, FrameContext fc) {
            Map<MemberInfo, Expression> resFields = null;
            for (Entry<MemberInfo, Expression> e : fieldValues.entrySet()) {
                Expression left = e.getValue();
                Expression right = other.get(e.getKey());
                Expression phi = left == null || right == null ? null : makePhiNode(left, right, fc);
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

        private Map<Variable, Expression> mergeSources(Frame other, FrameContext fc) {
            Map<Variable, Expression> res = null;
            for (Entry<Variable, Expression> e : sources.entrySet()) {
                Expression left = e.getValue();
                Expression right = other.get(e.getKey());
                Expression phi = makePhiNode(left, right, fc);
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
                    res.put(e.getKey(), makePhiNode(e.getValue(), initial.get(e.getKey().getOriginalParameter()), fc));
                }
            }
            return res;
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
                if(!isExprEqual(e.getValue(), r.get(e.getKey())))
                    return false;
            }
            Map<MemberInfo, Expression> lf = left.fieldValues;
            Map<MemberInfo, Expression> rf = right.fieldValues;
            if(lf.size() != rf.size())
                return false;
            for(Entry<MemberInfo, Expression> e : lf.entrySet()) {
                if(!isExprEqual(e.getValue(), rf.get(e.getKey())))
                    return false;
            }
            return true;
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

        Frame replaceAll(UnaryOperator<Expression> op) {
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
    }

    SourceAnnotator() {
        super("source", null);
    }
    
    Frame origFrame;
    ClassFields cf;
    FrameContext fc;
    
    Frame build(ClassFields cf, CFG cfg) {
        // TODO: refactor to make it stateless
        this.origFrame = null;
        this.cf = cf;
        return cfg.runDFA(this, 7) ? origFrame == null ? makeEntryState(cfg.md, null) : origFrame : null;
    }
    
    public Expression getSource(Expression input) {
        Expression source = get(input);
        return source == null ? input : source;
    }

    @Override
    public Frame makeTopState() {
        return null;
    }
    
    @Override
    public Frame makeEntryState(MethodDefinition md, Frame closureState) {
        this.fc = new FrameContext(md, cf);
        Frame initialFrame = new Frame(fc, closureState);
        if(origFrame == null)
            origFrame = initialFrame;
        return initialFrame;
    }

    @Override
    public Frame transferState(Frame target, Expression expr) {
        switch (expr.getCode()) {
        case Store: {
            Variable var = ((Variable) expr.getOperand());
            Expression arg = expr.getArguments().get(0);
            Expression source = get(arg);
            if(source == null)
                source = arg;
            return target.replace(var, source);
        }
        case Inc:
            if (expr.getOperand() instanceof Variable) {
                Variable var = ((Variable) expr.getOperand());
                target = target.replace(var, expr);
            }
            return target;
        case PostIncrement:
        case PreIncrement: {
            Expression arg = expr.getArguments().get(0);
            if (arg.getOperand() instanceof Variable) {
                return target.replace(((Variable) arg.getOperand()), expr);
            }
            if (arg.getCode() == AstCode.GetField) {
                FieldReference fr = ((FieldReference) arg.getOperand());
                if(fc.isThis(Exprs.getChild(arg, 0))) {
                    MemberInfo mi = new MemberInfo(fr);
                    Expression prevExpr = target.fieldValues.get(mi);
                    if(prevExpr != null)
                        target = target.replaceField(fr, fc.makeUpdatedNode(prevExpr));
                }
                return target.replaceAll(src -> src.getCode() == AstCode.GetField
                    && fr.isEquivalentTo((FieldReference) src.getOperand()) ? fc.makeUpdatedNode(src) : src);
            }
            if(arg.getCode() == AstCode.GetStatic) {
                FieldReference fr = ((FieldReference) arg.getOperand());
                MemberInfo mi = new MemberInfo(fr);
                Expression prevExpr = target.fieldValues.get(mi);
                if(prevExpr != null)
                    target = target.replaceField(fr, fc.makeUpdatedNode(prevExpr));
                return target.replaceAll(src -> src.getCode() == AstCode.GetStatic
                    && fr.isEquivalentTo((FieldReference) src.getOperand()) ? fc.makeUpdatedNode(src) : src);
            }
            return target;
        }
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
                            Map<MemberInfo,Expression> newFields = new HashMap<>(target.fieldValues);
                            newFields.putAll(ctorFields);
                            target = new Frame(target, target.sources, Maps.compactify(newFields));
                        }
                    }
                    else
                        target = target.deleteAllFields();
                }
                else
                    target = target.deleteFields();
            }
            return target;
        }
        case StoreElement: {
            return target.replaceAll(src -> src.getCode() == AstCode.LoadElement ? fc.makeUpdatedNode(src) : src);
        }
        case PutField: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if(fc.isThis(Exprs.getChild(expr, 0))) {
                target = target.replaceField(fr, Exprs.getChild(expr, 1));
            }
            return target.replaceAll(src -> src.getCode() == AstCode.GetField
                && fr.isEquivalentTo((FieldReference) src.getOperand()) ? fc.makeUpdatedNode(src) : src);
        }
        case PutStatic: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            return target.replaceField(fr, Exprs.getChild(expr, 0)).replaceAll(src -> src
                    .getCode() == AstCode.GetStatic && fr.isEquivalentTo((FieldReference) src.getOperand())
                            ? fc.makeUpdatedNode(src) : src);
        }
        default:
            return target;
        }
    }
    @Override
    public Frame transferExceptionalState(Frame src, Expression expr) {
        return transferState(src, expr);
    }
    @Override
    public TrueFalse<Frame> transferConditionalState(Frame src, Expression expr) {
        return new TrueFalse<>(transferState(src, expr));
    }
    @Override
    public Frame mergeStates(Frame s1, Frame s2) {
        if (s1 == null || s1 == s2)
            return s2;
        if (s2 == null)
            return s1;
        return s1.merge(s2, fc);
    }
    
    @Override
    public boolean sameState(Frame s1, Frame s2) {
        return Frame.isEqual(s1, s2);
    }

    @Override
    public Expression makeFact(Frame state, Expression expr) {
        switch (expr.getCode()) {
        case Load: {
            Variable var = ((Variable) expr.getOperand());
            return state.get(var);
        }
        case Inc:
            if (expr.getOperand() instanceof Variable) {
                Variable var = ((Variable) expr.getOperand());
                Expression source = state.get(var);
                return source == null ? null : fc.makeUpdatedNode(source);
            }
            break;
        case PostIncrement:
        case PreIncrement: {
            Expression arg = expr.getArguments().get(0);
            if (arg.getOperand() instanceof Variable) {
                Variable var = ((Variable) arg.getOperand());
                Expression source = state.get(var);
                return source == null ? null : fc.makeUpdatedNode(source);
            }
            break;
        }
        case GetStatic: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            return state.fieldValues.get(new MemberInfo(fr));
        }
        case GetField: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if(fc.isThis(Exprs.getChild(expr, 0))) {
                return state.fieldValues.get(new MemberInfo(fr));
            }
            break;
        }
        default:
        }
        return null;
    }
    
    @Override
    public Expression makeUnknownFact() {
        return null;
    }

    @Override
    public Expression mergeFacts(Expression f1, Expression f2) {
        return makePhiNode(f1, f2, fc);
    }
    @Override
    public boolean sameFact(Expression f1, Expression f2) {
        return isExprEqual(f1, f2);
    }

    static Stream<Expression> children(Set<Expression> visited, Expression parent) {
        if(parent.getCode() == PHI_TYPE) {
            return parent.getArguments().stream();
        } else if(parent.getCode() == AstCode.TernaryOp) {
            if(!visited.add(parent))
                return Stream.empty();
            return IntStream.of(1, 2).mapToObj(i -> Inf.SOURCE.getSource(parent.getArguments().get(i)))
                    .flatMap(ch -> children(visited, ch));
        } else
            return Stream.of(parent);
    }

    static Expression makePhiNode(Expression left, Expression right, FrameContext fc) {
        if (left == null)
            return right;
        if (right == null || left == right)
            return left;
        if (left.getCode() == UPDATE_TYPE) {
            Expression leftContent = left.getArguments().get(0);
            if (leftContent == right)
                return left;
            if(right.getCode() == UPDATE_TYPE) {
                Expression rightContent = right.getArguments().get(0);
                if(leftContent == rightContent)
                    return left;
                return fc.makeUpdatedNode(makePhiNode(leftContent, rightContent, fc));
            }
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

    static boolean isExprEqual(Expression left, Expression right) {
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

    @Override
    public void onSuccess(Frame exitState) {
        fc.makeFieldsFrom(exitState);
    }
}
