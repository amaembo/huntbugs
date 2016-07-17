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

import java.io.IOException;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestMutableServletField {

    @AssertNoWarning("*")
    public static class TestServletOk extends GenericServlet {
        private static final long serialVersionUID = 1L;
        
        long data;
        
        public TestServletOk() {
            data = System.currentTimeMillis();
        }
        
        public long getData() {
            return data;
        }

        @Override
        public void service(ServletRequest arg0, ServletResponse arg1) throws ServletException, IOException {
        }
    }
    
    public static class TestServlet extends GenericServlet {
        private static final long serialVersionUID = 1L;
        
        @AssertWarning("MutableServletField")
        long data;
        
        public long getData() {
            return data;
        }

        @Override
        public void service(ServletRequest arg0, ServletResponse arg1) throws ServletException, IOException {
            data = System.currentTimeMillis();
        }
    }
}
