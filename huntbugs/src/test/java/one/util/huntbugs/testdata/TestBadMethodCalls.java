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

import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.Assert;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestBadMethodCalls {
    ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(10);
    
    @AssertWarning(value="SystemExit", maxScore = 30)
    public void systemExit() {
        System.exit(0);
    }

    @SuppressWarnings("deprecation")
    @AssertWarning("SystemRunFinalizersOnExit")
    public void runFinalizers() {
        System.runFinalizersOnExit(true);
    }
    
    @AssertWarning(value="SystemExit", minScore = 40)
    public void doSomething() {
        System.exit(0);
    }

    @AssertWarning(value="SystemGc", maxScore = 40)
    public void collectSomeGarbage() {
        System.gc();
    }
    
    @AssertNoWarning("SystemGc")
    public void collectGarbageInCatch() {
        try {
            System.out.println();
        }
        catch(OutOfMemoryError ex) {
            System.gc();
        }
        try {
            System.out.println();
        }
        catch(StackOverflowError | OutOfMemoryError ex) {
            System.gc();
        }
    }
    
    @AssertNoWarning("SystemGc")
    public void collectGarbageTimeMeasure() {
        System.gc();
        long start = System.nanoTime();
        System.out.println();
        long end = System.nanoTime();
        System.out.println(end - start);
    }
    
    @AssertWarning("SystemGc")
    public void collectGarbageInGeneralCatch() {
        try {
            System.out.println();
        }
        catch(Exception ex) {
            System.gc();
        }
    }
    
    @AssertNoWarning("System*")
    public static void main(String[] args) {
        System.gc();
        System.exit(0);
	}

    @SuppressWarnings("deprecation")
    @AssertWarning("ThreadStopThrowable")
    public void threadStopThrowable() {
        Thread.currentThread().stop(new Exception());
    }
    
    @SuppressWarnings("deprecation")
    @AssertNoWarning("ThreadStopThrowable")
    public void threadStop() {
        Thread.currentThread().stop();
    }

    @AssertWarning("UselessThread")
    public String testCreateThread() {
        return new Thread().getName();
    }
    
    static class MyThread extends Thread
    {
        @AssertNoWarning("UselessThread")
        public MyThread() {
        }
        
        @AssertWarning("UselessThread")
        public MyThread(int x) {
            System.out.println(new Thread().getName()+x);
        }
        
        @Override
        public void run() {
            System.out.println("My thread");
        }
    }
    
    @AssertWarning("BigDecimalConstructedFromDouble") 
    public BigDecimal testBigDecimal() {
        return new BigDecimal(1.33);
    }
    
    @AssertWarning("BigDecimalConstructedFromInfiniteOrNaN") 
    public BigDecimal testBigDecimalInf() {
        return new BigDecimal(Double.POSITIVE_INFINITY);
    }
    
    @AssertNoWarning("BigDecimal*") 
    public BigDecimal testBigDecimalRound(double x) {
        return new BigDecimal(1.5).add(new BigDecimal(x));
    }
    
    @AssertWarning("URLBlockingMethod")
    public int urlHashCode(URL url) {
        return url.hashCode();
    }
    
    @AssertWarning("URLBlockingMethod")
    public boolean urlEquals(URL url1, URL url2) {
        return url1.equals(url2);
    }
    
    @AssertWarning("ArrayToString")
    public String format(String prefix, int[] arr) {
        return prefix+":"+arr;
    }
    
    @AssertWarning("ArrayToString")
    public String format2(String suffix, int[] arr) {
        return arr+":"+suffix;
    }
    
    @AssertWarning("ArrayToString")
    public String format3(int[] arr) {
        return arr.toString();
    }
    
    @AssertNoWarning("ArrayToString")
    public String format4(String arr) {
        return arr+arr;
    }
    
    @AssertWarning("ArrayHashCode")
    public int hash(String str, int[] arr) {
        return str.hashCode()*31+arr.hashCode();
    }
    
    @AssertWarning("ArrayHashCode")
    public int hash2(String str, int[] arr) {
        return Objects.hashCode(str)*31+Objects.hashCode(arr);
    }
    
    @AssertWarning("ArrayHashCode")
    public int hash3(String str, int[] arr) {
        return Objects.hash(str, arr);
    }
    
    @AssertWarning("DoubleLongBitsToDoubleOnInt")
    public double testDouble(int x) {
        return Double.longBitsToDouble(x);
    }

    @AssertWarning("ScheduledThreadPoolExecutorChangePoolSize")
    public void testThreadPoolExecutor(int poolSize) {
        ex.setMaximumPoolSize(poolSize);
    }

    @SuppressWarnings("deprecation")
    @AssertWarning("DateBadMonth")
    public void testBadMonth(Date date) {
        date.setMonth(12);
    }
    
    @AssertWarning("CollectionAddedToItself") 
    public void testAddCollection(Collection<Object> c) {
        c.add(c);
    }

    @AssertWarning("CollectionAddedToItself") 
    public void testAddArrayList(ArrayList<Object> c) {
        c.add(c);
    }
    
    @AssertWarning("NullCheckMethodForConstant")
    public void testNullCheckAssert() {
        Objects.requireNonNull("test");
    }

    @AssertNoWarning("NullCheckMethodForConstant")
    public void testNullCheckAssertOk() {
        Objects.requireNonNull(null);
    }

    @AssertWarning("NullCheckMethodForConstant")
    public void testNullCheckAssert2() {
        String s = "test";
        Assert.assertNotNull(s);
    }
    
    @AssertWarning("WrongArgumentOrder")
    public void testWrongAssert(String str) {
        Assert.assertNotNull(str, "String is null");
    }

    @AssertNoWarning("*")
    public void testCorrectAssert(String str) {
        Assert.assertNotNull("String is null", str);
    }
    
    @AssertWarning("WrongArgumentOrder")
    public void testWrongPrecondition(String str) {
        Objects.requireNonNull("String is null", str);
    }
    
    @AssertNoWarning("*")
    public void testCorrectPrecondition(String str) {
        Objects.requireNonNull(str, "String is null");
    }

    private final int[] state = new int[10];
    
    public TestBadMethodCalls() {
    }

    // Two constructors are necessary to test merging of state initialization
    public TestBadMethodCalls(int x) {
        System.out.println(x);
    }
    
    @Override
    @AssertWarning("ArrayHashCode")
    public int hashCode() {
        return state.hashCode();
    }
}
