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
import java.util.List;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.assertions.MemberAsserter;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningType;

import com.strobel.assembler.ir.attributes.SourceAttribute;
import com.strobel.assembler.ir.attributes.SourceFileAttribute;
import com.strobel.assembler.metadata.TypeDefinition;

/**
 * @author lan
 *
 */
public class ClassContext {
    final TypeDefinition type;
    final Detector detector;
    final Context ctx;
    final Object det;
    List<WarningAnnotation<?>> annot;
    private MemberAsserter ca;

    ClassContext(Context ctx, TypeDefinition type, Detector detector) {
        super();
        this.type = type;
        this.detector = detector;
        this.ctx = ctx;
        this.det = detector.newInstance();
    }
    
    List<WarningAnnotation<?>> getTypeSpecificAnnotations() {
        if(annot == null) {
            annot = new ArrayList<>();
            annot.add(WarningAnnotation.forType(type));
            String sourceFile = getSourceFile();
            if(sourceFile != null)
                annot.add(WarningAnnotation.forSourceFile(sourceFile));
        }
        return annot;
    }
    
    String getSourceFile() {
        for(SourceAttribute sa : type.getSourceAttributes()) {
            if(sa instanceof SourceFileAttribute) {
                return ((SourceFileAttribute)sa).getSourceFile();
            }
        }
        return null;
    }

    void setAsserter(MemberAsserter ma) {
        this.ca = ma;
    }

    boolean visitClass() {
        for(MethodHandle mh : detector.classVisitors) {
            try {
                if (!(boolean) detector.bindDatabases(Detector.CLASS_VISITOR_TYPE.parameterCount(), type, mh)
                        .invoke(det, this, type)) {
                    return false;
                }
            } catch (Throwable e) {
                ctx.addError(new ErrorMessage(detector, type, e));
            }
        }
        return !detector.methodVisitors.isEmpty() || !detector.astVisitors.isEmpty()
            || !detector.methodAfterVisitors.isEmpty() || !detector.classAfterVisitors.isEmpty();
    }
    
    void visitAfterClass() {
        for(MethodHandle mh : detector.classAfterVisitors) {
            try {
                detector.bindDatabases(Detector.CLASS_VISITOR_TYPE.parameterCount(), type, mh).invoke(det, this, type);
            } catch (Throwable e) {
                ctx.addError(new ErrorMessage(detector, type, e));
            }
        }
    }

    public void report(String warning, int priority, WarningAnnotation<?>... annotations) {
        WarningType wt = detector.getWarningType(warning);
        if (wt == null) {
            error("Tries to report a warning of non-declared type: " + warning);
            return;
        }
        if (priority < 0) {
            error("Tries to report a warning " + warning + " with negative priority " + priority);
            return;
        }
        if (wt.getMaxScore() - priority < ctx.getOptions().minScore) {
            return;
        }
        List<WarningAnnotation<?>> anno = new ArrayList<>();
        anno.addAll(getTypeSpecificAnnotations());
        anno.addAll(Arrays.asList(annotations));
        Warning w = new Warning(wt, priority, anno);
        ca.checkWarning(this::error, w);
        ctx.addWarning(w);
    }

    public void error(String message) {
        ctx.addError(new ErrorMessage(detector, type, message));
    }

    MethodContext forMethod(MethodData md) {
        return new MethodContext(ctx, this, md);
    }
}
