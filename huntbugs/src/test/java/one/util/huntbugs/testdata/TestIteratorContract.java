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

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestIteratorContract implements Iterator<String> {
    Iterator<String> input = Arrays.asList("foo", "bar").iterator();
    String nextElement;
    
    @Override
    @AssertWarning("IteratorHasNextCallsNext")
    public boolean hasNext() {
        try {
            nextElement = next();
        } catch (NoSuchElementException e) {
            return false;
        }
        return true;
    }

    @Override
    @AssertNoWarning("*")
    public String next() {
        return nextElement == null ? input.next() : nextElement;
    }
    
    public class Iterator2 implements Iterator<String> {
        int state = 0;

        @Override
        public boolean hasNext() {
            return state == 0;
        }

        @Override
        @AssertWarning("IteratorNoThrow")
        public String next() {
            return ++state == 1 ? "Hello!".toLowerCase() : null;
        }
        
    }
    
    @AssertNoWarning("*")
    public class NonIterator {
        public boolean hasNext() {
            return next() == null;
        }

        public String next() {
            return null;
        }
    }
}
