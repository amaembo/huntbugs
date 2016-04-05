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
import java.util.List;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.assertions.MethodAsserter;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningType;

import com.strobel.assembler.ir.attributes.LineNumberTableAttribute;
import com.strobel.assembler.ir.attributes.SourceAttribute;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.languages.java.LineNumberTableConverter;
import com.strobel.decompiler.languages.java.OffsetToLineNumberConverter;

/**
 * @author lan
 *
 */
public class MethodContext {
    private final MethodDefinition md;
    private final Detector detector;
    private final Context ctx;
    private final Object det;
    private OffsetToLineNumberConverter ltc;
    private final ClassContext cc;
    List<WarningAnnotation<?>> annot;
    private MethodAsserter ma;

    MethodContext(Context ctx, ClassContext classCtx, MethodDefinition md) {
        this.cc = classCtx;
        this.md = md;
        this.ctx = ctx;
        this.detector = classCtx == null ? null : classCtx.detector;
        this.det = classCtx == null ? null : classCtx.det;
    }
    
    void setMethodAsserter(MethodAsserter ma) {
        this.ma = ma;
    }
    
    int getLineNumber(int offset) {
        if(ltc == null) {
            ltc = createConverter();
        }
        int line = ltc.getLineForOffset(offset);
        return line == OffsetToLineNumberConverter.UNKNOWN_LINE_NUMBER ? -1 : line;
    }
    
    private OffsetToLineNumberConverter createConverter() {
        for(SourceAttribute sa : md.getSourceAttributes()) {
            if(sa instanceof LineNumberTableAttribute) {
                return new LineNumberTableConverter((LineNumberTableAttribute) sa);
            }
        }
        return OffsetToLineNumberConverter.NOOP_CONVERTER;
    }
    
    void visitNode(Node node, NodeChain parents) {
        for(MethodHandle mh : detector.astVisitors) {
            try {
                mh.invoke(det, node, parents, this, md, cc.type);
            } catch (Throwable e) {
                ctx.addError(new ErrorMessage(detector, md, -1, e));
            }
        }
    }
    
    List<WarningAnnotation<?>> getMethodSpecificAnnotations() {
        if(annot == null) {
            annot = Collections.singletonList(WarningAnnotation.forMethod(md));
        }
        return annot;
    }

    public void report(String warning, int rankAdjustment, Node node, 
            WarningAnnotation<?>... annotations) {
        WarningType wt = detector.getWarningType(warning);
        if (wt == null) {
            error("Tries to report a warning of non-declared type: " + warning);
            return;
        }
        if (wt.getBaseRank() + rankAdjustment < 0) {
            return;
        }
        List<WarningAnnotation<?>> anno = new ArrayList<>();
        anno.addAll(cc.getTypeSpecificAnnotations());
        anno.addAll(getMethodSpecificAnnotations());
        if(node instanceof Expression) {
            int offset = ((Expression)node).getOffset();
            if(offset != Expression.MYSTERY_OFFSET) {
                anno.add(WarningAnnotation.forByteCodeOffset(offset));
                int lineNumber = getLineNumber(offset);
                if(lineNumber != -1) {
                    anno.add(WarningAnnotation.forSourceLine(lineNumber));
                }
            }
        }
        anno.addAll(Arrays.asList(annotations));
        Warning warn = new Warning(wt, rankAdjustment, anno);
        ma.checkWarning(this, warn);
        ctx.addWarning(warn);
    }

    public void error(String message) {
        ctx.addError(new ErrorMessage(detector, md, -1, 
            new IllegalStateException(message)));
    }
}
