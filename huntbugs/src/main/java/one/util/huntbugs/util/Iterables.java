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
package one.util.huntbugs.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author lan
 *
 */
public class Iterables {
    public static <T> Iterable<T> concat(Iterable<? extends T> it1, Iterable<? extends T> it2) {
        return () -> new Iterator<T>() {
            boolean first = true;
            Iterator<? extends T> it = it1.iterator();

            @Override
            public boolean hasNext() {
                boolean hasNext = it.hasNext();
                if(hasNext)
                    return true;
                if(first) {
                    first = false;
                    it = it2.iterator();
                    return hasNext();
                }
                return false;
            }

            @Override
            public T next() {
                if(first) {
                    hasNext();
                }
                return it.next();
            }
        };
    }
    
    public static <T> List<T> toList(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        iterable.forEach(list::add);
        return list;
    }
}
