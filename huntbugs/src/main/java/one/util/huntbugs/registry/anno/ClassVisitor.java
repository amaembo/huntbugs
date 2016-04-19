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
package one.util.huntbugs.registry.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import one.util.huntbugs.registry.ClassContext;

import com.strobel.assembler.metadata.TypeDefinition;

/**
 * Method in detector class which called for every visited class.
 * 
 * <p>
 * Allowed parameter types (no repeats): {@link ClassContext},
 * {@link TypeDefinition} or any registered databases (see {@link TypeDatabase},
 * {@link TypeDatabaseItem})
 * 
 * <p>
 * May return boolean or void. If returns false, any other visitors (e.g.
 * {@link MethodVisitor}, {@link AstVisitor}) defined in this detector will be
 * skipped for this class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ClassVisitor {

}
