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

import java.util.ArrayList;

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestNumericComparison {
    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testChar(char c) {
        if(c < 0) {
            System.out.println("Never!");
        }
        if(c >= 0) {
            System.out.println("Always");
        }
    }

    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testByte(Byte b) {
        int i = b.byteValue();
        if(i < 0x80) {
            System.out.println("Always");
        }
    }

    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testInt(int c) {
        if(c < 0x100000000000L) {
            System.out.println("Always!");
        }
    }
    
    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testArrayLength(int[] array) {
        if(array.length < 0) {
            System.out.println("Never!");
        }
    }

    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testListLength(ArrayList<String> list) {
        if(list.size() == -1) {
            System.out.println("Never!");
        }
    }

    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testBitOp(int input) {
        int result = input & 0xFF0;
        if(result > 0xFFFF) {
            System.out.println("Never!");
        }
    }
}
