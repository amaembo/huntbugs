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
import java.util.stream.Stream;

import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

/**
 * @author shustkost
 *
 */
public class BackLinkAnnotator extends Annotator<Set<Expression>> {

    public BackLinkAnnotator() {
        super("backlink2", Collections.emptySet());
    }
    
    void annotate(Node node) {
        forExpressions(node, this::annotateBackLinks);
        forExpressions(node, this::fixTernary);
    }
    
    private void fixTernary(Expression expr) {
        for(Expression child : expr.getArguments()) {
            fixTernary(child);
        }
        if(expr.getCode() == AstCode.TernaryOp) {
            Expression left = expr.getArguments().get(1);
            Expression right = expr.getArguments().get(1);
            Set<Expression> links = get(expr);
            if(!(links instanceof HashSet))
                links = new HashSet<>(links);
            links.addAll(get(left));
            links.addAll(get(right));
            links.remove(expr);
            put(expr, links);
        }
    }

    private void annotateBackLinks(Expression expr) {
        for(Expression child : expr.getArguments()) {
            doLink(expr, child);
            annotateBackLinks(child);
        }
        Expression source = Inf.SOURCE.get(expr);
        if(source != null) {
            link(expr, source);
        }
    }

    private void link(Expression target, Expression source) {
        if (source.getCode() == Frame.PHI_TYPE || source.getCode() == Frame.UPDATE_TYPE) {
            source.getArguments().forEach(arg -> link(target, arg));
            return;
        }
        doLink(target, source);
    }

    private void doLink(Expression target, Expression source) {
        Set<Expression> set = get(source);
        if (set.isEmpty()) {
            put(source, Collections.singleton(target));
        } else {
            if (!(set instanceof HashSet)) {
                set = new HashSet<>(set);
                put(source, set);
            }
            set.add(target);
        }
    }

    public Set<Expression> findUsages(Expression input) {
        Set<Expression> set = get(input);
        return set instanceof HashSet ? Collections.unmodifiableSet(set) : set;
    }

    public Stream<Expression> findTransitiveUsages(Expression expr, boolean includePhi) {
        return findUsages(expr).stream().filter(includePhi ? x -> true : x -> !ValuesFlow.hasPhiSource(x))
            .flatMap(x -> {
                if(x.getCode() == AstCode.Store)
                    return null;
                if(x.getCode() == AstCode.Load)
                    return findTransitiveUsages(x, includePhi);
                if(x.getCode() == AstCode.TernaryOp && includePhi)
                    return findTransitiveUsages(x, includePhi);
                return Stream.of(x);
            });
    }
}
