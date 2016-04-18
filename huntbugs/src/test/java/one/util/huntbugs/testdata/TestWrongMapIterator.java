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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestWrongMapIterator {
    @AssertWarning(type="WrongMapIterator")
    public void test(Map<String, String> m) {
        Iterator<String> it = m.keySet().iterator();
        while (it.hasNext()) {
            String name = it.next();
            String value = m.get(name);
            System.out.println(name + " = " + value);
        }
    }

    @AssertWarning(type="WrongMapIterator")
    public void test2(int a, int b, int c, int d, Map<String, String> m) {
        Iterator<String> it = m.keySet().iterator();
        while (it.hasNext()) {
            String name = it.next();
            String value = m.get(name);
            System.out.println(name + " = " + value);
        }
    }

    @AssertWarning(type="WrongMapIterator")
    public void test3(Map<String, String> m) {
        Set<String> s = m.keySet();
        Iterator<String> it = s.iterator();
        while (it.hasNext()) {
            String name = it.next();
            String value = m.get(name);
            System.out.println(name + " = " + value);
        }
    }

    @AssertWarning(type="WrongMapIterator")
    public void testFor(Map<String, String> m) {
        for(String name : m.keySet()) {
            String value = m.get(name);
            System.out.println(name + " = " + value);
        }
    }
    
    @AssertWarning(type="WrongMapIterator")
    public void testForBoxing(Map<Integer, String> m) {
        for(int key : m.keySet()) {
            String value = m.get(key);
            System.out.println(key + " = " + value);
        }
    }
    
    @AssertWarning(type="WrongMapIterator")
    public void test4(Map<String, String> m) {
        Iterator<String> it = m.keySet().iterator();
        while (it.hasNext()) {
            Object name = it.next();
            String value = m.get(name);
            System.out.println(name.toString() + " = " + value);
        }
    }

    @AssertNoWarning(type="WrongMapIterator")
    public void testIteratorLoadBug(Map<String, String> m1, List<String> list) {
        Set<String> keys = m1.keySet();
        //int a = 0;
        for(String str : list) {
            System.out.println(m1.get(str));
        }
    }

    @AssertNoWarning(type="WrongMapIterator")
    public void testRegisterReuseBug(Map<String, String> m) {
        for(String key : m.keySet()) {
            System.out.println(key);
        }
        String k = "special";
        System.out.println(m.get(k));
    }

    @AssertNoWarning(type="WrongMapIterator")
    public void testEnumMap(EnumMap<TimeUnit, String> m) {
        for(TimeUnit key : m.keySet()) {
            System.out.println(m.get(key));
        }
    }

    @AssertNoWarning(type="WrongMapIterator")
    public void testRegisterReuseBug2(Map<Integer, String> m) {
        int maxKey = 0;
        for(Integer key : m.keySet()) {
            if(key > maxKey)
                maxKey = key;
        }
        for(Integer i=0; i<maxKey; i++) {
            String val = m.get(i);
            System.out.println(i+": "+(val == null ? "none" : val));
        }
    }

    @AssertWarning(type="WrongMapIterator")
    public void testTwice(Map<String, String> m1, Map<String, String> m2) {
        for(String m1key : m1.keySet()) {
            System.out.println(m1key);
        }
        for(String m2key : m2.keySet()) {
            System.out.println(m2key+"="+m2.get(m2key));
        }
    }

    @AssertWarning(type="WrongMapIterator")
    public void testSingleElement(Map<String, String> m) {
        String key = m.keySet().iterator().next();
        String value = m.get(key);
        System.out.println(key+"="+value);
    }

    @AssertWarning(type="WrongMapIterator")
    public void testBoxing(Map<Integer, String> m) {
        for(int key : m.keySet()) {
            System.out.println(key+": "+m.get(key));
        }
    }

    private final Map<Object, String> fieldMap = new HashMap<>();

    @AssertWarning(type="WrongMapIterator")
    public void testField() {
        Iterator<Object> it = fieldMap.keySet().iterator();
        while (it.hasNext()) {
            Object name = it.next();
            String value = fieldMap.get(name);
            System.out.println(name.toString() + " = " + value);
        }
    }

    private static Map<Object, String> staticMap = new HashMap<>();

    @AssertWarning(type="WrongMapIterator")
    public void testStatic() {
        for(Object name : staticMap.keySet()) {
            String value = staticMap.get(name);
            System.out.println(name.toString() + " = " + value);
        }
    }

    private final Map<Object, String> fieldMap2 = new HashMap<>();
    @AssertNoWarning(type="WrongMapIterator")
    public void testWrongMap() {
        Iterator<Object> it = fieldMap.keySet().iterator();
        while (it.hasNext()) {
            Object name = it.next();
            String value = fieldMap2.get(name);
            System.out.println(name.toString() + " = " + value);
        }
    }

    public static class DebugHashMap extends HashMap<Object, String> {
        @AssertWarning(type="WrongMapIterator")
        public void dump() {
            Iterator<Object> it = keySet().iterator();
            while (it.hasNext()) {
                Object name = it.next();
                String value = get(name);
                System.out.println(name.toString() + " = " + value);
            }
        }
    }
}
