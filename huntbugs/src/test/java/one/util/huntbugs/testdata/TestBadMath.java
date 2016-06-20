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
public class TestBadMath {
    private static final long FLAG_BAR = 0x100;
    private static final long FLAG_FOO = 0x8000_0000_0000_0000L;
    
    @AssertWarning("RemOne")
    public int testRem(int x) {
        int mod = 1;
        Integer add = 0;
        if (x == 2)
            mod += add;
        return x % mod;
    }

    @AssertWarning("RemOne")
    public int testRemSwitch(int x) {
        int mod = 1;
        switch(x) {
        case 1:
            System.out.println("Hehe");
            break;
        case 2:
            System.out.println("Haha");
            break;
        default:
            mod = 1;
            break;
        }
        return x % mod;
    }
    
    @AssertWarning("RemOne")
    public int testRemSwitchInside(int x) {
        int mod = 1;
        switch(x) {
        case 1:
            System.out.println("Hehe");
            mod = 1;
        case 2:
            System.out.println("Haha");
            return x % mod;
        default:
            mod = 2;
            break;
        }
        return x % mod;
    }
    
    @AssertNoWarning("RemOne")
    public int testRemSwitchOk(int x) {
        int mod = 1;
        switch(x) {
        case 1:
            System.out.println("Hehe");
            mod = 2;
            break;
        case 2:
            System.out.println("Haha");
            break;
        default:
            mod = 1;
            break;
        }
        return x % mod;
    }
    
    @AssertWarning("RemOne")
    public int testRemLoop() {
        int mod = 1;
        for(int i=0; i<10; i++) {
            if(i % mod > 0) {
                System.out.println("Hehe");
            }
        }
        return 0;
    }
    
    @AssertNoWarning("RemOne")
    public int testRemLoopOk() {
        int mod = 1;
        for (int i = 1; i < 10; i++) {
            if (mod % i > 0) {
                System.out.println("Hehe");
            }
        }
        return 0;
    }
    
    @AssertWarning("RemOne")
    public int testRemDoWhile() {
        int mod = 1;
        int i = 0;
        do {
            if(i % mod > 0) {
                System.out.println("Hehe");
            }
        } while(++i < 10);
        return 0;
    }
    
    @AssertNoWarning("RemOne")
    public int testRemDoWhileOk() {
        int mod = 1;
        int i = 1;
        do {
            if(mod % i > 0) {
                System.out.println("Hehe");
            }
        } while(++i < 10);
        return 0;
    }
    
    @AssertWarning("RemOne")
    public int testRemAbs(int x) {
        int mod = Math.abs(-1);
        return x % mod;
    }
    
    @AssertNoWarning("RemOne")
    public int testRemOk(int x) {
        int mod = 1;
        if (x == 2)
            mod = 2;
        return x % mod;
    }

    @AssertWarning("UselessOrWithZero")
    public int testOrZero(int x) {
        int arg = 0;
        return x | arg;
    }
    
    @AssertWarning("UselessOrWithZero")
    public int testXorZero(int x) {
        int arg = 0;
        return x ^ arg;
    }
    
    int flags;
    byte byteFlags;
    
    static final int NOTHING = 0;
    
    @AssertNoWarning("UselessOrWithZero")
    public TestBadMath(int x) {
        flags |= x;
    }
    
    @AssertNoWarning("*")
    public void setNothingField() {
        byteFlags |= NOTHING;
    }
    
    @AssertNoWarning("*")
    public void setNothingLocal() {
        byte f = byteFlags;
        f |= NOTHING;
        System.out.println(f);
    }
    
    static long sFlags, sFlags2;
    
    @AssertNoWarning("UselessOrWithZero")
    public static void setFlags(int x) {
        sFlags = 0;
        if(x > 5)
            sFlags = sFlags | x;
    }
    
    @AssertWarning("UselessOrWithZero")
    public static void setFlags2(int x) {
        sFlags = 0;
        if(x > 5)
            sFlags2 = sFlags | x;
    }
    
    @AssertWarning("UselessAndWithMinusOne")
    public int testAndFFFF(int x) {
        return x & 0xFFFFFFFF;
    }
    
    @AssertNoWarning("UselessAndWithMinusOne")
    public long testAndFFFF(long x) {
        return x & 0xFFFFFFFFL;
    }
    
