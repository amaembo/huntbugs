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

import java.util.Random;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestDeadLocalStore {
    int x;

    @AssertWarning(value="ParameterOverwritten", minScore = 55)
    public void testDeadLocalSimple(int x) {
        x = 10;
        System.out.println(x);
    }

    @AssertNoWarning("ParameterOverwritten")
    public void testDeadLocalBranch(int x) {
        if (Math.random() > 0.5)
            x = 10;
        System.out.println(x);
    }

    class Extension extends TestDeadLocalStore {
        @Override
        @AssertWarning(value="ParameterOverwritten", maxScore = 40)
        public void testDeadLocalSimple(int x) {
            x = 10;
            System.out.println(x);
        }
    }

    @AssertWarning("DeadIncrementInReturn")
    public int testDeadIncrement(int x) {
        return x++;
    }

    @AssertNoWarning("DeadIncrementInReturn")
    public int testFieldIncrement() {
        return x++;
    }

    @SuppressWarnings("unused")
    @AssertWarning("DeadStoreInReturn")
    public boolean testDeadStore(boolean b) {
        return b = true;
    }

    @AssertWarning("DeadIncrementInAssignment")
    public void testDeadIncrementAssignment(int i) {
        i = i++;
        System.out.println(i);
    }

    @AssertNoWarning("*")
    public void testDeadIncrementAssignment2(int i) {
        int x = i++;
        System.out.println(i);
        i = x;
        System.out.println(i);
    }
    
    @AssertWarning("DeadParameterStore")
    public void testDeadParameterStore(int i) {
        if(i > 0) {
            i = 0;
        }
    }
    
    @AssertWarning(value = "DeadLocalStore", maxScore=45)
    public void testDeadLocalStore(int i) {
        int x = 0;
        if(i > x) {
            x = 1;
        }
        System.out.println(i);
    }
    
    @AssertWarning(value = "DeadLocalStore", minScore=46)
    public void testDeadLocalStore2(int i) {
        int x = 1;
        if(i > x) {
            x = 2;
        }
        System.out.println(i);
    }
    
    @AssertNoWarning("*")
    public void testDeadLocalStoreSame(int i) {
        int x = 1;
        if(i > x) {
            x = 1;
        }
        System.out.println(x);
    }
    
    @AssertNoWarning("*")
    public void testDeadLocalStoreLambda() {
        double x = Math.random();
        System.out.println(new Random().doubles(1000).filter(d -> d > x).count());
    }

    @AssertNoWarning("*")
    public int testDeadLocalStoreLambda2(boolean b) {
        if (b) {
            double x = Math.random();
            System.out.println(new Random().doubles(1000).filter(d -> d > x).count());
            return 1;
        }
        return 0;
    }
    
    @AssertNoWarning("*")
    public void testDeadLocalStoreConst(int i) {
        final int x = 123456;
        if(i > x) {
            System.out.println(i);
        }
    }
    
    @AssertNoWarning("*")
    public void testDeadLocalStoreConstStr(int i) {
        final String x = "test";
        if(i > x.length()) {
            System.out.println(i);
        }
    }
    
    @AssertNoWarning("*")
    public void testDeadLocalStoreCatch(String i) {
        try {
            System.out.println(Integer.parseInt(i));
        }
        catch(NumberFormatException | NullPointerException ex) {
            System.out.println("none");
        }
    }

    @AssertNoWarning("*")
    public void testThrow(String i) {
        if (i.length() > 2) {
            final String s = "exception message";
            throw new RuntimeException(i + s);
        }
    }

    @AssertNoWarning("DeadParameterStore")
    public void testDeadLocalStoreCatch2(String i) {
        try {
            System.out.println(Integer.parseInt(i));
        }
        catch(NumberFormatException ex) {
            i = "abc";
        }
        catch(Exception ex) {
            return;
        }
        System.out.println(i);
    }
    
    static class MyException extends Exception {
        private static final long serialVersionUID = 1L;
    };
    
    private int convert(String s) throws MyException {
        if(s.isEmpty())
            throw new MyException();
        return Integer.parseInt(s);
    }

    @AssertNoWarning("*")
    public void testDeadLocalStoreCatch3(String i) {
        boolean flag = false;
        try {
            if(i != null) {
                System.out.println(convert(i));
            } else {
                flag = true;
            }
        }
        catch(MyException ex) {
            flag = true;
        }
        if(flag) {
            throw new RuntimeException();
        }
    }
    
    @AssertNoWarning("*")
    public void testDeadLocalStoreLabel(boolean x, String a) {
        if(x ? !a.equals("x") : !a.equals("y"))
            return;
        int i = 1;
        System.out.println(i);
    }
    
    public void testLocalClass() {
        class X {
            @AssertWarning("ParameterOverwritten")
            public void print(int x) {
                x = 10;
                System.out.println(x);
            }
        }
        new X().print(5);
    }
}
