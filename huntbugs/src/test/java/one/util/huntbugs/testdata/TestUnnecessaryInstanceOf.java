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

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestUnnecessaryInstanceOf {
    Object f = Math.random() > 0.5 ? (Number)1 : (Number)1.0;
    
    @AssertWarning("UnnecessaryInstanceOf")
    void testInferred(int x) {
        Object a = 1.0;
        if(x > 2) a = -2;
        if(a instanceof Number) {
            System.out.println(a);
        }
    }

    @AssertWarning("UnnecessaryInstanceOf")
    void testField() {
        if(f instanceof Number) {
            System.out.println(f);
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
    void testCast() {
        Object a = "test";
        System.out.println((Integer)a);
    }
    
    @AssertWarning("ImpossibleInstanceOf")
    void testArray(String[] data) {
        Object[] arr = data;
        if(arr instanceof Integer[]) {
            System.out.println("Never");
        }
    }
    
    @AssertNoWarning("*")
    int testPrimArray(Object data) {
        if(data instanceof Object[])
            return 1;
        if(data instanceof int[])
            return 2;
        return 0;
    }
    
    @AssertWarning("UnnecessaryInstanceOf")
    void testArrayOk(String[] data) {
        Object[] arr = data;
        if(arr instanceof CharSequence[]) {
            System.out.println("Always");
        }
    }
    
    @AssertWarning("UnnecessaryInstanceOf")
    void testConditional(Object obj) {
        if(!(obj instanceof String))
            return;
        if(obj instanceof CharSequence) {
            System.out.println("Always");
        }
    }
    
    @AssertWarning("ImpossibleInstanceOf")
    void testCCE(Object obj) {
        CharSequence s;
        try {
            s = (CharSequence)obj;
        }
        catch(ClassCastException cce) {
            if(obj instanceof String) {
                System.out.println("Never!");
            }
            return;
        }
        System.out.println(s);
    }
    
    @AssertWarning("ImpossibleInstanceOf")
    void testConditionalImpossible(Object obj) {
        if(!(obj instanceof String))
            return;
        if(obj instanceof Number) {
            System.out.println("Never");
        }
    }
    
    @AssertNoWarning("*")
    void testInterfaceNonFinal(ArrayList<String> al) {
        Object obj = al;
        if(obj instanceof Comparable) {
            System.out.println("Yes!");
        }
    }

    @AssertNoWarning("*")
    void testInterfaceNonFinal2(Comparable<?> cmp) {
        Object obj = cmp;
        if(obj instanceof ArrayList) {
            System.out.println("Yes!");
        }
    }
    
    @AssertWarning("ImpossibleInstanceOf")
    void testInterfaceFinal(StringBuilder sb) {
        Object obj = sb;
        if(obj instanceof Comparable) {
            System.out.println("Yes!");
        }
    }
    
    @AssertNoWarning("*")
    void testTypeMerging(Object obj, String type) {
        if(type.equals("String")) {
            String val = (String)obj;
            System.out.println("String: "+val);
        }
        if(type.equals("Int")) {
            Integer val = (Integer)obj;
            System.out.println("Int: "+val);
        }
    }
}
