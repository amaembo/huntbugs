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
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningType;

public class FieldContext extends ElementContext {
    private final ClassContext cc;
    private final FieldData fdata;
    private final Object det;

    FieldContext(Context ctx, ClassContext cc, FieldData fdata) {
        super(ctx, cc.detector);
        this.cc = cc;
        this.fdata = fdata;
        this.det = cc.det;
    }

    void visitField() {
        for(MethodHandle mh : detector.fieldVisitors) {
            try {
                detector.bindDatabases(Detector.FIELD_VISITOR_TYPE.parameterCount(), cc.type, mh)
                        .invoke(det, this, fdata.fd, cc.type);
            } catch (Throwable e) {
                ctx.addError(new ErrorMessage(detector, fdata.fd, -1, e));
            }
        }
    }

    public void report(String warning, int priority, WarningAnnotation<?>... annotations) {
        WarningType wt = resolveWarningType(warning, priority);
        if(wt == null)
            return;
        List<WarningAnnotation<?>> anno = new ArrayList<>();
        anno.addAll(cc.getTypeSpecificAnnotations());
        anno.add(Roles.FIELD.create(fdata.fd));
        anno.addAll(Arrays.asList(annotations));
        Warning w = new Warning(wt, priority, anno);
        cc.getMemberAsserter(fdata.fd).checkWarning(this::error, w);
        ctx.addWarning(w);
    }

    @Override
    public void error(String message) {
        ctx.addError(new ErrorMessage(detector, fdata.fd, -1, message));
    }

    @Override
    public String toString() {
        return "Analyzing field " + fdata + " with detector " + detector;
    }
}
