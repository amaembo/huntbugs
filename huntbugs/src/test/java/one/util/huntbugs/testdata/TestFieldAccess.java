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
public class TestFieldAccess {
    @AssertWarning("UnusedPrivateField")
    private int a;

    @AssertWarning("UnusedPrivateField")
    static int b;

    @AssertWarning("UnusedPublicField")
    public int c;
    
    @AssertWarning("UnusedPublicField")
    protected static int d;
    
    @AssertNoWarning("*")
    public int e;
    
    @AssertWarning("UnreadPrivateField")
    private int f;
    
    @AssertWarning("FieldShouldBeStatic")
    @AssertNoWarning("UnreadPrivateField")
    private final String g = "test";
    
    @AssertNoWarning("FieldShouldBeStatic")
    @AssertWarning("UnreadPrivateField")
    private final String h; 
    
    {
        if(Math.random() > 0.5)
            h = "pass";
        else
            h = "fail";
    }
    
    @AssertWarning("UnreadPrivateField")
    public void setF(int f) {
        this.f = f;
    }
    
    public class SubClass extends TestFieldAccess {
        public int getE() {
            return e;
        }

        public void setE(int e) {
            this.e = e;
        }
    }
    
    @AssertNoWarning("*")
    public static class TestGeneric<T> {
        private T[] data;

        public T[] getData() {
            return data;
        }

        public void setData(T[] data) {
            this.data = data;
        }
    }
    
    private double x;
    private double y;
    private double z;

    @AssertWarning("FieldUsedInSingleMethod")
    public void test() {
        x = Math.random();
        if(x > 0.5) {
            System.out.println("Big!");
        }
    }
    
    @AssertNoWarning("*")
    public void testOk() {
        if(z > 0.5) {
            System.out.println("Big!");
        }
        z = Math.random();
    }
    
    @AssertNoWarning("*")
    public void testOk(TestFieldAccess tfa) {
        if(tfa.y > 0) {
            this.y = tfa.y;
            System.out.println("Big!");
        }
    }
    
    @AssertWarning("UnwrittenPublicField")
    public long unwritten;
    
    @AssertWarning("UnwrittenPublicField")
    public long getUnwritten() {
        return unwritten;
    }
    
    @AssertNoWarning("*")
    public enum MyEnum {
        A, B, C;
    }
}
