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

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestEqualsContract {

    @Override
    @AssertWarning(value="EqualsReturnsFalse", minScore = 40)
    public boolean equals(Object obj) {
        return false;
    }

    static class NonPublic {
        @Override
        @AssertWarning(value="EqualsReturnsTrue", maxScore = 30)
        public boolean equals(Object obj) {
            return true;
        }

        @Override
        public int hashCode() {
            return 42;
        }
    }

    public static class SubClass2 extends NonPublic {
        int f;

        @Override
        @AssertWarning("EqualsNoHashCode")
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!super.equals(obj) || getClass() != obj.getClass())
                return false;
            SubClass2 other = (SubClass2) obj;
            return f == other.f;
        }
    }

    public static final class Final {
        @Override
        @AssertWarning(value="EqualsReturnsFalse", minScore = 35, maxScore = 45)
        @AssertNoWarning("EqualsObjectHashCode")
        public boolean equals(Object obj) {
            return false;
        }
    }

    public static final class ClassName {
        public int i;

        @Override
        @AssertWarning("EqualsClassNames")
        public boolean equals(Object obj) {
            if (!"one.util.huntbugs.testdata.TestEqualsContract.ClassName".equals(obj.getClass().getName()))
                return false;
            ClassName other = (ClassName) obj;
            return i == other.i;
        }
    }

    @AssertWarning("EqualsOther")
    public static class OtherEquals {
        public boolean equals(TestEqualsContract other) {
            return other != null;
        }
    }

    @AssertWarning("EqualsSelf")
    public static class SelfEquals {
        public boolean equals(SelfEquals other) {
            return other == this;
        }

        @Override
        public int hashCode() {
            return 42;
        }
    }

    public static class SubClass extends SelfEquals {
        int f;

        @Override
        @AssertWarning("EqualsNoHashCode")
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!super.equals(obj) || getClass() != obj.getClass())
                return false;
            SubClass other = (SubClass) obj;
            return f == other.f;
        }
    }

    public static class SubClassNoFields extends SelfEquals {
        @Override
        @AssertNoWarning("EqualsNoHashCode")
        public boolean equals(Object obj) {
            return obj instanceof SubClassNoFields && super.equals(obj);
        }
    }
    
    public static class SubClassNoFields1 extends SelfEquals {
        @Override
        @AssertNoWarning("EqualsNoHashCode")
        public boolean equals(Object obj) {
            return obj == this || obj instanceof SubClassNoFields && super.equals(obj);
        }
    }
    
    public static class SubClassNoFields1a extends SelfEquals {
        @Override
        @AssertNoWarning("EqualsNoHashCode")
        public boolean equals(Object obj) {
            return this == obj || obj instanceof SubClassNoFields && super.equals(obj);
        }
    }
    
    public static class SubClassNoFields2 extends SelfEquals {
        @Override
        @AssertNoWarning("EqualsNoHashCode")
        public boolean equals(Object obj) {
            if(!(obj instanceof SubClassNoFields2))
                return false;
            return super.equals(obj);
        }
    }
    
    @AssertWarning("EqualsEnum")
    @AssertNoWarning("EqualsSelf")
    public static enum EnumEquals {
        A, B, C;

        public boolean equals(EnumEquals other) {
            return other == this;
        }
    }

    @AssertNoWarning("*")
    public static class EqualsOk {
        public boolean equals(EqualsOk other, int check) {
            return other != this && check > 2;
        }
    }

    public static class HashCodeObject {
        @Override
        @AssertWarning("HashCodeObjectEquals")
        public int hashCode() {
            return 42;
        }
    }

    public static class HashCodeList extends ArrayList<String> {
        private static final long serialVersionUID = 1L;

        String myField;

        public HashCodeList(String myField) {
            this.myField = myField;
        }

        @Override
        @AssertWarning("HashCodeNoEquals")
        public int hashCode() {
            return super.hashCode() * 31 + myField.hashCode();
        }
    }

    public static class EqualsObject {
        public int f;

        @Override
        @AssertWarning(value="EqualsObjectHashCode", maxScore = 48, minScore = 43)
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            EqualsObject other = (EqualsObject) obj;
            return f == other.f;
        }
    }

    static class EqualsObject2 {
        public int f;

        @Override
        @AssertWarning(value="EqualsObjectHashCode", maxScore = 32, minScore = 22)
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            EqualsObject other = (EqualsObject) obj;
            return f == other.f;
        }
    }

    @AssertNoWarning("Equals*")
    public static class EqualsList extends ArrayList<String> {
        private static final long serialVersionUID = 1L;
        public int f;

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!super.equals(obj) || getClass() != obj.getClass())
                return false;
            EqualsList other = (EqualsList) obj;
            return f == other.f;
        }
    }
    
    public static class EqualsWrongField {
        private int x;
        private int y;
        private int[] arr1;
        private int[] arr2;

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + x;
            result = prime * result + y;
            return result;
        }
        
        @Override
        @AssertWarning("EqualsSuspiciousFieldComparison")
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            EqualsWrongField other = (EqualsWrongField) obj;
            return x == other.y && Arrays.equals(arr1, other.arr2);
        }
    }

    @AssertNoWarning("*")
    public static class EqualsFieldsOk {
        private final int x;
        private final int y;
        
        public EqualsFieldsOk(int x, int y) {
            this.x = x;
            this.y = y;
        }
        
        public Point getPoint() {
            return new Point(x, y);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            Point pt = ((EqualsFieldsOk) obj).getPoint();
            return x == pt.x && y == pt.y;
        }
    }
}
