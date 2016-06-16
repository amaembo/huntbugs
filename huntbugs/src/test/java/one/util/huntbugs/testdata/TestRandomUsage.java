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

import java.security.SecureRandom;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestRandomUsage {
    @AssertWarning("RandomDoubleToInt")
    public int test() {
        return (int) Math.random();
    }

    @AssertWarning(value="RandomNextIntViaNextDouble", maxScore=35)
    public int testMul() {
        return (int) (10*Math.random());
    }
    
    @AssertWarning("RandomDoubleToInt")
    public int testRnd(Random r) {
        return (int) r.nextDouble();
    }
    
    @AssertWarning("RandomNextIntViaNextDouble")
    public int testRndMult(Random r) {
        return (int) (r.nextDouble()*10);
    }
    
    @AssertWarning("RandomDoubleToInt")
    public int testRnd() {
        return (int) ThreadLocalRandom.current().nextDouble();
    }
    
    @AssertNoWarning("Random*")
    public int testRndOk() {
        return (int) ThreadLocalRandom.current().nextDouble(100);
    }
    
    @AssertWarning("RandomUsedOnlyOnce")
    public int testRndOnce() {
        return new SplittableRandom().nextInt(10, 20);
    }
    
    @AssertNoWarning("RandomUsedOnlyOnce")
    public int[] testRndArr() {
        return new Random().ints(100).toArray();
    }
    
    @AssertNoWarning("RandomUsedOnlyOnce")
    public int testRndOnceSecure() {
        return new SecureRandom().nextInt();
    }
}
