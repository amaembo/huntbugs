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
package one.util.huntbugs.testdata.sub;

import one.util.huntbugs.testdata.TestFieldAccess;

/**
 * @author lan
 *
 */
public class SubFieldAccess {
    int val = TestFieldAccess.FieldInterface.usedArr[1];
    
    Object val2 = TestFieldAccess.usedEverywhere;
    
    static {
        System.out.println(TestFieldAccess.FieldInterface.emptyStrings);
        System.out.println(TestFieldAccess.FieldInterface.strings);
        System.out.println(TestFieldAccess.FieldInterface.stringsList);
    }
}
