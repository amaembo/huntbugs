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
public class TestVolatileIncrement {
    volatile int x;
    volatile long y;
    volatile double z;
    int a;
    long b;
    double c;

    @AssertNoWarning("Volatile*")
    public int testNonVolatile() {
        a++;
        ++b;
        c*=2;
        return (int) (a+b+c);
    }

    @AssertWarning(value="VolatileIncrement", minScore = 70)
    public int testPre() {
        return x += 2;
    }
    
    @AssertWarning(value="VolatileIncrement", minScore = 70)
    public int testPost() {
        return ++x;
    }

    @AssertWarning(value="VolatileIncrement", minScore = 40, maxScore = 50)
    public synchronized int testPostSynchronized() {
        return ++x;
    }
    
    @AssertWarning(value="VolatileIncrement", minScore = 40, maxScore = 50)
    public int testPostSynchronizedBlock() {
        synchronized (this) {
            return ++x;
        }
    }
    
    @AssertWarning(value="VolatileMath", minScore = 70)
    public int testMul() {
        return x *= 2;
    }

    @AssertWarning(value="VolatileMath", minScore = 80)
    public double testMulDouble() {
        z *= 3;
        return z *= 2;
    }

    @AssertWarning(value="VolatileIncrement", minScore = 80)
    public long testPreLong() {
        return y += 2;
    }

    @AssertWarning(value="VolatileIncrement", minScore = 80)
    public long testPostLong() {
        return ++y;
    }
}
