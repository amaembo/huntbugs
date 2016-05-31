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

import java.io.File;
import java.nio.file.FileSystems;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestRegexProblems {
    @AssertWarning("RegexBadSyntax")
    public void testBadRegex(String test) {
        if(test.matches("***")) {
            System.out.println("Matches");
        }
    }

    @AssertWarning("RegexUnintended")
    public void testPipe(String test) {
        for(String part : test.split("|")) {
            System.out.println(part);
        }
    }
    
    @AssertWarning("RegexUnintended")
    public void testDot(String test) {
        for(String part : test.split(".")) {
            System.out.println(part);
        }
    }

    @AssertNoWarning("RegexUnintended")
    public String testDotReplace(String test) {
        return test.replaceAll(".", " ");
    }

    @AssertWarning("RegexUnintended")
    public String testDotReplaceFirst(String test) {
        return test.replaceFirst(".", " ");
    }

    @AssertWarning("RegexFileSeparator")
    public String[] testFileSeparator(String test) {
        return test.split(File.separator);
    }
    
    @AssertWarning("RegexFileSeparator")
    public String[] testFileSeparator2(String test) {
        return test.split(FileSystems.getDefault().getSeparator());
    }
}
