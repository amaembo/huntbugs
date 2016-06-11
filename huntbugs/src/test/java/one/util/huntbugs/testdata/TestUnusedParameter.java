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
 * @author shustkost
 *
 */
public class TestUnusedParameter {
    @AssertNoWarning("*")
    public TestUnusedParameter(int x, int y) {
        this(x, y, "other");
    }
    
    @AssertNoWarning("*")
    public TestUnusedParameter() {
        this(1, 2, "other");
    }
    
    @AssertWarning("ConstructorParameterIsNotPassed")
    public TestUnusedParameter(int x, String z) {
        this(x, 2, "other");
    }
    
    @AssertNoWarning("*")
    public TestUnusedParameter(int x, int y, String z) {
        System.out.println(x + y + z);
    }
    
    @AssertNoWarning("*")
    public void print(String info, int x) {
        System.out.println(info+" "+x);
    }
    
    @AssertWarning("MethodParameterIsNotPassed")
    public void print(int x) {
        print("test", 0);
    }
    
    @AssertNoWarning("MethodParameterIsNotPassed")
    @AssertWarning("MethodParameterIsNotUsed")
    public void print(double x) {
        print("test", 0);
    }

    public static void printStatic(String info, int x) {
        System.out.println(info+" "+x);
    }
    
    @AssertWarning("MethodParameterIsNotPassed")
    @AssertNoWarning("MethodParameterIsNotUsed")
    public static void printStatic(int x) {
        printStatic("test", 0);
    }
    
    @AssertNoWarning("MethodParameterIsNotPassed")
    @AssertWarning("MethodParameterIsNotUsed")
    public void printStatic(double x) {
        printStatic("test", 0);
    }
}
