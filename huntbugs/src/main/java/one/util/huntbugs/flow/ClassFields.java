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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import one.util.huntbugs.db.FieldStats;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.Expression;

/**
 * @author lan
 *
 */
public class ClassFields {
    Map<MemberInfo, FieldDefinition> fields = new HashMap<>();
    Map<MemberInfo, Expression> values = new HashMap<>();
    Set<FieldDefinition> initializedInCtor = new HashSet<>();

    public ClassFields(TypeDefinition td, FieldStats fieldStats) {
        for (FieldDefinition fd : td.getDeclaredFields()) {
            fields.put(new MemberInfo(fd), fd);
            int flags = fieldStats.getFlags(fd);
            if(Flags.testAny(flags, FieldStats.WRITE_CONSTRUCTOR) &&
                    !Flags.testAny(flags, FieldStats.WRITE_CLASS | FieldStats.WRITE_PACKAGE | FieldStats.WRITE_OUTSIDE)) {
                initializedInCtor.add(fd);
            }
        }
    }

    public boolean isKnownFinal(MemberInfo field) {
        FieldDefinition fd = fields.get(field);
        return fd != null && fd.isFinal();
    }

    public boolean isKnownEffectivelyFinal(MemberInfo field) {
        FieldDefinition fd = fields.get(field);
        return fd != null && (fd.isFinal() || initializedInCtor.contains(fd));
    }
    
    void mergeFinalFields(Frame frame) {
        if(frame == null)
            return;
        frame.fieldValues.forEach((mi, expr) -> {
            FieldDefinition fd = fields.get(mi);
            if (fd != null && !fd.isStatic() && (fd.isFinal() || (fd.isPrivate() || fd.isPackagePrivate())
                && initializedInCtor.contains(fd))) {
                // TODO: better merging
                values.merge(mi, expr, Frame::makePhiNode);
            }
        });
    }

    void setStaticFinalFields(Frame frame) {
        if(frame == null)
            return;
        frame.fieldValues.forEach((mi, expr) -> {
            FieldDefinition fd = fields.get(mi);
            if(fd != null && fd.isStatic() && (fd.isFinal() || (fd.isPrivate() || fd.isPackagePrivate())
                    && initializedInCtor.contains(fd))) {
                values.put(mi, expr);
            }
        });
    }
}
