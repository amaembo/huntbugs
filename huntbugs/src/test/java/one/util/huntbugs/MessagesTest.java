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
package one.util.huntbugs;

import static org.junit.Assert.*;
import one.util.huntbugs.warning.Messages;
import one.util.huntbugs.warning.Messages.Message;

import org.junit.Test;

/**
 * @author lan
 *
 */
public class MessagesTest {
    @Test
    public void testMessages() {
        Messages m = Messages.load();
        Message msg = m.getMessagesForType("RoughConstantValue");
        assertEquals("Rough value of known constant is used", msg.getTitle());
        assertEquals("Constant $NUMBER$ should be replaced with $REPLACEMENT$", msg.getDescription());
    }
}
