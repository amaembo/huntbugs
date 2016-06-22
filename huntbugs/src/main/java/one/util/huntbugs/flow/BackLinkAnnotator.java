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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.strobel.decompiler.ast.Expression;

/**
 * @author shustkost
 *
 */
public class BackLinkAnnotator extends Annotator<Set<Expression>> {

    public BackLinkAnnotator() {
        super("backlink", Collections.emptySet());
    }

    void link(Expression target, Expression source) {
        if (source.getCode() == Frame.PHI_TYPE || source.getCode() == Frame.UPDATE_TYPE) {
            source.getArguments().forEach(arg -> link(target, arg));
            return;
        }
        Set<Expression> set = get(source);
        if (!(set instanceof HashSet)) {
            set = new HashSet<>(set);
            put(source, set);
        }
        set.add(target);
    }
}
