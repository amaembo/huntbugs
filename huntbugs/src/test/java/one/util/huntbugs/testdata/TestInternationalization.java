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
package one.util.huntbugs.testdata;

import java.util.Locale;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestInternationalization {
    @AssertWarning(type="ConvertCaseWithDefaultLocale") 
    public String toLowerCase(String s) {
        return s.toLowerCase();
    }

    @AssertWarning(type="ConvertCaseWithDefaultLocale") 
    public String toUpperCase(String s) {
        return s.toUpperCase();
    }

    @AssertNoWarning(type="*") 
    public String toLowerCaseOk(String s) {
        return s.toLowerCase(Locale.ENGLISH);
    }
    
    @AssertNoWarning(type="*") 
    public String toUpperCaseOk(String s) {
        return s.toUpperCase(Locale.ENGLISH);
    }
}
