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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

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

    @AssertNoWarning("*")
    private long updated;
    
    private static final AtomicLongFieldUpdater<TestFieldAccess> alfu = AtomicLongFieldUpdater.newUpdater(TestFieldAccess.class, "updated");
    
    public long inc() {
        return alfu.incrementAndGet(this);
    }
    
    @AssertNoWarning("*")
    private Object refUpdated;
    
    private static final AtomicReferenceFieldUpdater<TestFieldAccess, Object> arfu = AtomicReferenceFieldUpdater
            .newUpdater(TestFieldAccess.class, Object.class, "refUpdated");
    
    public boolean cas(Object expected, Object updated) {
        return arfu.compareAndSet(this, expected, updated);
    }
    
    @AssertNoWarning("*")
    private long reflected;
    
    public void updateReflected() throws Exception {
        Class<TestFieldAccess> clazz = TestFieldAccess.class;
        Field field = clazz.getDeclaredField("reflected");
        field.setAccessible(true);
        field.set(this, ((long)field.get(this))+1);
    }
    
    @AssertNoWarning("*")
    private long mh;

    public void updateMH() throws Throwable {
        MethodHandle getter = MethodHandles.lookup().findGetter(TestFieldAccess.class, "mh", long.class);
        MethodHandle setter = MethodHandles.lookup().findSetter(TestFieldAccess.class, "mh", long.class);
        setter.invokeExact(this, (((long)getter.invokeExact(this))+1));
    }

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

    public enum MyEnum2 {
        A, B, C;
        
        @AssertWarning("StaticFieldShouldBeFinal")
        public static String STATIC = "";
        
        @AssertWarning("MutableEnumField")
        public String field;
        
        public final List<String> list = Arrays.asList("a", "b", "c");
        
        public void agg(String s) {
            field += s;
        }
        
        public String get(int i) {
            return list.get(i);
        }
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
    
    @AssertNoWarning("*")
    private static int exposeOk = 1;

    @AssertNoWarning("*")
    public static int getExposeOk() {
        exposeOk++;
        System.out.print(exposeOk);
        return exposeOk;
    }
    
    @AssertWarning(value="ExposeMutableFieldViaReturnValue", maxScore=25)
    private int[] expose = {1};
    
    @AssertWarning("ExposeMutableFieldViaReturnValue")
    public int[] getExpose() {
        expose[0]++;
        System.out.print(expose[0]);
        return expose;
    }
    
    public void setExpose(int[] expose) {
        this.expose = expose;
    }
    
    @AssertNoWarning("*")
    private int[] expose2 = {1};
    
    @AssertNoWarning("*")
    public int[] getExpose2() {
        int[] data = expose2;
        expose2 = new int[] {3};
        return data;
    }
    
    public void updateExpose2() {
        expose2 = new int[] {2};
    }
    
    @AssertNoWarning("*")
    public static class Reflected {
        long x = 0;
        long y = 0;
        long z = 0;
        
        public void set(String name) throws Exception {
            Reflected.class.getDeclaredField(name).set(this, 1L);
        }
    }
}
