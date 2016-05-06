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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author shustkost
 *
 */
public class TestCheckReturnValue {
    @AssertWarning(type="ReturnValueOfRead")
    public void read(InputStream is, byte[] arr) throws IOException {
        is.read(arr);
    }

    @AssertWarning(type="ReturnValueOfSkip")
    public void skipTen(Reader r) throws IOException {
        r.skip(10);
    }

    @AssertNoWarning(type="ReturnValueOfSkip")
    public void skipTenOk(Reader r) throws IOException {
        long skip = 10;
        while(skip > 0) {
            skip -= r.skip(skip);
        }
    }

    @AssertNoWarning(type="*")
    public void readOk(InputStream is, byte[] arr) throws IOException {
        if(is.read(arr) != arr.length) {
            throw new IOException("Not fuly read");
        }
    }
}
