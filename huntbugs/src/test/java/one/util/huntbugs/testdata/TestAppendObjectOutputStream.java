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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestAppendObjectOutputStream {
    @AssertWarning(type="AppendObjectOutputStream")
    public ObjectOutputStream createStream() throws IOException {
        return new ObjectOutputStream(new FileOutputStream("/tmp/file", true));
    }

    @AssertWarning(type="AppendObjectOutputStream")
    public ObjectOutputStream createStreamComplex() throws IOException {
        OutputStream out = new FileOutputStream("/tmp/file", true);
        out = new BufferedOutputStream(out);
        return new ObjectOutputStream(out);
    }

    @AssertWarning(type="AppendObjectOutputStream")
    public ObjectOutputStream createStreamConditional(boolean buffered) throws IOException {
        OutputStream out = new FileOutputStream("/tmp/file", true);
        if(buffered)
            out = new BufferedOutputStream(out);
        return new ObjectOutputStream(out);
    }
    
    @AssertNoWarning(type="AppendObjectOutputStream")
    public ObjectOutputStream createStreamNoAppend() throws IOException {
        return new ObjectOutputStream(new FileOutputStream("/tmp/file", false));
    }
}
