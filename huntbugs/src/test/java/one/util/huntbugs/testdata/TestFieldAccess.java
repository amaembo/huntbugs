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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

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
    @AssertWarning(value="UnreadPrivateField", minScore=45)
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
    
    @AssertWarning(value="UnreadPrivateField", maxScore=40)
    private Object refField;
    
    public void setRef(Object val) {
        refField = val;
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
    static class TestGeneric<T> {
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
    
    @AssertWarning(value="UnwrittenPrivateField", maxScore=30)
    private transient long unwrittenTransient;
    
    @AssertWarning("UnwrittenPrivateField")
    public long getUnwrittenTransient() {
        return unwrittenTransient;
    }
    
    @AssertNoWarning("*")
    public enum MyEnum {
        A, B, C;
    }
    
    @AssertWarning("FieldIsAlwaysNull")
    private String s = null;
    
    public void reset() {
        s = null;
    }
    
    public String getS() {
        return s;
    }
    
    @AssertNoWarning("FieldIsAlwaysNull")
    private String s2 = Math.random() > 0.5 ? "test" : null;
    
    public void reset2() {
        s2 = null;
    }
    
    public String getS2() {
        return s2;
    }

    @AssertWarning("StaticFieldShouldBeFinal")
    public static double VALUE = Math.random(); 
    
    public static double getValue() {
        return VALUE;
    }
    
    @AssertWarning("StaticFieldShouldBeRefactoredToFinal")
    public static double VALUE_COMPLEX = Math.random();
    
    static {
        if(VALUE_COMPLEX < 0.5)
            VALUE_COMPLEX = Math.random();
    }
    
    public static double getValueComplex() {
        return VALUE_COMPLEX;
    }
    
    @AssertWarning(value = "StaticFieldShouldBePackagePrivate", maxScore=50)
    protected static double VALUE_NON_FINAL = Math.random();
    
    public static double getValueNonFinal() {
        return VALUE_NON_FINAL;
    }
    
    public static void recreateValueNonFinal() {
        VALUE_NON_FINAL = Math.random();
    }

    @AssertWarning("StaticFieldShouldBePackagePrivate")
    public static final Object data = new Integer[] {5,4,3};

    @AssertNoWarning("StaticField*")
    public static final Object empty = new Integer[] {};

    @AssertWarning("StaticFieldCannotBeFinal")
    public static Object usedEverywhere = "1";
    
    public static void recreate() {
        usedEverywhere = "2";
    }
    
    @AssertWarning("StaticFieldShouldBeFinalAndPackagePrivate")
    public static double[] ARRAY = {1.0, 2.0, 3.0};
    
    public double getArrayElement(int x) {
        return ARRAY[x];
    }
    
    public interface FieldInterface {
        @AssertNoWarning("*")
        public static int val = 10;

        @AssertWarning("StaticFieldShouldBeNonInterfacePackagePrivate")
        public static int[] arr = {1,2,3};

        @AssertWarning("StaticFieldMutableArray")
        public static int[] usedArr = {1,2,3};
        
        @AssertNoWarning("*")
        public static Collection<String> emptyStrings = Arrays.asList();

        @AssertWarning("StaticFieldMutableCollection")
        public static Collection<String> strings = Arrays.asList("1");

        @AssertWarning("StaticFieldMutableCollection")
        public static Collection<String> stringsList = new ArrayList<>();
    }
}
