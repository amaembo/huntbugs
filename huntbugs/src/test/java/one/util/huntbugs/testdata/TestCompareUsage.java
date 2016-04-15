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

import java.util.Comparator;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestCompareUsage {
    @AssertWarning(type="NegatingComparatorResult")
    public boolean less(String s1, String s2) {
        int res = s1.compareTo(s2);
        if(s1.isEmpty())
            res = -1;
        int neg = -res;
        return neg > 0;
    }

    @AssertWarning(type="ComparingComparatorResultWithNumber")
    public boolean greater(String s1, String s2) {
        return 1 == s1.compareTo(s2);
    }

    @AssertWarning(type="ComparingComparatorResultWithNumber")
    public boolean greater2(String s1, String s2) {
        int res = s1.compareTo(s2);
        return res == 1;
    }
    
    @AssertNoWarning(type="ComparingComparatorResultWithNumber")
    public boolean eq(String s1, String s2) {
        return s1.compareTo(s2) == 0;
    }
    
    @AssertNoWarning(type="NegatingComparatorResult")
    public boolean ok(String s1, String s2) {
        int res = s1.isEmpty() ? 1 : -1;
        int neg = -res;
        return neg > 0;
    }

    @AssertWarning(type="NegatingComparatorResult")
    public boolean lessCmp(String s1, String s2) {
        return -Comparator.<String>naturalOrder().compare(s1, s2) > 0;
    }

    @AssertNoWarning(type="NegatingComparatorResult")
    public boolean lessOk(String s1, String s2) {
        return s2.compareTo(s1) > 0;
    }
    
    @AssertNoWarning(type="NegatingComparatorResult")
    public boolean lessCmpOk(String s1, String s2) {
        return Comparator.<String>naturalOrder().compare(s2, s1) > 0;
    }
}
