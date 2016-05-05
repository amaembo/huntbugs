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

import java.io.File;
import java.nio.file.FileSystems;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestRegexProblems {
    @AssertWarning(type="RegexBadSyntax")
    public void testBadRegex(String test) {
        if(test.matches("***")) {
            System.out.println("Matches");
        }
    }

    @AssertWarning(type="RegexUnintended")
    public void testPipe(String test) {
        for(String part : test.split("|")) {
            System.out.println(part);
        }
    }
    
    @AssertWarning(type="RegexUnintended")
    public void testDot(String test) {
        for(String part : test.split(".")) {
            System.out.println(part);
        }
    }

    @AssertNoWarning(type="RegexUnintended")
    public String testDotReplace(String test) {
        return test.replaceAll(".", " ");
    }

    @AssertWarning(type="RegexUnintended")
    public String testDotReplaceFirst(String test) {
        return test.replaceFirst(".", " ");
    }

    @AssertWarning(type="RegexFileSeparator")
    public String[] testFileSeparator(String test) {
        return test.split(File.separator);
    }
    
    @AssertWarning(type="RegexFileSeparator")
    public String[] testFileSeparator2(String test) {
        return test.split(FileSystems.getDefault().getSeparator());
    }
}
