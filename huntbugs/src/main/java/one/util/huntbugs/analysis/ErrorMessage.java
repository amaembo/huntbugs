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

import one.util.huntbugs.registry.Detector;

import com.strobel.assembler.metadata.MethodDefinition;

/**
 * @author lan
 *
 */
public class ErrorMessage {
    private final String className;
    private final String elementName;
    private final String descriptor;
    private final int line;
    private final Throwable error;
    private final String detector;
    
    public ErrorMessage(Detector detector, MethodDefinition method, int line, Throwable error) {
        this(detector == null ? null : detector.toString(), method.getDeclaringType().getFullName(), method
                .getFullName(), method.getSignature(), line, error);
    }

    public ErrorMessage(String detector, String className, String elementName, String descriptor, int line, Throwable error) {
        this.detector = detector;
        this.className = className;
        this.elementName = elementName;
        this.descriptor = descriptor;
        this.line = line;
        this.error = error;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Class: ").append(className);
        if(line != -1)
            sb.append("\nLine: ").append(line);
        if(elementName != null)
            sb.append("\nElement: ").append(elementName).append(": ").append(descriptor);
        if(detector != null)
            sb.append("\nDetector: ").append(detector);
        sb.append("\nError: ").append(error.getMessage());
        for(StackTraceElement ste : error.getStackTrace()) {
            sb.append("\n\t").append(ste);
        }
        return sb.toString();
    }
}
