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
package one.util.huntbugs.registry;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.Location;
import one.util.huntbugs.warning.WarningType;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Variable;

/**
 * @author Tagir Valeev
 *
 */
public class MethodContext extends ElementContext {
    static class WarningInfo {
        private final WarningType type;
        private int priority;
        private final List<WarningAnnotation<?>> annotations;
        private Location bestLocation;
        private final List<Location> locations = new ArrayList<>();

        public WarningInfo(WarningType type, int priority, Location loc, List<WarningAnnotation<?>> annotations) {
            super();
            this.type = type;
            this.priority = priority;
            this.annotations = annotations;
            this.bestLocation = loc;
        }

        boolean tryMerge(WarningInfo other) {
            if (!other.type.equals(type) || !other.annotations.equals(annotations)) {
                return false;
            }
            if (other.priority < priority) {
                this.priority = other.priority;
                if (bestLocation != null)
                    this.locations.add(bestLocation);
                bestLocation = other.bestLocation;
            } else {
                if (other.bestLocation != null) {
                    this.locations.add(other.bestLocation);
                }
            }
            this.locations.addAll(other.locations);
            return true;
        }

        Warning build() {
            if (bestLocation != null)
                annotations.add(Roles.LOCATION.create(bestLocation));
            locations.stream().map(Roles.ANOTHER_INSTANCE::create).forEach(annotations::add);
            return new Warning(type, priority, annotations);
        }
    }

    private final MethodData mdata;
    private final Object det;
    private final ClassContext cc;
    private WarningInfo lastWarning;
    private final List<MethodHandle> astVisitors;

    MethodContext(Context ctx, ClassContext сс, MethodData md) {
        super(ctx, сс.detector);
        this.cc = сс;
        this.mdata = md;
        this.det = сс.det;
        astVisitors = detector.astVisitors.stream().filter(vi -> vi.isApplicable(md.mainMethod)).map(
            vi -> vi.bind(сс.type)).collect(Collectors.toCollection(ArrayList::new));
    }

    boolean visitMethod() {
        for(MethodHandle mh : detector.methodVisitors) {
            try {
                if (!(boolean) detector.bindDatabases(Detector.METHOD_VISITOR_TYPE.parameterCount(), cc.type, mh)
                        .invoke(det, this, mdata.mainMethod, cc.type)) {
                    return false;
                }
            } catch (Throwable e) {
                ctx.addError(new ErrorMessage(detector, mdata.mainMethod, -1, e));
            }
        }
        return !astVisitors.isEmpty() || !detector.methodAfterVisitors.isEmpty();
    }

    void visitAfterMethod() {
        for(MethodHandle mh : detector.methodVisitors) {
            try {
                detector.bindDatabases(Detector.METHOD_VISITOR_TYPE.parameterCount(), cc.type, mh)
                        .invoke(det, this, mdata.mainMethod, cc.type);
            } catch (Throwable e) {
                ctx.addError(new ErrorMessage(detector, mdata.mainMethod, -1, e));
            }
        }
    }

    boolean visitNode(Node node) {
        for (Iterator<MethodHandle> it = astVisitors.iterator(); it.hasNext();) {
            try {
                MethodHandle mh = it.next();
                if (!(boolean) mh.invoke(det, node, mdata.parents, this, mdata.mainMethod, cc.type)) {
                    it.remove();
                }
            } catch (Throwable e) {
                ctx.addError(new ErrorMessage(detector, mdata.mainMethod, -1, e));
            }
        }
        return !astVisitors.isEmpty();
    }

    void finalizeMethod() {
        if (lastWarning != null) {
            Warning warn = lastWarning.build();
            cc.getMemberAsserter(mdata.mainMethod).checkWarning(this::error, warn);
            ctx.addWarning(warn);
        }
    }

    public void report(String warning, int priority, WarningAnnotation<?>... annotations) {
        report(warning, priority, null, annotations);
    }

    public void report(String warning, int priority, Node node, WarningAnnotation<?>... annotations) {
        WarningType wt = resolveWarningType(warning, priority);
        if(wt == null)
            return;
        List<WarningAnnotation<?>> anno = new ArrayList<>();
        anno.addAll(cc.getTypeSpecificAnnotations());
        anno.addAll(mdata.getMethodSpecificAnnotations());
        Location loc = getLocation(node);
        if (node instanceof Expression) {
            Expression expr = (Expression) node;
            Object operand = expr.getOperand();
            if (operand instanceof Variable) {
                anno.add(WarningAnnotation.forVariable((Variable) operand));
                operand = ValuesFlow.getSource(expr).getOperand();
            }
            if (operand instanceof FieldReference) {
                anno.add(Roles.FIELD.create((FieldReference) operand));
            }
            if (operand instanceof MethodReference) {
                MethodReference mr = (MethodReference) operand;
                if (!mr.getReturnType().isVoid() || expr.getCode() == AstCode.InitObject)
                    anno.add(Roles.RETURN_VALUE_OF.create(mr));
                else
                    anno.add(Roles.CALLED_METHOD.create(mr));
            }
        }
        anno.addAll(Arrays.asList(annotations));
        WarningInfo info = new WarningInfo(wt, priority, loc, anno);
        if (lastWarning == null) {
            lastWarning = info;
        } else if (!lastWarning.tryMerge(info)) {
            Warning warn = lastWarning.build();
            cc.getMemberAsserter(mdata.mainMethod).checkWarning(this::error, warn);
            ctx.addWarning(warn);
            lastWarning = info;
        }
    }

    /**
     * @param node to get the location for
     * @return location object which describes given node
     */
    public Location getLocation(Node node) {
        return mdata.getLocation(node);
    }

    /**
     * Forget last bug reported by current detector. Subsequent calls of this
     * method have no effect if no new bugs were reported.
     */
    public void forgetLastBug() {
        lastWarning = null;
    }

    @Override
    public void error(String message) {
        ctx.addError(new ErrorMessage(detector, mdata.mainMethod, -1, message));
    }

    /**
     * @return true if the method is fully annotated via ValuesFlow
     */
    public boolean isAnnotated() {
        return mdata.origParams != null;
    }
    
    public Set<Expression> getParameterUsages(ParameterDefinition pd) {
        if(mdata.origParams == null)
            return null;
        for(Expression expr : mdata.origParams) {
            if(expr.getOperand() == pd)
                return ValuesFlow.findUsages(expr);
        }
        return null;
    }

    @Override
    public String toString() {
        return "Analyzing method " + mdata + " with detector " + detector;
    }

    public boolean isFullyAnalyzed() {
        return mdata.fullyAnalyzed;
    }
}
