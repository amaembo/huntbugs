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

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestNumberConstructor {
    @AssertWarning(type = "NumberConstructor", minRank = 42)
    public Integer testInteger() {
        return new Integer(123);
    }

    @AssertWarning(type = "NumberConstructor", minRank = 20, maxRank = 35)
    public Integer testInteger2() {
        return new Integer(130);
    }

    @AssertWarning(type = "NumberConstructor", maxRank = 15)
    public Long testLong() {
        return new Long(130);
    }

    @AssertWarning(type = "NumberConstructor", maxRank = 42)
    public Integer testInteger3(int x) {
        return new Integer(x);
    }

    @AssertWarning(type = "NumberConstructor", maxRank = 42)
    public Character testChar(char x) {
        return new Character(x);
    }

    @AssertWarning(type = "NumberConstructor", maxRank = 42)
    public Boolean testBoolean(boolean x) {
        return new Boolean(x);
    }

    @AssertNoWarning(type = "NumberConstructor")
    public Float testFloat(float x) {
        return new Float(x);
    }
}
