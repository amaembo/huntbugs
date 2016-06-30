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

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestAbandonedStream {
    @AssertWarning("AbandonedStream")
    public void testSimple(List<String> list) {
        list.stream().map(String::trim);
    }
    
    @AssertNoWarning("AbandonedStream")
    public Stream<String> testClose(List<String> list) {
        Stream<String> stream = list.stream();
        stream.onClose(() -> System.out.println("Closed!"));
        return stream;
    }
    
    @AssertNoWarning("*")
    public void testSimpleOk(List<String> list) {
        list.stream().map(String::trim).forEach(System.out::println);
    }
    
    @AssertNoWarning("*")
    public Stream<String> testSimpleReturn(List<String> list) {
        return list.stream().map(String::trim);
    }

    @AssertWarning("AbandonedStream")
    public void testVar(List<String> list) {
        Stream<String> stream = list.stream();
        stream = stream.map(String::trim);
    }
    
    @AssertNoWarning("*")
    public void testVarOk(List<String> list) {
        Stream<String> stream = list.stream();
        stream = stream.map(String::trim);
        stream.forEach(System.out::println);
    }
    
    @AssertWarning("AbandonedStream")
    public void testIf(List<String> list, boolean b) {
        Stream<String> stream = list.stream();
        if(b) {
            stream = stream.map(String::trim);
        }
    }
    
    @AssertNoWarning("*")
    public void testIfOk(List<String> list, boolean b) {
        Stream<String> stream = list.stream();
        if(b) {
            stream = stream.map(String::trim);
        }
        stream.forEach(System.out::println);
    }
    
    @AssertNoWarning("*")
    public void testTernaryOk(List<String> list, boolean b) {
        Stream<String> stream = b ? list.stream() : list.stream().map(String::trim);
        stream.forEach(System.out::println);
    }
    
    @AssertWarning("AbandonedStream")
    public void testToPrimitive(List<String> list) {
        list.stream().mapToInt(String::length);
    }
    
    @AssertWarning("AbandonedStream")
    public void testPrimitive(int[] data) {
        IntStream.of(data).mapToObj(String::valueOf);
    }
}
