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
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningType;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.Node;

/**
 * @author lan
 *
 */
public class MethodContext {
    private final MethodDefinition md;
    private final Detector detector;
    private final Context ctx;

    MethodContext(Context ctx, MethodDefinition md, Detector detector) {
        this.ctx = ctx;
        this.md = md;
        this.detector = detector;
    }
    
    void visitNode(Node node) {
        for(MethodHandle mh : detector.astVisitors) {
            try {
                mh.invokeExact(node, this);
            } catch (Throwable e) {
                ctx.addError(new ErrorMessage(detector, md, -1, e));
            }
        }
    }

    public void report(String warning, int rankAdjustment,
            WarningAnnotation<?>... annotations) {
        WarningType wt = detector.getWarningType(warning);
        if (wt == null) {
            ctx.addError(new ErrorMessage(detector, md, -1, 
                    new IllegalStateException("Detector " + detector
                        + " tries to report a warning of non-declared type: " + warning)));
            return;
        }
        List<WarningAnnotation<?>> anno = new ArrayList<>();
        anno.add(new WarningAnnotation.TypeWarningAnnotation(md.getDeclaringType()));
        anno.add(new WarningAnnotation.MethodWarningAnnotation(md));
        anno.addAll(Arrays.asList(annotations));
        ctx.addWarning(new Warning(wt, rankAdjustment, anno));
    }
}
