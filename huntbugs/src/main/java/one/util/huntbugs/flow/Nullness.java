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
import java.util.HashMap;
import java.util.Map;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.CFG.EdgeType;
import one.util.huntbugs.flow.CFG.SearchResult;

/**
 * @author Tagir Valeev
 */
public class Nullness {
    public static enum NullState {
        UNKNOWN, NONNULL, NONNULL_DEREF, NONNULL_CHECKED, NULL, NULLABLE, NULL_EXCEPTIONAL, EXPLICIT_THROW;
        
        NullState or(NullState other) {
            if(this == other)
                return this;
            if(this == NULL || other == NULL || this == NULLABLE || other == NULLABLE)
                return NULLABLE;
            if(this != UNKNOWN && other != UNKNOWN)
                return NONNULL;
            return UNKNOWN;
        }
        
        public boolean isNull() {
            return this == NULL;
        }

        public boolean isNonNull() {
            switch(this) {
            case NONNULL:
            case NONNULL_CHECKED:
            case NONNULL_DEREF:
                return true;
            default:
                return false;
            }
        }
    }
    
    static final Expression ENTRY_EXPRESSION = new Expression(AstCode.Nop, null, -1); 

    public static final Nullness UNKNOWN = new Nullness(Collections.emptyMap());

    static final Nullness UNKNOWN_AT_ENTRY = createAt(ENTRY_EXPRESSION, NullState.UNKNOWN);

    private final Map<Expression, NullState> expressions;

    private Nullness(Map<Expression, NullState> expressions) {
        this.expressions = expressions;
    }
    
    private NullState state() {
        NullState n = null;
        for(NullState nn : expressions.values()) {
            if(n == null)
                n = nn;
            else if(n != nn)
                return null;
        }
        return n;
    }

    public NullState stateAt(CFG cfg, Expression target) {
        if(this == UNKNOWN)
            return NullState.UNKNOWN;
        NullState state = state();
        if(state != null && state != NullState.NULLABLE)
            return state;
        boolean hasNullable = false, hasUnknown = false, hasNonnull = false;
        for(NullState nn : expressions.values()) {
            switch(nn) {
            case NONNULL:
            case NONNULL_CHECKED:
            case NONNULL_DEREF:
                hasNonnull = true;
                break;
            case NULL:
            case NULLABLE:
                hasNullable = true;
                break;
            default:
                hasUnknown = true;
                break;
            }
        }
        if(hasNonnull && !hasNullable && !hasUnknown)
            return NullState.NONNULL;
//        if(hasNullable && !hasNonnull && !hasUnknown)
//            return NullState.NULLABLE;
        if(cfg == null || (hasUnknown && !hasNullable))
            return NullState.UNKNOWN;
        SearchResult<NullState> sr = cfg.graphSearch(new NullGraphSearch(target));
        if(sr.atExit() != null || sr.atFail() == NullState.EXPLICIT_THROW)
            return NullState.UNKNOWN;
        return sr.atExpression(target);
    }
    
    class NullGraphSearch implements GraphSearch<NullState> {
        private final Expression target;

        public NullGraphSearch(Expression target) {
            this.target = target;
        }

        @Override
        public NullState markStart(Expression expr, boolean isEntry) {
            NullState state = expressions.get(expr);
            return (state == null && isEntry) ? expressions.get(ENTRY_EXPRESSION) : state;
        }

        @Override
        public NullState transfer(NullState orig, Expression from, EdgeType edge, Expression to) {
            NullState state = expressions.get(to);
            if(state != null)
                return state;
            if(from == target)
                return null;
            if((orig == NullState.NULL || orig == NullState.NULLABLE) && edge == EdgeType.FAIL) {
                if(to == null && from.getCode() == AstCode.AThrow)
                    return NullState.EXPLICIT_THROW;
                return NullState.NULL_EXCEPTIONAL;
            }
            return orig;
        }

        @Override
        public NullState merge(NullState f1, NullState f2) {
            if(f1 == null || f1 == f2)
                return f2;
            if(f2 == null)
                return f1;
            if(f1 == NullState.EXPLICIT_THROW || f2 == NullState.EXPLICIT_THROW)
                return NullState.EXPLICIT_THROW;
            if(f1.isNonNull() && f2.isNonNull())
                return NullState.NONNULL;
            if(f1 == NullState.NULL || f1 == NullState.NULLABLE || f2 == NullState.NULL || f2 == NullState.NULLABLE)
                return NullState.NULLABLE;
            if(f1 == NullState.NULL_EXCEPTIONAL || f2 == NullState.NULL_EXCEPTIONAL)
                return NullState.NULL_EXCEPTIONAL;
            return NullState.UNKNOWN;
        }
        
    }
    
    public boolean isNull() {
        return state() == NullState.NULL;
    }

    Nullness or(Nullness other) {
        if (this == other)
            return this;
        Map<Expression, NullState> newExprs = new HashMap<>(expressions);
        other.expressions.forEach((expr, ns) -> newExprs.merge(expr, ns, NullState::or));
        return new Nullness(newExprs);
    }
    
    Nullness unknownToNull() {
        return this == UNKNOWN ? null : this;
    }

    public boolean isNonNull() {
        NullState state = state();
        return state != null && state.isNonNull();
    }

    public static Nullness nullAt(Expression expr) {
        return createAt(expr, NullState.NULL);
    }
    
    public static Nullness createAt(Expression expr, NullState state) {
        return new Nullness(Collections.singletonMap(expr, state));
    }

    @Override
    public int hashCode() {
        return expressions.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Nullness other = (Nullness) obj;
        return expressions.equals(other.expressions);
    }

    @Override
    public String toString() {
        return expressions.toString();
    }
}
