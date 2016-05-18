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

import java.util.Arrays;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestArrayRangeCheck {
    @AssertWarning(type="ArrayIndexNegative")
    public int get(int[] arr) {
        int idx = -1;
        return arr[idx];
    }

    @AssertWarning(type="ArrayIndexOutOfRange")
    public void test() {
        int[] arr = {1,2,3};
        arr[3] = 4;
        System.out.println(Arrays.toString(arr));
    }

    @AssertWarning(type="ArrayIndexOutOfRange")
    public void testArrayBoolean(boolean b) {
        int[] arr = {1,2,3};
        if(b)
            arr = new int[4];
        arr[4] = 4;
        System.out.println(Arrays.toString(arr));
    }

    @AssertWarning(type="ArrayIndexOutOfRange")
    public int[] getValueLocal(boolean flag) {
        int[] array = new int[5];
        if(flag) {
            array = new int[10];
            array[6] = 1;
            return array;
        } else {
            array[6] = 2;
            return array;
        }
    }

    @AssertWarning(type="ArrayIndexOutOfRange")
    public void testArrayClone(boolean b) {
        int[] arr = {1,2,3};
        int[] barr = arr.clone();
        barr[4] = 4;
        System.out.println(Arrays.toString(barr));
    }
    
    @AssertNoWarning(type="*")
    public void testOk() {
        int[] arr = {1,2,3};
        arr[2] = 4;
        System.out.println(Arrays.toString(arr));
    }
    
    @AssertNoWarning(type="*")
    public void testArrayCopyOk() {
        int[] a = new int[4];
        int[] b = new int[5];
        System.arraycopy(a, 0, b, 1, 4);
        System.out.println(Arrays.toString(b));
    }
    
    @AssertWarning(type="ArrayLengthOutOfRange")
    public void testArrayCopy() {
        int[] a = new int[4];
        int[] b = new int[5];
        System.arraycopy(a, 1, b, 0, 4);
        System.out.println(Arrays.toString(b));
    }
    
    @AssertWarning(type="ArrayLengthOutOfRange")
    public void testArrayCopyLength() {
        int[] a = new int[4];
        int[] b = new int[5];
        System.arraycopy(a, 1, b, 0, a.length);
        System.out.println(Arrays.toString(b));
    }
    
    @AssertWarning(type="ArrayOffsetOutOfRange")
    public void testArrayCopyIdx() {
        int[] a = new int[4];
        int[] b = new int[5];
        System.arraycopy(a, 5, b, 0, 4);
        System.out.println(Arrays.toString(b));
    }
    
    @AssertWarning(type="ArrayIndexNegative")
    public void testArrayCopyNegative() {
        int[] a = new int[4];
        int[] b = new int[5];
        System.arraycopy(a, 0, b, -1, 1);
        System.out.println(Arrays.toString(b));
    }

    @AssertNoWarning(type="Array*")
    public void testArrayCopyCatch() {
        int[] a = new int[4];
        int[] b = new int[5];
        try {
            a[-1] = 2;
            b[10] = 3;
            System.arraycopy(a, -1, b, -1, 1000);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        System.out.println(Arrays.toString(b));
    }
    
}
