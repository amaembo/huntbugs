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
import java.util.List;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.assertions.MemberAsserter;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;
import one.util.huntbugs.warning.WarningType;

import com.strobel.assembler.ir.attributes.SourceAttribute;
import com.strobel.assembler.ir.attributes.SourceFileAttribute;
import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.TypeDefinition;

/**
 * @author Tagir Valeev
 *
 */
public class ClassContext extends ElementContext {
    final TypeDefinition type;
    final Object det;
    final ClassData cdata;
    List<WarningAnnotation<?>> annot;

    ClassContext(Context ctx, ClassData cdata, Detector detector) {
        super(ctx, detector);
        this.type = cdata.td;
        this.cdata = cdata;
        this.det = detector.newInstance();
    }
    
    List<WarningAnnotation<?>> getTypeSpecificAnnotations() {
        if(annot == null) {
            annot = new ArrayList<>();
            annot.add(Roles.TYPE.create(type));
            String sourceFile = getSourceFile();
            if(sourceFile != null)
                annot.add(Roles.FILE.create(sourceFile));
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
        WarningType wt = resolveWarningType(warning, priority);
        if(wt == null)
            return;
        List<WarningAnnotation<?>> anno = new ArrayList<>();
        anno.addAll(getTypeSpecificAnnotations());
        anno.addAll(Arrays.asList(annotations));
        Warning w = new Warning(wt, priority, anno);
        MemberAsserter ma = cdata.ca;
        MemberInfo mi = w.getAnnotation(Roles.METHOD);
        if(mi != null)
            ma = cdata.getAsserter(mi);
        ma.checkWarning(this::error, w);
        ctx.addWarning(w);
    }

    @Override
    public void error(String message) {
        ctx.addError(new ErrorMessage(detector, type, message));
    }

    MethodContext forMethod(MethodData md) {
        return new MethodContext(ctx, this, md);
    }
    
    FieldContext forField(FieldData fd) {
        return new FieldContext(ctx, this, fd);
    }
    
    MemberAsserter getMemberAsserter(MemberReference mr) {
        return cdata.getAsserter(mr);
    }
}
