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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.assertions.MemberAsserter;
import one.util.huntbugs.filter.AnnotationFilters;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.TypeDefinition;

/**
 * @author Tagir Valeev
 *
 */
public class ClassData {
    final TypeDefinition td;
    final MemberAsserter ca;
    Map<MemberInfo, MemberAsserter> mas;
    Predicate<Warning> filter;

    ClassData(TypeDefinition td) {
        this.td = td;
        this.ca = MemberAsserter.forMember(td);
        this.filter = AnnotationFilters.forType(td);
    }

    void finish(Context ctx) {
        ca.checkFinally(err -> ctx.addError(new ErrorMessage(null, td, err)));
        if (mas != null) {
            mas.forEach((mi, ma) -> ma.checkFinally(err -> ctx.addError(new ErrorMessage(null, mi.getTypeName(), mi
                    .getName(), mi.getSignature(), -1, err))));
        }
    }

    MemberAsserter getAsserter(MemberReference mr) {
        if (mas == null)
            return ca;
        return mas.getOrDefault(new MemberInfo(mr), ca);
    }

    MemberAsserter getAsserter(MemberInfo mi) {
        if (mas == null)
            return ca;
        return mas.getOrDefault(mi, ca);
    }
    
    MemberAsserter registerAsserter(MemberReference mr) {
        MemberAsserter ma = MemberAsserter.forMember(ca, mr);
        if (!ma.isEmpty()) {
            if (mas == null)
                mas = new HashMap<>();
            if (mas.put(new MemberInfo(mr), ma) != null) {
                throw new InternalError("Asserter is registered twice for " + mr);
            }
        }
        return ma;
    }
}
