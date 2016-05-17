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

import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * @author Tagir Valeev
 *
 * Tests for some procyon bugs
 */
public class TestProcyon<K> {
    public <V> void print(Map<K, V> map) {
        map.keySet().forEach(k -> System.out.println(k));
        map.values().forEach(v -> System.out.println(v));
        map.entrySet().forEach(e -> System.out.println(e));
    }
    
    public void test() {
        new X("abc"){};
    }
    
    class X {
        <P> X(P arg) {
            System.out.println(arg);
        }
    }
    
    public String infer(Stream<?> stream) {
        return stream.collect(Collector.of(() -> "", (acc, t) -> {}, (acc1, acc2) -> acc1));
    }
}
