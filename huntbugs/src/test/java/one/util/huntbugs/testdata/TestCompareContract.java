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

import java.util.Comparator;

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author shustkost
 *
 */
public class TestCompareContract implements Comparable<TestCompareContract>{

    @Override
    @AssertWarning("CompareReturnsMinValue")
    public int compareTo(TestCompareContract o) {
        return o == this ? 0 : Integer.MIN_VALUE;
    }
    
    Comparator<String> CMP = new Comparator<String>() {
        @Override
        @AssertWarning("CompareReturnsMinValue")
        public int compare(String o1, String o2) {
            if(o1.isEmpty())
                return Integer.MIN_VALUE;
            if(o2.isEmpty())
                return Integer.MAX_VALUE;
            return o1.compareTo(o2);
        }
    };
}
