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

import com.strobel.decompiler.ast.Expression;

public class CodeBlock {
    public final Expression startExpr;
    public final int length;
    public final boolean isExceptional;

    public CodeBlock(Expression startExpr, int length, boolean isExceptional) {
        this.startExpr = startExpr;
        this.length = length;
        this.isExceptional = isExceptional;
    }
}