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

import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestMinValueHandling {
    @AssertWarning(type="AbsoluteValueOfRandomInt", minRank=78)
    public int testRandom() {
        int h = ThreadLocalRandom.current().nextInt();
        int v = Math.abs(h);
        return v % 15;
    }

    @AssertWarning(type="AbsoluteValueOfRandomInt", minRank=70, maxRank=78)
    public long testRandomLong() {
        long h = new SplittableRandom().nextLong();
        long v = Math.abs(h);
        return v % 15;
    }
    
    @AssertWarning(type="AbsoluteValueOfHashCode", minRank=85)
    public int testHashCodeRem(Object obj) {
        int h = obj.hashCode();
        int v = Math.abs(h);
        return v % 15;
    }
    
    @AssertWarning(type="AbsoluteValueOfHashCode", maxRank=60, minRank=40)
    public int testPowerOf2Rem(Object obj) {
        int h = obj.hashCode();
        int v = Math.abs(h);
        return v % 32;
    }
    
    @AssertWarning(type="AbsoluteValueOfHashCode", minRank=75, maxRank=85)
    public int testHashCode(Object obj) {
        int h = obj.hashCode();
        int v = Math.abs(h);
        return v;
    }

    @AssertNoWarning(type="AbsoluteValueOfHashCode")
    static int falsePositive(Object key) {
        int rawHash = key.hashCode();
        return rawHash == Integer.MIN_VALUE ? 0 : Math.abs(rawHash);
    }
}
