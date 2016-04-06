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
package one.util.huntbugs.registry;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.assertions.MethodAsserter;
import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.Detector.VisitorType;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.Location;
import one.util.huntbugs.warning.WarningType;

import com.strobel.assembler.ir.attributes.LineNumberTableAttribute;
import com.strobel.assembler.ir.attributes.SourceAttribute;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Variable;
import com.strobel.decompiler.languages.java.LineNumberTableConverter;
import com.strobel.decompiler.languages.java.OffsetToLineNumberConverter;

/**
 * @author lan
 *
 */
public class MethodContext {
    static class WarningInfo {
        private final WarningType type;
        private int score;
        private final List<WarningAnnotation<?>> annotations;
        private Location bestLocation;
        private final List<Location> locations = new ArrayList<>();
    
        public WarningInfo(WarningType type, int score, Location loc, List<WarningAnnotation<?>> annotations) {
            super();
            this.type = type;
            this.score = score;
            this.annotations = annotations;
            this.bestLocation = loc;
        }
    
        boolean tryMerge(WarningInfo other) {
            if (!other.type.equals(type) || !other.annotations.equals(annotations)) {
                return false;
            }
            if(other.score > score) {
                this.score = other.score;
                if(bestLocation != null)
                    this.locations.add(bestLocation);
                bestLocation = other.bestLocation;
            } else {
                if(other.bestLocation != null) {
                    this.locations.add(other.bestLocation);
                }
            }
            this.locations.addAll(other.locations);
            return true;
        }
        
        Warning build() {
            if(bestLocation != null)
                annotations.add(WarningAnnotation.forLocation(bestLocation));
            locations.stream().map(WarningAnnotation::forAnotherInstance).forEach(annotations::add);
            return new Warning(type, score, annotations);
        }
    }

    private final MethodDefinition md;
    private final Detector detector;
    private final Context ctx;
    private final Object det;
    private OffsetToLineNumberConverter ltc;
    private final ClassContext cc;
    List<WarningAnnotation<?>> annot;
    private MethodAsserter ma;
    private WarningInfo lastWarning;
    private final Map<VisitorType, List<MethodHandle>> visitors;

    MethodContext(Context ctx, ClassContext classCtx, MethodDefinition md) {
        this.cc = classCtx;
        this.md = md;
        this.ctx = ctx;
        this.detector = classCtx == null ? null : classCtx.detector;
        this.det = classCtx == null ? null : classCtx.det;
        if(detector == null) {
            visitors = Collections.emptyMap();
        } else {
            visitors = new EnumMap<>(detector.visitors);
            visitors.replaceAll((k, v) -> new ArrayList<>(v));
        }
    }

    void setMethodAsserter(MethodAsserter ma) {
        this.ma = ma;
    }

    int getLineNumber(int offset) {
        if (ltc == null) {
            ltc = createConverter();
        }
        int line = ltc.getLineForOffset(offset);
        return line == OffsetToLineNumberConverter.UNKNOWN_LINE_NUMBER ? -1 : line;
    }

    private OffsetToLineNumberConverter createConverter() {
        for (SourceAttribute sa : md.getSourceAttributes()) {
            if (sa instanceof LineNumberTableAttribute) {
                return new LineNumberTableConverter((LineNumberTableAttribute) sa);
            }
        }
        return OffsetToLineNumberConverter.NOOP_CONVERTER;
    }

    void visitNode(Node node, NodeChain parents) {
        for (Entry<VisitorType, List<MethodHandle>> entry : visitors.entrySet()) {
            try {
                switch(entry.getKey()) {
                case AST_NODE_VISITOR:
                    for(Iterator<MethodHandle> it = entry.getValue().iterator(); it.hasNext();) {
                        MethodHandle mh = it.next();
                        if(!(boolean)mh.invoke(det, node, parents, this, md, cc.type)) {
                            it.remove();
                        }
                    }
                    break;
                case AST_EXPRESSION_VISITOR:
                    if(node instanceof Expression) {
                        for(Iterator<MethodHandle> it = entry.getValue().iterator(); it.hasNext();) {
                            MethodHandle mh = it.next();
                            if(!(boolean)mh.invoke(det, (Expression)node, parents, this, md, cc.type)) {
                                it.remove();
                            }
                        }
                    }
                    break;
                case AST_BODY_VISITOR:
                    if(parents == null) {
                        for(MethodHandle mh : entry.getValue()) {
                            mh.invoke(det, node, this, md, cc.type);
                        }
                    }
                    break;
                }
            } catch (Throwable e) {
                ctx.addError(new ErrorMessage(detector, md, -1, e));
            }
        }
    }

    void finalizeMethod() {
        if(lastWarning != null) {
            Warning warn = lastWarning.build();
            ma.checkWarning(this, warn);
            ctx.addWarning(warn);
        }
    }

    List<WarningAnnotation<?>> getMethodSpecificAnnotations() {
        if (annot == null) {
            annot = Collections.singletonList(WarningAnnotation.forMethod(md));
        }
        return annot;
    }

    public void report(String warning, int scoreAdjustment, Node node, WarningAnnotation<?>... annotations) {
        WarningType wt = detector.getWarningType(warning);
        if (wt == null) {
            error("Tries to report a warning of non-declared type: " + warning);
            return;
        }
        if (wt.getBaseScore() + scoreAdjustment < 0) {
            return;
        }
        List<WarningAnnotation<?>> anno = new ArrayList<>();
        anno.addAll(cc.getTypeSpecificAnnotations());
        anno.addAll(getMethodSpecificAnnotations());
        Location loc = null;
        if (node instanceof Expression) {
            Expression expr = (Expression) node;
			int offset = expr.getOffset();
            if (offset != Expression.MYSTERY_OFFSET) {
                loc = new Location(offset, getLineNumber(offset));
            }
            Object operand = expr.getOperand();
            if(operand instanceof Variable) {
                anno.add(WarningAnnotation.forVariable((Variable) operand));
                operand = ValuesFlow.getSource(expr).getOperand();
            }
            if(operand instanceof FieldReference) {
                anno.add(WarningAnnotation.forField((FieldReference) operand));
            }
            if(operand instanceof MethodReference) {
                anno.add(WarningAnnotation.forReturnValue((MethodReference) operand));
            }
        }
        anno.addAll(Arrays.asList(annotations));
        WarningInfo info = new WarningInfo(wt, scoreAdjustment, loc, anno);
        if(lastWarning == null) {
            lastWarning = info;
        } else if(!lastWarning.tryMerge(info)) {
            Warning warn = lastWarning.build();
            ma.checkWarning(this, warn);
            ctx.addWarning(warn);
            lastWarning = info;
        }
    }

    public void forgetLastBug() {
        lastWarning = null;
	}

	public void error(String message) {
        ctx.addError(new ErrorMessage(detector, md, -1, message));
    }

    @Override
    public String toString() {
        return "Analyzing method " + md + " with detector " + detector;
    }
}
