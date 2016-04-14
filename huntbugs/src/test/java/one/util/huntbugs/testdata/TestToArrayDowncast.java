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

import java.util.ArrayList;
import java.util.Collection;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestToArrayDowncast {
    @AssertWarning(type="ImpossibleToArrayDowncast")
    public String[] toArray(ArrayList<String> l) {
        return (String[]) l.toArray();
    }

    @AssertWarning(type="ImpossibleToArrayDowncast")
    public String[] toArray(Collection<String> l) {
        return (String[]) l.toArray();
    }
    
    @AssertNoWarning(type="ImpossibleToArrayDowncast")
    public String[] toArrayOk(Collection<String> l) {
        return l.toArray(new String[0]);
    }

    @SuppressWarnings("unchecked")
    // Probably some other warning should be issued here, but not this
    @AssertNoWarning(type="ImpossibleToArrayDowncast")
    public <T> T[] toArrayGeneric(Collection<String> l) {
        return (T[])l.toArray();
    }
}
