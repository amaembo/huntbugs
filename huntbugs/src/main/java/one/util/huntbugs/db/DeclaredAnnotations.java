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
package one.util.huntbugs.db;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.annotations.AnnotationElement;
import com.strobel.assembler.metadata.annotations.AnnotationParameter;
import com.strobel.assembler.metadata.annotations.CustomAnnotation;
import com.strobel.assembler.metadata.annotations.EnumAnnotationElement;

import one.util.huntbugs.registry.AbstractTypeDatabase;
import one.util.huntbugs.registry.anno.TypeDatabase;
import one.util.huntbugs.registry.anno.TypeDatabaseItem;
import one.util.huntbugs.util.Types;

/**
 * @author lan
 *
 */
@TypeDatabase
public class DeclaredAnnotations extends AbstractTypeDatabase<DeclaredAnnotations.DeclaredAnnotation> {

    public DeclaredAnnotations() {
        super(type -> new DeclaredAnnotation());
    }

    @Override
    protected void visitType(TypeDefinition td) {
        if (!td.isAnnotation())
            return;
        DeclaredAnnotation da = getOrCreate(td);
        for (CustomAnnotation ca : td.getAnnotations()) {
            if (Types.is(ca.getAnnotationType(), Retention.class)) {
                for (AnnotationParameter ap : ca.getParameters()) {
                    if (ap.getMember().equals("value")) {
                        AnnotationElement value = ap.getValue();
                        if (value instanceof EnumAnnotationElement) {
                            EnumAnnotationElement enumValue = (EnumAnnotationElement) value;
                            if (Types.is(enumValue.getEnumType(), RetentionPolicy.class)) {
                                da.policy = RetentionPolicy.valueOf(enumValue.getEnumConstantName());
                            }
                        }
                    }
                }
            }
        }
    }

    @TypeDatabaseItem(parentDatabase = DeclaredAnnotations.class)
    public static class DeclaredAnnotation {
        RetentionPolicy policy = RetentionPolicy.CLASS;

        public RetentionPolicy getPolicy() {
            return policy;
        }
    }
}
