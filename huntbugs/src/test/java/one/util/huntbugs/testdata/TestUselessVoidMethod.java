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
public class TestUselessVoidMethod {
    @AssertWarning("UselessVoidMethod")
    public void doNothing(int x) {
        int y = x;
        if (x > 0) {
            y = y * 2;
        }
    }
    
    @AssertWarning("UselessVoidMethod")
    public void uselessSwitch(int x) {
        switch(x) {
        case 1:
            break;
        }
    }
    
    boolean b;
    volatile boolean v;
    
    @AssertWarning("UselessVoidMethod")
    public void uselessSpinLoop() {
        while(b) {}
    }
    
    @AssertNoWarning("UselessVoidMethod")
    public void volatileSpinLoop() {
        while(v) {}
    }
}
