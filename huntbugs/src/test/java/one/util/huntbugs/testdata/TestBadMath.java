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

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestBadMath {
    @AssertWarning(type="RemOne")
    public int testRem(int x) {
        int mod = 1;
        Integer add = 0;
        if (x == 2)
            mod += add;
        return x % mod;
    }

    @AssertWarning(type="RemOne")
    public int testRemAbs(int x) {
        int mod = Math.abs(-1);
        return x % mod;
    }
    
    @AssertNoWarning(type="RemOne")
    public int testRemOk(int x) {
        int mod = 1;
        if (x == 2)
            mod = 2;
        return x % mod;
    }

    @AssertWarning(type="UselessOrWithZero")
    public int testOrZero(int x) {
        int arg = 0;
        return x | arg;
    }
    
    @AssertWarning(type="UselessOrWithZero")
    public int testXorZero(int x) {
        int arg = 0;
        return x ^ arg;
    }
    
    @AssertWarning(type="UselessAndWithMinusOne")
    public int testAndFFFF(int x) {
        return x & 0xFFFFFFFF;
    }
    
    @AssertNoWarning(type="UselessAndWithMinusOne")
    public long testAndFFFF(long x) {
        return x & 0xFFFFFFFFL;
    }
    
    @AssertWarning(type="UselessAndWithMinusOne")
    public long testAndFFFFIncorrect(long x) {
        return x & 0xFFFFFFFF;
    }
    
    @AssertWarning(type="UselessAndWithMinusOne")
    public long testAndFFFFConvert(long x) {
        int mask = 0xFFFFFFFF;
        return x & mask;
    }
    
    @AssertNoWarning(type="Useless*")
    public boolean testBoolean() {
        boolean x = false, z = false, y = true;
        x |= y;
        z = y | z;
        return x ^ y ^ z;
    }
    
    @AssertNoWarning(type="Useless*")
    public boolean testCompound() {
        int x = 0;
        int y = 0xFFFFFFFF;
        x |= 0x100;
        x |= 0x400;
        y &= ~1;
        return (x ^ y) > 0;
    }
    
    @AssertWarning(type="CompareBitAndIncompatible")
    public void testIncompatibleAnd(int x) {
        if((x & 1) == 2) {
            System.out.println();
        }
    }
    
    @AssertWarning(type="CompareBitAndIncompatible")
    public void testIncompatibleAnd2(int x) {
        if((x & 1) == 3) {
            System.out.println();
        }
    }
    
    @AssertNoWarning(type="CompareBitAndIncompatible")
    public void testCompatibleAnd(int x) {
        if((x & 3) == 2) {
            System.out.println();
        }
    }

    @AssertWarning(type="CompareBitOrIncompatible")
    public void testIncompatibleOr(int x) {
        if((x | 1) == 2) {
            System.out.println();
        }
    }
    
    @AssertWarning(type="CompareBitOrIncompatible")
    public void testIncompatibleOr2(int x) {
        if((x | 3) == 2) {
            System.out.println();
        }
    }
    
    @AssertNoWarning(type="CompareBitOrIncompatible")
    public void testCompatibleOr(int x) {
        if((x | 1) == 3) {
            System.out.println();
        }
    }
    
    @AssertWarning(type="CompareBitOrIncompatible")
    public void testInCompatibleOrObscure(int x) {
        int mask = 0xFF;
        int subMask = mask & 0x20;
        if((x | mask) == subMask) {
            System.out.println();
        }
    }

    @AssertWarning(type="UselessAndWithMinusOne")
    public int testUselessAnd(long input) {
        for(int i=0; i<input; i++) input--;
        return (int)(input & 0xFFFFFFFF);
    }
    
}
