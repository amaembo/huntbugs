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

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestRedundantStreamCalls {
    @AssertWarning("RedundantStreamForEach")
    public void testSet(Set<String> x) {
        x.stream().forEach(System.out::println);
    }

    @AssertWarning("RedundantStreamForEach")
    public void testArrayList(ArrayList<String> x) {
        x.stream().forEachOrdered(System.out::println);
    }
    
    @AssertWarning("RedundantStreamFind")
    public boolean testArrayListFind(ArrayList<String> x) {
        return x.stream().filter(str -> str.startsWith("xyz")).findFirst().isPresent();
    }
    
    @AssertWarning("RedundantStreamFind")
    public boolean testStreamFind(Set<Integer> x) {
        Stream<Integer> set = x.parallelStream();
        return set.filter(i -> i > 0).findAny().isPresent();
    }
    
    @AssertWarning("RedundantStreamFind")
    public boolean testStreamFindVar(Set<Integer> x) {
        Optional<Integer> opt = x.parallelStream().filter(i -> i > 0).findAny();
        return opt.isPresent();
    }
    
    @AssertNoWarning("*")
    public Integer testStreamFindOk(Set<Integer> x) {
        Optional<Integer> opt = x.parallelStream().filter(i -> i > 0).findAny();
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }
    
    @AssertWarning("RedundantStreamFind")
    public boolean testIntStreamFind(IntStream is) {
        return is.filter(i -> i > 0).findAny().isPresent();
    }
    
    @AssertNoWarning("*")
    public void testIntermediate(ArrayList<String> x) {
        x.stream().map(String::trim).forEachOrdered(System.out::println);
    }
    
    @AssertNoWarning("*")
    public void testParallel(ArrayList<String> x) {
        x.parallelStream().forEach(System.out::println);
    }
}