    @AssertWarning("UselessAndWithMinusOne")
    public long testAndFFFFIncorrect(long x) {
        return x & 0xFFFFFFFF;
    }
    
    @AssertWarning("UselessAndWithMinusOne")
    public long testAndFFFFConvert(long x) {
        int mask = 0xFFFFFFFF;
        return x & mask;
    }
    
    @AssertNoWarning("Useless*")
    public boolean testBoolean() {
        boolean x = false, z = false, y = true;
        x |= y;
        z = y | z;
        return x ^ y ^ z;
    }
    
    @AssertNoWarning("Useless*")
    public boolean testCompound() {
        int x = 0;
        int y = 0xFFFFFFFF;
        x |= 0x100;
        x |= 0x400;
        y &= ~1;
        return (x ^ y) > 0;
    }
    
    @AssertWarning("CompareBitAndIncompatible")
    public void testIncompatibleAnd(int x) {
        if((x & 2) == 1) {
            System.out.println();
        }
    }
    
    @AssertWarning("CompareBitAndIncompatible")
    public void testIncompatibleAnd2(int x) {
        if((x & 2) == 3) {
            System.out.println();
        }
    }
    
    @AssertNoWarning("CompareBitAndIncompatible")
    public void testCompatibleAnd(int x) {
        if((x & 3) == 2) {
            System.out.println();
        }
    }

    @AssertWarning("CompareBitOrIncompatible")
    public void testIncompatibleOr(int x) {
        if((x | 1) == 2) {
            System.out.println();
        }
    }
    
    @AssertWarning("CompareBitOrIncompatible")
    public void testIncompatibleOr2(int x) {
        if((x | 3) == 2) {
            System.out.println();
        }
    }
    
    @AssertNoWarning("CompareBitOrIncompatible")
    public void testCompatibleOr(int x) {
        if((x | 1) == 3) {
            System.out.println();
        }
    }
    
    @AssertWarning("CompareBitOrIncompatible")
    public void testInCompatibleOrObscure(int x) {
        int mask = 0xFF;
        int subMask = mask & 0x20;
        if((x | mask) == subMask) {
            System.out.println();
        }
    }

    @AssertWarning("UselessAndWithMinusOne")
    public int testUselessAnd(long input) {
        for(int i=0; i<input; i++) input--;
        return (int)(input & 0xFFFFFFFF);
    }
    
    @AssertWarning("BitCheckGreaterNegative") 
    public boolean isFoo(long flags) {
        return (flags & FLAG_FOO) > 0;
    }

    @AssertWarning("BitCheckGreaterNegative") 
    public boolean isFooRev(long flags) {
        return 0 < (flags & FLAG_FOO);
    }
    
    @AssertNoWarning("*") 
    public boolean isFooStrangeButOk(long flags) {
        return (flags & FLAG_FOO) < 0;
    }
    
    @AssertNoWarning("BitCheckGreaterNegative") 
    @AssertWarning("BitCheckGreater") 
    public boolean isBar(long flags) {
        return (flags & FLAG_BAR) > 0;
    }
    
    @AssertNoWarning("*") 
    public boolean isFooOk(long flags) {
        return (flags & FLAG_FOO) != 0;
    }
    
    @AssertWarning("BitShiftInvalidAmount")
    public long shift(long input) {
        return input >> 64;
    }
    
    @AssertWarning("BitShiftInvalidAmount")
    public int shift(int input) {
        return input >>> 32;
    }
    
    @AssertWarning("BitShiftInvalidAmount")
    public int shiftLeft(int input) {
        int amount = -1;
        return input << amount;
    }
    
    @AssertNoWarning("BitShiftInvalidAmount")
    public long shiftOk(long input) {
        return input >> 32;
    }
    
    @AssertNoWarning("BitShiftInvalidAmount")
    public int shiftOk(int input) {
        return input >>> 31;
    }
    
    @AssertWarning("BitAddSignedByte")
    public int bitAdd(int src, byte add) {
        return (src << 8) + add;
    }

    @AssertWarning("BitAddSignedByte")
    public long bitAdd(long src, byte add) {
        return (src << 8) + add;
    }

    @AssertWarning("BitAddSignedByte")
    public int bitAdd2(int src, byte add) {
        return (src & 0xFFFFFF00) + add;
    }

    @AssertWarning("BitOrSignedByte")
    public int bitOr(int src, byte add) {
        return (src << 8) | add;
    }
}
