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
import java.util.List;
import java.util.Random;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestNumericComparison {
    @AssertNoWarning(type="*")
    public void testByte(byte b) {
        if(b + 1 > Byte.MAX_VALUE) {
            System.out.println("possible?");
        }
    }
    
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
    public void testShort(short s) {
        if(s == 0xFEF0) {
            System.out.println("Never!");
        }
    }
    
    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testInt(boolean b) {
        int x = b ? 1 : 2;
        if(x > 2) {
            System.out.println("Never!");
        }
    }
    
    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testIntPhi(boolean b) {
        int x = 1;
        if (b)
            x = 2;
        if(x > 2) {
            System.out.println("Never!");
        }
    }
    
    @AssertNoWarning(type="ComparisonWithOutOfRangeValue")
    public void testCharOk(char c) {
        int r = c - 'a';
        if(r < 0) {
            System.out.println("Ok!");
        }
    }

    @AssertNoWarning(type="*")
    public void testAssert(char c) {
        assert c >= 0;
        if(c == 'x') {
            System.out.println("X!");
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

    @AssertWarning(type="SwitchBranchUnreachable")
    public int testArrayLengthSwitch(int[] array) {
        switch(array.length) {
        case 0:
            return -1;
        case 1:
            return 0;
        case 2:
            return 10;
        case Integer.MAX_VALUE:
            return 12;
        case -1:
            return Integer.MIN_VALUE;
        default:
            return -2;
        }
    }

    @AssertNoWarning(type="SwitchBranchUnreachable")
    public int testArrayLengthSwitchOk(int[] array) {
        switch(array.length) {
        case 0:
            return -1;
        case 1:
            return 0;
        case 2:
            return 10;
        case Integer.MAX_VALUE:
            return 12;
        default:
            return -2;
        }
    }

    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testListLength(ArrayList<String> list) {
        if(list.size() == -1) {
            System.out.println("Never!");
        }
    }

    @AssertNoWarning(type="ComparisonWithOutOfRangeValue")
    public void testBitOpOk(int input) {
        int result = input & 0x1FFF0;
        if(result > 0xFFFF) {
            System.out.println("Never!");
        }
    }

    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testBitOp(int input) {
        int result = 0xFF0 & input;
        if(result > 0xFFFF) {
            System.out.println("Never!");
        }
    }
    
    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testBitOpSelf(int input) {
        input &= 0xFF0;
        if(input > 0xFFFF) {
            System.out.println("Never!");
        }
    }
    
    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testBitOpPhi(int input, boolean b) {
        int mask = b ? 0xFF : 0x1F0;
        int result = mask & input;
        if(result > 0x1FF) {
            System.out.println("Never!");
        }
    }

    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testBitOpPhi2(int input, boolean b) {
        int mask = b ? 0xFF : 0x1F0;
        int result = mask & input;
        if(result < 0) {
            System.out.println("Never!");
        }
    }

    @AssertNoWarning(type="ComparisonWithOutOfRangeValue")
    public void testBitOpPhiOk(int input, boolean b) {
        int mask = b ? 0xF0 : 0x1FF;
        int result = mask & input;
        if(result >= 0x1FF) {
            System.out.println("Never!");
        }
    }
    
    @AssertNoWarning(type="ComparisonWithOutOfRangeValue")
    public void testBitOpPhiOk2(int input, boolean b) {
        int mask = b ? 0xF0 : -1;
        int result = mask & input;
        if(result == Integer.MIN_VALUE) {
            System.out.println("Never!");
        }
    }
    
    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testRem(int input) {
        int result = input % 3;
        if(result == 3) {
            System.out.println("Never!");
        }
    }

    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testRem(List<String> list) {
        int result = list.size() % 3;
        if(result < 0) {
            System.out.println("Never!");
        }
    }

    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testRemPhi(List<String> list, boolean b) {
        int x = b ? 3 : 5;
        int result = list.size() % x;
        if(result > 5) {
            System.out.println("Never!");
        }
    }
    
    @AssertNoWarning(type="ComparisonWithOutOfRangeValue")
    public void testShrOk(int input) {
        int result = input >> 10;
        if(result == 0x1FFFFF || result == -0x200000) {
            System.out.println("Ok");
        }
    }
    
    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testShr(int input) {
        int result = input >> 10;
        if(result == 0x200000) {
            System.out.println("Never");
        }
    }
    
    @AssertNoWarning(type="ComparisonWithOutOfRangeValue")
    public void testUShrOk(int input) {
        int result = input >>> 10;
        if(result == 0x3FFFFF) {
            System.out.println("Ok");
        }
    }
    
    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void testUShr(int input) {
        int result = input >>> 10;
        if(result == 0x400000) {
            System.out.println("Never");
        }
    }

    @AssertWarning(type="CheckForOddnessFailsForNegative")
    public void testRem2(int input) {
        if(input % 2 == 1) {
            System.out.println("odd");
        }
    }

    @AssertNoWarning(type="CheckForOddnessFailsForNegative")
    public void testRem2Ok(List<String> list) {
        if(list.size() % 2 == 1) {
            System.out.println("odd");
        }
    }

    @AssertNoWarning(type="*")
    public int countMembers(int[] bits) {
        int count = 0;
        for (int i = 0; i < bits.length; i++) {
            int x = bits[i];
            while (x != 0) {
                count++;
                x &= (x - 1);
            }
        }
        return count;
    }
    
    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void checkRandom(Random r) {
        if(r.nextInt(10) > 15) {
            System.out.println("Never");
        }
    }
    
    @AssertWarning(type="ComparisonWithOutOfRangeValue")
    public void checkWithCatch(String s) {
        int x;
        try {
            x = Byte.parseByte(s);
        }
        catch(NumberFormatException ex) {
            x = 130;
        }
        if(x > 140) {
            System.out.println("Never!");
        }
    }
}
