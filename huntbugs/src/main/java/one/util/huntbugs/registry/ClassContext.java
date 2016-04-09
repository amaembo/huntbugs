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

import java.util.ArrayList;
import java.util.List;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.warning.WarningAnnotation;

import com.strobel.assembler.ir.attributes.SourceAttribute;
import com.strobel.assembler.ir.attributes.SourceFileAttribute;
import com.strobel.assembler.metadata.MethodDefinition;
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

    public MethodContext forMethod(MethodDefinition md) {
        return new MethodContext(ctx, this, md);
    }
}
