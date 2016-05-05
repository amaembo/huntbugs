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

import java.security.SecureRandom;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestRandomUsage {
    @AssertWarning(type="RandomDoubleToInt")
    public int test() {
        return (int) Math.random();
    }

    @AssertWarning(type="RandomNextIntViaNextDouble", maxScore=35)
    public int testMul() {
        return (int) (10*Math.random());
    }
    
    @AssertWarning(type="RandomDoubleToInt")
    public int testRnd(Random r) {
        return (int) r.nextDouble();
    }
    
    @AssertWarning(type="RandomNextIntViaNextDouble")
    public int testRndMult(Random r) {
        return (int) (r.nextDouble()*10);
    }
    
    @AssertWarning(type="RandomDoubleToInt")
    public int testRnd() {
        return (int) ThreadLocalRandom.current().nextDouble();
    }
    
    @AssertNoWarning(type="Random*")
    public int testRndOk() {
        return (int) ThreadLocalRandom.current().nextDouble(100);
    }
    
    @AssertWarning(type="RandomUsedOnlyOnce")
    public int testRndOnce() {
        return new SplittableRandom().nextInt(10, 20);
    }
    
    @AssertNoWarning(type="RandomUsedOnlyOnce")
    public int testRndOnceSecure() {
        return new SecureRandom().nextInt();
    }
}
