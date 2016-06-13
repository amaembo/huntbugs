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
package one.util.huntbugs.util;

import com.strobel.assembler.metadata.IAnnotationsProvider;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.assembler.metadata.annotations.CustomAnnotation;

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author shustkost
 *
 */
public class Annotations {
    private static final String INTERNAL_ANNOTATION_PACKAGE = AssertWarning.class.getPackage().getName();

    public static boolean hasAnnotation(IAnnotationsProvider fd, boolean ignoreDeprecated) {
        for (CustomAnnotation ca : fd.getAnnotations()) {
            TypeReference annoType = ca.getAnnotationType();
            if (annoType.getPackageName().equals(INTERNAL_ANNOTATION_PACKAGE))
                continue;
            if (ignoreDeprecated && annoType.getInternalName().equals("java/lang/Deprecated"))
                continue;
            String simpleName = annoType.getSimpleName();
            if (simpleName.startsWith("Suppress") && simpleName.endsWith("Warning"))
                continue;
            if (simpleName.equalsIgnoreCase("nonnull") || simpleName.equalsIgnoreCase("notnull") || simpleName
                    .equalsIgnoreCase("nullable") || simpleName.equalsIgnoreCase("checkfornull"))
                continue;
            return true;
        }
        return false;
    }

}
