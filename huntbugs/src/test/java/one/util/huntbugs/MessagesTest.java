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
package one.util.huntbugs;

import static org.junit.Assert.*;

import java.util.Arrays;

import one.util.huntbugs.warning.Formatter;
import one.util.huntbugs.warning.Messages;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningType;
import one.util.huntbugs.warning.Messages.Message;

import org.junit.Test;

/**
 * @author Tagir Valeev
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

    @Test
    public void testFormatter() {
        Formatter f = new Formatter();
        WarningType type = new WarningType("BadPractice", "RoughConstantValue", 60);
        Warning w = new Warning(type, 0, Arrays.asList(WarningAnnotation.forNumber(3.1415), new WarningAnnotation<>("REPLACEMENT", "Math.PI")));
        assertEquals("Rough value of known constant is used", f.getTitle(w));
        assertEquals("Constant 3.1415 should be replaced with Math.PI", f.getDescription(w));
    }
}
