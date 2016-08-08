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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author shustkost
 *
 */
public class TestNullCheck {
    @SuppressWarnings("null")
    @AssertWarning("NullDereferenceGuaranteed")
    public void testNullDeref() {
        String x = null;
        if (x.isEmpty()) {
            System.out.println("Oops");
        }
    }
    
    private void error(String s) {
        throw new IllegalArgumentException(s);
    }
    
    @AssertNoWarning("*")
    public void testErrorCall(String s) {
        if(s == null) {
            error("null");
        }
        System.out.println(s.trim());
    }

    @AssertWarning("NullDereferenceExceptional")
    public void testNullExceptional(String s) {
        Integer x = null;
        try {
            x = Integer.valueOf(s);
        } catch (NumberFormatException ex) {
            ex.printStackTrace();
        }
        if (x > 0) {
            System.out.println("Bigger");
        }
    }

    @AssertWarning("RedundantComparisonNull")
    public void testRedundantNull(String s) {
        String a = null;
        if (s == null) {
            System.out.println(1);
            if (s == a) {
                System.out.println(2);
            }
        }
    }
    
    @AssertNoWarning("*")
    public void testNullThrow(String s) {
        boolean invalid = s == null || s.isEmpty();
        if(invalid) {
            throw new IllegalArgumentException();
        }
        System.out.println(s.trim());
    }

    @AssertNoWarning("*")
    public void testFileOpen(File f1, File f2, File f3) throws IOException {
        InputStream is = null;
        boolean success = false;
        try {
            is = new FileInputStream(f1);
            success = true;
        }
        catch(IOException e) {
        }
        if(!success) {
            try {
                is = new FileInputStream(f2);
                success = true;
            } catch (IOException e) {
            }
        }
        if(!success) {
            try {
                is = new FileInputStream(f3);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        System.out.println(is.read());
    }
    
    @AssertWarning(value = "RedundantComparisonNullNonNull", maxScore = 45)
    public void testRedundantNotNull() {
        TestNullCheck other = null;
        if (this != other) {
            System.out.println("Always");
        }
    }
    
    class X {
        X parent;
    }
    
    @AssertWarning("RedundantNullCheckNull")
    public void testRedundantNullWhile(X x) {
        while(x != null) {
            x = x.parent;
        }
        if(x == null) {
            System.out.println("Always");
        }
    }

    @AssertWarning(value = "RedundantEqualsNullCheck", minScore = 55)
    public void testRedundantEqualsNull() {
        TestNullCheck other = null;
        if (this.equals(other)) {
            System.out.println("Never");
        }
    }

    @AssertNoWarning("*")
    public String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    @AssertNoWarning("*")
    public void addItem(Map<Integer, List<String>> map, int from, int to, List<String> values) {
        List<String> list = null;
        System.out.println(list);
        try {
            for (int key = from; key < to; key++) {
                list = map.get(key);
                if (list == null) {
                    list = new ArrayList<>(values);
                } else {
                    list.addAll(values);
                }
                map.put(key, list);
            }
        } catch (Exception ex) {
            list = null;
        }
        System.out.println(list);
    }

    @AssertNoWarning("*")
    public void testOk(String s, boolean b) {
        if (b) {
            System.out.println("Hello");
        } else if (s != null) {
            System.out.println(s);
        } else {
            throw new IllegalArgumentException();
        }
        System.out.println(s.trim());
    }

    @AssertWarning(value = "RedundantNullCheckNull", maxScore = 50)
    public void testNull(String s) {
        if (s == null) {
            System.out.println("Hello");
            if (null == s) {
                System.out.println("Yes");
            }
        }
    }

    @AssertNoWarning("*")
    public void testAssert(int i) {
        String s;
        if (i > 0) {
            s = String.valueOf(i);
        } else {
            s = null;
        }
        if (i > 0) {
            assert s != null;
            System.out.println(s.trim());
        }
    }

    @AssertWarning("NullDereferencePossible")
    public void testNullDerefPossible(boolean b) {
        String x = null;
        if (b) {
            x = "test";
        }
        if (x.isEmpty()) {
            System.out.println("Oops");
        }
    }

    @SuppressWarnings("unused")
    @AssertWarning("RedundantNullCheckDeref")
    public void testRedundantDeref(String str) {
        System.out.println(str.trim());
        if (str == null) {
            System.out.println("Never");
        }
    }

    @SuppressWarnings("unused")
    @AssertWarning("RedundantNullCheck")
    public void testRedundant() {
        String str = "const";
        if (str == null) {
            System.out.println("Never");
        }
    }

    @SuppressWarnings("unused")
    @AssertWarning("RedundantNullCheckChecked")
    public void testRedundantDoubleCheck(String s) {
        if (s == null) {
            throw new IllegalArgumentException();
        }
        if (s == null) {
            System.out.println("Never");
        }
    }

    @SuppressWarnings("unused")
    @AssertWarning("RedundantNullCheckChecked")
    public void testRedundantDoubleCheckCall(String s) {
        Objects.requireNonNull(s);
        if (s == null) {
            System.out.println("Never");
        }
    }
}
