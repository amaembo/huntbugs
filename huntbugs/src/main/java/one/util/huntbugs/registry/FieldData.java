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
package one.util.huntbugs.registry;

import com.strobel.assembler.metadata.FieldDefinition;

/**
 * @author Tagir Valeev
 *
 */
final class FieldData {
    final FieldDefinition fd;

    public FieldData(FieldDefinition fd) {
        this.fd = fd;
    }

    @Override
    public String toString() {
        return fd.getDeclaringType() + "." + fd.getName()+" : "+fd.getSignature();
    }
}
