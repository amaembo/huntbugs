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

    public static boolean hasAnnotation(IAnnotationsProvider fd) {
        for (CustomAnnotation ca : fd.getAnnotations()) {
            TypeReference annoType = ca.getAnnotationType();
            if (annoType.getPackageName().equals(AssertWarning.class.getPackage().getName()))
                continue;
            if (annoType.getSimpleName().startsWith("Suppress") && annoType.getSimpleName().endsWith("Warning"))
                continue;
            if (annoType.getSimpleName().equalsIgnoreCase("nonnull") || annoType.getSimpleName().equalsIgnoreCase(
                "notnull") || annoType.getSimpleName().equalsIgnoreCase("nullable") || annoType.getSimpleName()
                        .equalsIgnoreCase("checkfornull"))
                continue;
            return true;
        }
        return false;
    }

}
