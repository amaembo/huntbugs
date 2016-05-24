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
 * @author Tagir Valeev
 *
 */
public class TestSelfAssignment {
    Object f;
    static Object st;
    
    @AssertWarning(type="SelfAssignmentField")
    void test() {
        Object a = f;
        f = a;
    }
    
    @AssertWarning(type="SelfAssignmentField")
    void testArray() {
        TestSelfAssignment[] arr = {new TestSelfAssignment()};
        arr[0].f = arr[0].f;
    }
    
    @AssertWarning(type="SelfAssignmentField")
    void testArray(int a) {
        TestSelfAssignment[] arr = {new TestSelfAssignment()};
        arr[a+1].f = arr[1+a].f;
    }
    
    @AssertNoWarning(type="SelfAssignmentField")
    void testArrayOk() {
        TestSelfAssignment[] arr = {new TestSelfAssignment(), new TestSelfAssignment()};
        arr[0].f = arr[1].f;
    }

    @AssertNoWarning(type="*")
    void testArrayOk2(int i, int[] arr) {
        arr[i] = arr[++i];
    }
    
    @AssertNoWarning(type="SelfAssignmentField")
    void testArraySideEffect() {
        TestSelfAssignment[] arr = {new TestSelfAssignment(), new TestSelfAssignment()};
        int i=0;
        arr[i++].f = arr[i++].f;
    }
    
    @AssertWarning(type="SelfAssignmentField")
    void testThis() {
        TestSelfAssignment copy = this;
        Object a = f;
        copy.f = a;
    }
    
    @AssertNoWarning(type="SelfAssignmentField")
    void testOk() {
        f = new TestSelfAssignment().f;
    }

    @AssertWarning(type="SelfAssignmentField")
    void testStatic() {
        Object a = st;
        st = a;
    }
    
    @AssertNoWarning(type="SelfAssignmentField")
    void testSideEffectStatic() {
        Object a = st;
        st = null;
        System.out.println(st);
        st = a;
    }
    
    @AssertNoWarning(type="SelfAssignmentField")
    void testSideEffect() {
        Object a = f;
        f = null;
        System.out.println(f);
        f = a;
    }

    @AssertNoWarning(type="SelfAssignmentField")
    void testTwoObjects() {
        TestSelfAssignment t1 = new TestSelfAssignment();
        TestSelfAssignment t2 = new TestSelfAssignment();
        t1.f = t2.f;
    }

    @AssertWarning(type="SelfAssignmentArrayElement")
    void testArraySelf(int[] a, int idx) {
        a[idx] = a[idx];
    }
    
    @AssertNoWarning(type="SelfAssignmentArrayElement")
    void testArraySelfDiffArray(int[] a, int[] b, int idx) {
        a[idx] = b[idx];
    }
    
    @AssertWarning(type="SelfAssignmentArrayElement")
    void testArraySelfCopyArray(int[] a, int[] b, int idx) {
        a = b;
        a[idx] = b[idx];
    }
    
    @AssertWarning(type="SelfAssignmentArrayElement")
    void testArraySelfVar(int[] a, int idx) {
        int x = a[idx];
        a[idx] = x;
    }
    
    @AssertNoWarning(type="SelfAssignmentArrayElement")
    void testArraySelfChanged(int[] a, int idx) {
        int x = a[idx];
        a[idx] = 1;
        a[idx] = x;
    }
    
    @AssertNoWarning(type="SelfAssignmentArrayElement")
    void testArraySelfSideEffect(int[] a, int idx) {
        int x = a[idx];
        update(a);
        a[idx] = x;
    }
    
    private void update(int[] arr) {
        arr[0] = 2;
    }
    
    @AssertNoWarning(type="SelfAssignmentArrayElement")
    void testArraySelfDiffIndex(int[] a, int idx) {
        a[idx++] = a[idx++];
    }
    
    @AssertNoWarning(type="SelfAssignmentArrayElement")
    void testNew() {
        int[] a = new int[10];
        int[] b = new int[10];
        a[0] = b[0];
    }

    @SuppressWarnings("cast")
    @AssertWarning(type="SelfAssignmentLocal")
    @AssertNoWarning(type="SelfAssignmentLocalInsteadOfField")
    void testLocal(int a) {
        a = (int)a; // cast to prevent error in Eclipse
    }
    
    @SuppressWarnings("cast")
    @AssertWarning(type="SelfAssignmentLocalInsteadOfField")
    @AssertNoWarning(type="SelfAssignmentLocal")
    void testLocal(Object f) {
        f = (Object)f; // cast to prevent error in Eclipse
    }

    public static class HeapNode {
        public HeapNode[] myChildren;
        public String myEdge;
    }

    @AssertNoWarning(type="*")
    void heapify(HeapNode node) {
        while (true) {
            HeapNode min = node;
            for (int i = 0; i < 2; i++) {
                HeapNode child = node.myChildren[i];
                if (child != null && child.myEdge.length() < min.myEdge.length()) {
                    min = child;
                }
            }
            if (min != node) {
                String t = min.myEdge;
                min.myEdge = node.myEdge;
                node.myEdge = t;
                node = min;
            } else {
                break;
            }
        }
    }
    
    @AssertNoWarning(type="*")
    void testWithCatch() {
        Object oldVal = f;
        try {
            test();
        }
        catch(Exception exc) {
            f = oldVal;
        }
    }
}
