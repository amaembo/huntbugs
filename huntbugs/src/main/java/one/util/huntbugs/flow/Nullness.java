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

import com.strobel.decompiler.ast.Expression;

/**
 * @author shustkost
 *
 */
public class Nullness {
    public static enum NullState {
        UNKNOWN, NONNULL, NONNULL_DEREF, NONNULL_CHECKED, NULL, NULL_EXCEPTIONAL, NULLABLE;
    }

    public static final Nullness UNKNOWN = new Nullness(NullState.UNKNOWN);
    public static final Nullness NONNULL = new Nullness(NullState.NONNULL);
    public static final Nullness NONNULL_DEREF = new Nullness(NullState.NONNULL_DEREF);
    public static final Nullness NONNULL_CHECKED = new Nullness(NullState.NONNULL_CHECKED);

    private final NullState state;
    private final Set<Expression> expressions;

    private Nullness(NullState state) {
        this.state = state;
        this.expressions = Collections.emptySet();
    }

    private Nullness(NullState state, Set<Expression> exprs) {
        this.state = state;
        this.expressions = exprs;
    }

    private Nullness(NullState state, Set<Expression> exprs1, Set<Expression> exprs2) {
        this.state = state;
        if (exprs1.isEmpty()) {
            this.expressions = exprs2;
        } else if (exprs2.isEmpty()) {
            this.expressions = exprs1;
        } else {
            this.expressions = new HashSet<>(exprs1);
            this.expressions.addAll(exprs2);
        }
    }
    
    public NullState state() {
        return state;
    }
    
    public Stream<Expression> expressions() {
        return expressions.stream();
    }
    
    public boolean isNull() {
        return state == NullState.NULL;
    }

    Nullness or(Nullness other) {
        if (this == other || other == null)
            return this;
        if (this == UNKNOWN || other == UNKNOWN)
            return UNKNOWN;
        if (this.state == other.state)
            return new Nullness(state, this.expressions, other.expressions);
        if (state == NullState.NULLABLE || state == NullState.NULL_EXCEPTIONAL)
            return other.isNonNull() ? UNKNOWN : new Nullness(NullState.NULLABLE, expressions, other.expressions);
        if (other.state == NullState.NULLABLE || other.state == NullState.NULL_EXCEPTIONAL)
            return this.isNonNull() ? UNKNOWN : new Nullness(NullState.NULLABLE, expressions, other.expressions);
        if (this.isNull() || other.isNull())
            return new Nullness(NullState.NULLABLE, this.expressions, other.expressions);
        return NONNULL;
    }
    
    Nullness orExceptional(Nullness exceptional) {
        if (exceptional.isNull() && !isNull())
            return new Nullness(NullState.NULL_EXCEPTIONAL, exceptional.expressions);
        return or(exceptional);
    }

    Nullness unknownToNull() {
        return this == UNKNOWN ? null : this;
    }

    Nullness and(Nullness other) {
        if (this == other || other == UNKNOWN || other == null)
            return this;
        if (this == UNKNOWN)
            return other;
        if (this.isNull())
            return other.isNonNull() ? UNKNOWN : this;
        if (other.isNull())
            return isNonNull() ? UNKNOWN : other;
        if (this == NONNULL_DEREF || other == NONNULL_DEREF)
            return NONNULL_DEREF;
        if (this == NONNULL_CHECKED || other == NONNULL_CHECKED)
            return NONNULL_CHECKED;
        if (this == NONNULL || other == NONNULL)
            return NONNULL;
        if (this.state == NullState.NULL_EXCEPTIONAL || other.state == NullState.NULL_EXCEPTIONAL)
            return new Nullness(NullState.NULL_EXCEPTIONAL, expressions, other.expressions);
        return other.state == NullState.NULLABLE ? other : new Nullness(NullState.NULLABLE, other.expressions);
    }

    public boolean isNonNull() {
        return this == NONNULL || this == NONNULL_DEREF || this == NONNULL_CHECKED;
    }

    public static Nullness nullAt(Expression expr) {
        return new Nullness(NullState.NULL, Collections.singleton(expr));
    }

    @Override
    public int hashCode() {
        return state.hashCode() * 31 + expressions.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Nullness other = (Nullness) obj;
        return state == other.state && expressions.equals(other.expressions);
    }

    @Override
    public String toString() {
        return state.toString() + (expressions.isEmpty() ? "" : expressions);
    }
}
