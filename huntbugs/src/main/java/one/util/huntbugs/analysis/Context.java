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
package one.util.huntbugs.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import one.util.huntbugs.warning.Warning;

/**
 * @author lan
 *
 */
public class Context {
    List<ErrorMessage> errors = Collections.synchronizedList(new ArrayList<>());
    List<Warning> warnings = Collections.synchronizedList(new ArrayList<>());
    
    public void Context() {
        
    }
    
    public void addError(ErrorMessage msg) {
        errors.add(msg);
    }
    
    public void addWarning(Warning warning) {
        warnings.add(warning);
    }
    
    public void reportWarnings(Appendable app) {
        warnings.forEach(msg -> {
            try {
                app.append(msg.toString()).append("\n");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
    
    public void reportErrors(Appendable app) {
        errors.forEach(msg -> {
            try {
                app.append(msg.toString()).append("\n");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
