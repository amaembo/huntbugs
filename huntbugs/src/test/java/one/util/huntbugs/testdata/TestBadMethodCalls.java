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

import java.math.BigDecimal;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestBadMethodCalls {
    @AssertWarning(type="SystemExit", maxScore = 30)
    public void systemExit() {
        System.exit(0);
    }

    @AssertWarning(type="SystemRunFinalizersOnExit")
    public void runFinalizers() {
        System.runFinalizersOnExit(true);
    }
    
    @AssertWarning(type="SystemExit", minScore = 40)
    public void doSomething() {
        System.exit(0);
    }

    @AssertWarning(type="SystemGc", maxScore = 40)
    public void collectSomeGarbage() {
        System.gc();
    }
    
    @AssertNoWarning(type="SystemGc")
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
    
    @AssertNoWarning(type="SystemGc")
    public void collectGarbageTimeMeasure() {
        System.gc();
        long start = System.nanoTime();
        System.out.println();
        long end = System.nanoTime();
        System.out.println(end - start);
    }
    
    @AssertWarning(type="SystemGc")
    public void collectGarbageInGeneralCatch() {
        try {
            System.out.println();
        }
        catch(Exception ex) {
            System.gc();
        }
    }
    
    @AssertNoWarning(type="System*")
    public static void main(String[] args) {
        System.gc();
        System.exit(0);
	}

    @AssertWarning(type="ThreadStopThrowable")
    public void threadStopThrowable() {
        Thread.currentThread().stop(new Exception());
    }
    
    @AssertNoWarning(type="ThreadStopThrowable")
    public void threadStop() {
        Thread.currentThread().stop();
    }

    @AssertWarning(type="UselessThread")
    public String testCreateThread() {
        return new Thread().getName();
    }
    
    static class MyThread extends Thread
    {
        @AssertNoWarning(type="UselessThread")
        public MyThread() {
        }
        
        @AssertWarning(type="UselessThread")
        public MyThread(int x) {
            System.out.println(new Thread().getName()+x);
        }
        
        @Override
        public void run() {
            System.out.println("My thread");
        }
    }
    
    @AssertWarning(type="BigDecimalConstructedFromDouble") 
    public BigDecimal testBigDecimal() {
        return new BigDecimal(1.33);
    }
    
    @AssertWarning(type="BigDecimalConstructedFromInfiniteOrNaN") 
    public BigDecimal testBigDecimalInf() {
        return new BigDecimal(Double.POSITIVE_INFINITY);
    }
    
    @AssertNoWarning(type="BigDecimalConstructedFromDouble") 
    public BigDecimal testBigDecimalRound(double x) {
        return new BigDecimal(1.5).add(new BigDecimal(x));
    }
}
