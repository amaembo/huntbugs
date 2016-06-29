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

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestExceptionalExpression {
    @AssertWarning("ExceptionalExpression")
    public void testDivisionByZero() {
        int a = 1;
        int b = 0;
        System.out.println(a/b);
    }

    @AssertWarning(value="ExceptionalExpression", maxScore=60)
    public void testNFE() {
        String s = "test";
        try {
            System.out.println(Integer.parseInt(s));
        }
        catch(NumberFormatException nfe) {
            System.out.println("Well...");
        }
    }
}
