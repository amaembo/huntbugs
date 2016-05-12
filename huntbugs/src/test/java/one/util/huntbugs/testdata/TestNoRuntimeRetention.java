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
package one.util.huntbugs.testdata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestNoRuntimeRetention {
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface MyAnno {} 

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnoRuntime {} 
    
    @AssertWarning(type="AnnotationNoRuntimeRetention")
    public boolean testNoRuntimeRetention(Object obj) {
        return obj.getClass().isAnnotationPresent(MyAnno.class);
    }

    @AssertWarning(type="AnnotationNoRuntimeRetention")
    public boolean testNoRuntimeRetention(Method m) {
        return m.getDeclaredAnnotation(MyAnno.class) != null;
    }

    @AssertNoWarning(type="*")
    public boolean testRuntimeRetention(Method m) {
        return m.getDeclaredAnnotation(MyAnnoRuntime.class) != null;
    }
}
