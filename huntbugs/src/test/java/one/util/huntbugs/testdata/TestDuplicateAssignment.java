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

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestDuplicateAssignment {
    int x = 1, y;
    
    @AssertNoWarning(type="FieldDoubleAssignment")
    public TestDuplicateAssignment(int a) {
        x = a;
    }
    
    @SuppressWarnings("cast")
    @AssertWarning(type="FieldDoubleAssignment")
    public void doubleFieldChain(int val) {
        this.x = (int)(this.x = Math.abs(val));
    }
    
    @AssertWarning(type="FieldDoubleAssignment")
    public void doubleField(int val) {
        this.x = Math.abs(val);
        this.x = Math.addExact(val, val);
    }
    
    @AssertNoWarning(type="FieldDoubleAssignment")
    public void doubleFieldReuse(int val) {
        this.x = Math.abs(val);
        this.x = this.x * 2 + 1;
    }
    
    @AssertNoWarning(type="FieldDoubleAssignment")
    public void doubleDiffField(int val) {
        this.x = Math.abs(val);
        this.y = Math.addExact(val, val);
    }
    
    @AssertWarning(type="FieldDoubleAssignment")
    public void doubleDiffFieldSameField(int val) {
        this.x = Math.abs(val);
        this.y = Math.addExact(val, val);
        this.x = Math.decrementExact(val);
    }
    
    @AssertWarning(type="FieldDoubleAssignment")
    public void trickyReceiver(TestDuplicateAssignment tda, int val) {
        TestDuplicateAssignment tda2 = tda;
        tda2.x = tda.x = val;
    }
    
    @AssertWarning(type="FieldDoubleAssignment")
    public void array(TestDuplicateAssignment[] arr, int val) {
        arr[val].x = val;
        arr[val].x = val;
    }

    @AssertNoWarning(type="FieldDoubleAssignment")
    public void arrayOk(TestDuplicateAssignment[] arr, int val) {
        arr[0].x = val;
        arr[1].x = val;
    }
    
    @AssertNoWarning(type="FieldDoubleAssignment")
    public void doubleDiffObject(TestDuplicateAssignment tda, int val) {
        this.x = Math.abs(val);
        tda.x = Math.addExact(val, val);
    }
}
