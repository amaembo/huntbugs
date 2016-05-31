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

import java.util.ConcurrentModificationException;
import java.util.List;

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestDubiousCatch {
    Object obj = new Object();

    @AssertWarning("CatchIllegalMonitorStateException")
    public boolean catchIllegalState() {
        try {
            obj.wait();
            return true;
        } catch (InterruptedException | IllegalMonitorStateException e) {
            return false;
        }
    }

    @AssertWarning("CatchConcurrentModificationException")
    public int catchCME(List<String> list) {
        try {
            int i = 0;
            for (String s : list) {
                if (s.equals("test"))
                    i++;
            }
            return i;
        } catch (ConcurrentModificationException e) {
            return 0;
        }
    }

    @AssertWarning("CatchConcurrentModificationException")
    public void catchCMEVoid(List<String> list) {
        try {
            for (String s : list) {
                if (s.equals("test"))
                    System.out.println(s);
            }
        } catch (ConcurrentModificationException e) {
            // ignore
        }
    }
}
