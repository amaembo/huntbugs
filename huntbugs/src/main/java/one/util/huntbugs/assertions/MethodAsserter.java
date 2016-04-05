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
package one.util.huntbugs.assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import one.util.huntbugs.assertions.AssertionData.Status;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;
import one.util.huntbugs.warning.Warning;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.annotations.AnnotationParameter;
import com.strobel.assembler.metadata.annotations.ConstantAnnotationElement;
import com.strobel.assembler.metadata.annotations.CustomAnnotation;

/**
 * @author lan
 *
 */
public class MethodAsserter {
    private static final MethodAsserter EMPTY_ASSERTER = new MethodAsserter(null);
    
    private final List<AssertionData> data;
    private final List<AssertionData> finalData;
    
    public MethodAsserter(List<AssertionData> data) {
        super();
        this.data = data;
        this.finalData = data == null ? null : new ArrayList<>(data);
    }

    public static MethodAsserter forMethod(MethodDefinition md) {
        List<AssertionData> assertions = new ArrayList<>();
        for(CustomAnnotation anno : md.getAnnotations()) {
            if(anno.getAnnotationType().getFullName().equals(AssertWarning.class.getName())) {
                String type = "";
                int minRank = 0, maxRank = 100;
                for(AnnotationParameter param : anno.getParameters()) {
                    if(param.getMember().equals("type"))
                        type = (String) ((ConstantAnnotationElement)param.getValue()).getConstantValue();
                    else if(param.getMember().equals("minRank"))
                        minRank = (int) ((ConstantAnnotationElement)param.getValue()).getConstantValue();
                    else if(param.getMember().equals("maxRank"))
                        maxRank = (int) ((ConstantAnnotationElement)param.getValue()).getConstantValue();
                }
                assertions.add(new AssertionData(true, type, minRank, maxRank));
            } else if(anno.getAnnotationType().getFullName().equals(AssertNoWarning.class.getName())) {
                String type = "";
                for(AnnotationParameter param : anno.getParameters()) {
                    if(param.getMember().equals("type"))
                        type = (String) ((ConstantAnnotationElement)param.getValue()).getConstantValue();
                }
                assertions.add(new AssertionData(false, type, Warning.MIN_RANK, Warning.MAX_RANK));
            }
        }
        if(assertions.isEmpty())
            return EMPTY_ASSERTER;
        return new MethodAsserter(assertions);
    }
    
    public void checkWarning(MethodContext ctx, Warning warning) {
        if(data == null)
            return;
        for(int i=0; i<data.size(); i++) {
            AssertionData ad = data.get(i);
            Status status = ad.check(warning);
            if(status == Status.PASS)
                finalData.set(i, null);
            else if(status == Status.FAIL) {
                ctx.error("Unexpected warning: "+warning+" (rule: "+ad+")");
            }
        }
    }
    
    public void checkFinally(MethodContext ctx) {
        if(finalData == null)
            return;
        for(ListIterator<AssertionData> it = finalData.listIterator(); it.hasNext(); ) {
            AssertionData ad = it.next();
            if(ad == null) continue;
            Status status = ad.finalStatus();
            if(status == Status.FAIL) {
                ctx.error("Warning rule is not satisfied: "+ad);
            }
        }        
    }
}
