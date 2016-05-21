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

import java.util.HashMap;
import java.util.Map;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.Expression;

/**
 * @author lan
 *
 */
public class ClassFields {
    Map<MemberInfo, FieldDefinition> fields = new HashMap<>();
    Map<MemberInfo, Expression> values = new HashMap<>();

    public ClassFields(TypeDefinition td) {
        for (FieldDefinition fd : td.getDeclaredFields()) {
            fields.put(new MemberInfo(fd), fd);
        }
    }

    public boolean isKnownFinal(MemberInfo field) {
        FieldDefinition fd = fields.get(field);
        return fd != null && fd.isFinal();
    }

    void mergeFinalFields(Frame frame) {
        frame.fieldValues.forEach((mi, expr) -> {
            FieldDefinition fd = fields.get(mi);
            if(fd != null && !fd.isStatic() && fd.isFinal()) {
                // TODO: better merging
                values.merge(mi, expr, Frame::makePhiNode);
            }
        });
    }

    void setStaticFinalFields(Frame frame) {
        frame.fieldValues.forEach((mi, expr) -> {
            FieldDefinition fd = fields.get(mi);
            if(fd != null && fd.isStatic() && fd.isFinal()) {
                values.put(mi, expr);
            }
        });
    }
}
