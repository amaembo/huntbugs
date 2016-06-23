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
package one.util.huntbugs.flow;

/**
 * Publically available annotators
 * 
 * @author Tagir Valeev
 */
public class Inf {
    /**
     * Annotator which can determine the sources of given expression 
     */
    public static final SourceAnnotator SOURCE = new SourceAnnotator();
    
    /**
     * Annotator which can determine the statically known constant value of given expression (if any)
     */
    public static final ConstAnnotator CONST = new ConstAnnotator();
    
    /**
     * Annotator which can find the usages of given expression
     */
    public static final BackLinkAnnotator BACKLINK = new BackLinkAnnotator();
    
    /**
     * Annotator which can determine the purity of given expression
     */
    public static final PurityAnnotator PURITY = new PurityAnnotator();
}
