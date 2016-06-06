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
package one.util.huntbugs.filter;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.annotations.AnnotationElement;
import com.strobel.assembler.metadata.annotations.AnnotationParameter;
import com.strobel.assembler.metadata.annotations.ArrayAnnotationElement;
import com.strobel.assembler.metadata.annotations.ConstantAnnotationElement;
import com.strobel.assembler.metadata.annotations.CustomAnnotation;

/**
 * @author Tagir Valeev
 *
 */
public class AnnotationFilters {
    private static final Predicate<Warning> ALL_PASS = w -> true;
    private static final Predicate<Warning> NO_PASS = w -> false;
    
    private static Predicate<Warning> and(Predicate<Warning> left, Predicate<Warning> right) {
        if(left == ALL_PASS)
            return right;
        if(right == ALL_PASS)
            return left;
        if(left == NO_PASS || right == NO_PASS)
            return NO_PASS;
        return left.and(right);
    }
    
    private static Set<String> getSuppressed(List<CustomAnnotation> annos) {
        Set<String> filters = null;
        for (CustomAnnotation ca : annos) {
            String name = ca.getAnnotationType().getSimpleName();
            if (!(name.startsWith("Suppress") && name.endsWith("Warning")))
                continue;
            for (AnnotationParameter par : ca.getParameters()) {
                if (par.getMember().equals("value")) {
                    AnnotationElement ae = par.getValue();
                    AnnotationElement[] elements = ae instanceof ArrayAnnotationElement ? ((ArrayAnnotationElement) ae)
                            .getElements() : new AnnotationElement[] { ae };
                    for (AnnotationElement child : elements) {
                        if (child instanceof ConstantAnnotationElement) {
                            Object value = ((ConstantAnnotationElement) child).getConstantValue();
                            if (value instanceof String) {
                                if (filters == null)
                                    filters = new HashSet<>();
                                filters.add((String) value);
                            }
                        }
                    }
                }
            }
        }
        return filters == null ? Collections.emptySet() : filters;
    }
    
    private static Predicate<Warning> forSuppressed(String suppressed) {
        if(suppressed.equals("all") || suppressed.equals("*"))
            return NO_PASS;
        if(suppressed.endsWith("*")) {
            String substr = suppressed.substring(0, suppressed.length()-1);
            return w -> !w.getType().getName().startsWith(substr);
        }
        return w -> !w.getType().getName().equals(suppressed);
    }
    
    private static Predicate<Warning> forSuppressed(Set<String> suppressed) {
        return suppressed.stream().map(AnnotationFilters::forSuppressed).reduce(AnnotationFilters::and).orElse(ALL_PASS);
    }

    public static Predicate<Warning> forType(TypeDefinition td) {
        Predicate<Warning> pred = forSuppressed(getSuppressed(td.getAnnotations()));
        if(pred == NO_PASS)
            return pred;
        for(FieldDefinition fd : td.getDeclaredFields()) {
            Set<String> suppressed = getSuppressed(fd.getAnnotations());
            if(!suppressed.isEmpty()) {
                Predicate<Warning> fieldPred = forSuppressed(suppressed);
                MemberInfo mi = new MemberInfo(fd); 
                pred = and(pred, w -> !mi.equals(w.getAnnotation(Roles.FIELD)) || fieldPred.test(w));
            }
        }
        for(MethodDefinition md : td.getDeclaredMethods()) {
            Set<String> suppressed = getSuppressed(md.getAnnotations());
            MemberInfo mi = new MemberInfo(md); 
            if(!suppressed.isEmpty()) {
                Predicate<Warning> methodPred = forSuppressed(suppressed);
                pred = and(pred, w -> !mi.equals(w.getAnnotation(Roles.METHOD)) || methodPred.test(w));
            }
            for(ParameterDefinition pd : md.getParameters()) {
                suppressed = getSuppressed(pd.getAnnotations());
                if(!suppressed.isEmpty()) {
                    Predicate<Warning> paramPred = forSuppressed(suppressed);
                    String name = pd.getName(); 
                    pred = and(pred, w -> !mi.equals(w.getAnnotation(Roles.METHOD))
                        || !name.equals(w.getAnnotation(Roles.VARIABLE)) || paramPred.test(w));
                }
            }
        }
        return pred;
    }
}
