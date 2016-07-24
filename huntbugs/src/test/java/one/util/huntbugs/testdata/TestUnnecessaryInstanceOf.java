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
import java.util.Iterator;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestUnnecessaryInstanceOf {
    Object f = Math.random() > 0.5 ? (Number) 1 : (Number) 1.0;

    @AssertWarning("UnnecessaryInstanceOf")
    void testInferred(int x) {
        Object a = 1.0;
        if (x > 2)
            a = -2;
        if (a instanceof Number) {
            System.out.println(a);
        }
    }

    @AssertWarning("UnnecessaryInstanceOf")
    void testField() {
        if (f instanceof Number) {
            System.out.println(f);
        }
    }
    
    @AssertWarning("ClassComparisonFalse")
    void testClassComparisonSimple(String s) {
        Object obj = s;
        if(obj.getClass() == Integer.class) {
            System.out.println("Never");
        }
    }

    @AssertWarning("ClassComparisonFalse")
    void testClassComparisonMixed(String a, Number b) {
        if(b instanceof Float) {
            Object x = Math.random() > 0.5 ? a : b;
            if(x.getClass() == Long.class) {
                System.out.println("Never");
            }
        }
    }
    
    @AssertWarning("ClassComparisonFalse")
    void testClassComparisonComplex(String a, Number b) {
        Object x = 1.0;
        if(b instanceof Float) {
            x = Math.random() > 0.5 ? a : b;
        }
        if(x.getClass() == Long.class) {
            System.out.println("Never");
        }
    }
    
    @AssertWarning("UnnecessaryInstanceOf")
    void testSimple() {
        String a = "test";
        if (a instanceof CharSequence) {
            System.out.println(a);
        }
    }

    @AssertWarning("ImpossibleCast")
    int testComplex(Object obj) {
        if(obj instanceof Integer)
            return 2;
        if(obj instanceof String)
            return 1;
        if(obj instanceof CharSequence)
            return 3;
        return Integer.valueOf((String)obj);
    }

    @AssertWarning("ImpossibleCast")
    void testCast() {
        Object a = "test";
        System.out.println((Integer) a);
    }

    @AssertWarning("ImpossibleInstanceOf")
    void testArray(String[] data) {
        Object[] arr = data;
        if (arr instanceof Integer[]) {
            System.out.println("Never");
        }
    }

    @AssertNoWarning("*")
    int testPrimArray(Object data) {
        if (data instanceof Object[])
            return 1;
        if (data instanceof int[])
            return 2;
        return 0;
    }

    @AssertWarning("UnnecessaryInstanceOf")
    void testArrayOk(String[] data) {
        Object[] arr = data;
        if (arr instanceof CharSequence[]) {
            System.out.println("Always");
        }
    }

    @AssertWarning("UnnecessaryInstanceOf")
    void testConditional(Object obj) {
        if (!(obj instanceof String))
            return;
        if (obj instanceof CharSequence) {
            System.out.println("Always");
        }
    }

    @AssertWarning("ImpossibleInstanceOf")
    void testCCE(Object obj) {
        CharSequence s;
        try {
            s = (CharSequence) obj;
        } catch (ClassCastException cce) {
            if (obj instanceof String) {
                System.out.println("Never!");
            }
            return;
        }
        System.out.println(s);
    }

    @AssertWarning("ImpossibleInstanceOf")
    void testConditionalImpossible(Object obj) {
        if (!(obj instanceof String))
            return;
        if (obj instanceof Number) {
            System.out.println("Never");
        }
    }

    @AssertNoWarning("*")
    void testInterfaceNonFinal(ArrayList<String> al) {
        Object obj = al;
        if (obj instanceof Comparable) {
            System.out.println("Yes!");
        }
    }

    @AssertNoWarning("*")
    void testInterfaceNonFinal2(Comparable<?> cmp) {
        Object obj = cmp;
        if (obj instanceof ArrayList) {
            System.out.println("Yes!");
        }
    }

    @AssertWarning("ImpossibleInstanceOf")
    void testInterfaceFinal(StringBuilder sb) {
        Object obj = sb;
        if (obj instanceof Comparable) {
            System.out.println("Yes!");
        }
    }

    @AssertNoWarning("*")
    void testTypeMerging(Object obj, String type) {
        if (type.equals("String")) {
            String val = (String) obj;
            System.out.println("String: " + val);
        }
        if (type.equals("Int")) {
            Integer val = (Integer) obj;
            System.out.println("Int: " + val);
        }
    }

    String[] stringArr() {
        return new String[] { "test" };
    }

    int[] intArr() {
        return new int[] { 1 };
    }
    
    @AssertWarning("UnnecessaryInstanceOf")
    void testRetValue1() {
        Object x = stringArr();
        if(x instanceof String[]) {
            System.out.println("Ok");
        }
    }
    
    @AssertWarning("UnnecessaryInstanceOf")
    void testRetValue2() {
        Object x = intArr();
        if(x instanceof int[]) {
            System.out.println("Ok");
        }
    }
    
    @AssertWarning("ImpossibleInstanceOf")
    void testGetClass(Object obj) {
        if(obj.getClass() == ArrayList.class) {
            if(obj instanceof Comparable) {
                System.out.println("Never");
            }
        }
    }
    
    @AssertWarning("ImpossibleInstanceOf")
    void testGetClass2(Object obj) {
        if(ArrayList.class.equals(obj.getClass())) {
            if(obj instanceof Comparable) {
                System.out.println("Never");
            }
        }
    }
    
    @AssertWarning("UnnecessaryInstanceOf")
    void testIsInstance(Object obj) {
        Class<?> clazz = String.class;
        if(clazz.isInstance(obj)) {
            if(obj instanceof CharSequence) {
                System.out.println("Always");
            }
        }
    }
    
    Object convert(String s) {
        return Integer.valueOf(s);
    }
    
    @AssertNoWarning("*")
    void testTwoOptions(Object obj) {
        if(obj == null) {
            obj = "1";
        }
        if(obj instanceof String) {
            String s = (String)obj;
            System.out.println(s);
            obj = convert(s);
        }
        Integer i = (Integer)obj;
        System.out.println(i);
    }
    
    public class MyList<E extends CharSequence> extends ArrayList<E> {
        private static final long serialVersionUID = 1L;
        
        @AssertNoWarning("*")
        public boolean hasString() {
            for(Iterator<E> itr = iterator(); itr.hasNext();) {
                CharSequence cs = itr.next();
                if(cs instanceof String) {
                    return true;
                }
            }
            return false;
        }
    }
}
