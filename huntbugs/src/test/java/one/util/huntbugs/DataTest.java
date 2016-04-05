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

import java.io.File;

import one.util.huntbugs.analysis.Context;

import org.junit.Test;

import com.strobel.assembler.metadata.ClasspathTypeLoader;

/**
 * @author lan
 *
 */
public class DataTest {
    @Test
    public void test() throws Exception {
        File classRoot = new File(ClassAnalysis.class.getClassLoader().getResource(".").toURI());
        String classPath = classRoot.toString();
        Context ctx = new Context(new ClasspathTypeLoader(classPath));
        ctx.analyzeClass(ClassAnalysis.class.getName().replace(".", "/"));
        ctx.reportErrors(System.err);
        ctx.reportWarnings(System.out);
    }
}
