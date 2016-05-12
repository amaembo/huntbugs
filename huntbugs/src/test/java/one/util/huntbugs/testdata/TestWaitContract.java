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

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestWaitContract {
    private boolean condition;

    private final Object object = new Object();

    @AssertNoWarning(type = "NotifyNaked")
    public void setCondition() {
        synchronized (object) {
            condition = true;
            object.notifyAll();
        }
    }

    @AssertWarning(type = "WaitUnconditional")
    public void noLoopOrTest() throws Exception {

        synchronized (object) {
            object.wait();
        }
    }

    @AssertWarning(type = "NotifyNaked")
    public void nakedNotify() throws Exception {
        synchronized (object) {
            object.notify();
        }
    }
    
    @AssertWarning(type = "WaitNotInLoop")
    public void noLoop() throws Exception {

        synchronized (object) {
            if (!condition)
                object.wait();
        }
    }

    @AssertNoWarning(type = "Wait*")
    public void whileDo() throws Exception {

        synchronized (object) {
            while (!condition) {
                object.wait();
            }
        }
    }

    @AssertWarning(type = "WaitUnconditional")
    public void doWhile() throws Exception {

        synchronized (object) {
            do {
                object.wait();
            } while (!condition);
        }
    }

}
