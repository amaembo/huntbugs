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

import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestNegativeRemainder {
    Random r = new Random(1);
    SplittableRandom sr = new SplittableRandom();
    
    @AssertWarning("HashCodeRemainder")
    public static Object getHashBucket(Object a[], Object x) {
        return a[x.hashCode() % a.length];
    }

    @AssertWarning("HashCodeRemainder")
    public static Object getHashBucket2(Object a[], Object x) {
        int i = x.hashCode() % a.length;
        return a[i];
    }
    
    @AssertWarning("HashCodeRemainder")
    public static void setHashBucket(Object a[], Object x) {
        int i = x.hashCode() % a.length;
        a[i] = 1;
    }
    
    @AssertNoWarning("HashCodeRemainder")
    public static void setHashBucketOk(Object a[], Object x) {
        int i = x.hashCode() % a.length;
        a[1] = i;
    }
    
    @AssertWarning("HashCodeRemainder")
    public static String getHashBucketList(List<String> a, Object x) {
        return a.get(x.hashCode() % a.size());
    }
    
    @AssertNoWarning("*")
    public static void setHashBucketListOk(List<Integer> a, Object x) {
        a.set(1, x.hashCode() % a.size());
    }
    
    @AssertWarning("HashCodeRemainder")
    public static void setHashBucketList(List<Integer> a, Object x) {
        a.set(x.hashCode() % a.size(), 1);
    }
    
    @AssertWarning("RandomIntRemainder")
    public void setRandomElement(List<Integer> a) {
        a.set(r.nextInt() % a.size(), 1);
    }
    
    @AssertWarning("RandomIntRemainder")
    public void setSplittableRandomElement(int[] a) {
        a[sr.nextInt() % a.length] = 1;
    }
    
    @AssertNoWarning("RandomIntRemainder")
    public void setElementToRandom(int[] a) {
        a[1] = sr.nextInt() % a.length;
    }
    
    @AssertNoWarning("*")
    public void setRandomElementOk(List<Integer> a) {
        a.set(r.nextInt(a.size()), 1);
    }
}
