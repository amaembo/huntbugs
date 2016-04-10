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

import java.util.Hashtable;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestExposeRepresentation {
    int[] f;
    static Hashtable<String, Integer> ht;

    @AssertWarning(type="ExposeMutableFieldViaParameter", minScore=40)
    public void setField(int[] f) {
        if(f.length > 2)
            this.f = f;
    }
    
    @AssertNoWarning(type="ExposeMutableFieldViaParameter")
    public void setFieldClone(int[] f) {
        f = f.clone();
        this.f = f;
    }
    
    @AssertWarning(type="ExposeMutableFieldViaParameter", maxScore=39)
    public void setFieldVarArgs(int... f) {
        this.f = f;
    }
    
    @AssertNoWarning(type="ExposeMutableFieldViaParameter")
    public void setField(TestExposeRepresentation obj, int[] f) {
        obj.f = f;
    }
    
    @AssertWarning(type="ExposeMutableStaticFieldViaParameter", minScore=50)
    public static void setHashTable(Hashtable<String, Integer> ht) {
        TestExposeRepresentation.ht = ht;
    }
}
