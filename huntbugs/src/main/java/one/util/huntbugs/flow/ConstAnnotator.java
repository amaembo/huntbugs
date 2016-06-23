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

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.strobel.decompiler.ast.Expression;

/**
 * @author shustkost
 *
 */
public class ConstAnnotator extends Annotator<Object> {
    static final Object UNKNOWN_VALUE = new Object();

    ConstAnnotator() {
        super("value", null);
    }
    
    public Object getValue(Expression input) {
        Object value = get(input);
        return value == ConstAnnotator.UNKNOWN_VALUE ? null : value;
    }
    
    public boolean isConst(Expression input, Object constant) {
        return constant.equals(get(input));
    }

    void storeValue(Expression expr, Object val) {
        Object curValue = get(expr);
        if (Objects.equals(val, curValue) || curValue == UNKNOWN_VALUE)
            return;
        put(expr, curValue == null ? val : UNKNOWN_VALUE);
    }
    
    <A> void processUnaryOp(Expression expr, Class<A> type, Function<A, ?> op) {
        if (expr.getArguments().size() != 1)
            return;
        Object arg = get(expr.getArguments().get(0));
        if (arg == UNKNOWN_VALUE) {
            storeValue(expr, arg);
            return;
        }
        if (!type.isInstance(arg)) {
            if(type == Boolean.class && arg instanceof Integer)
                arg = Integer.valueOf(1).equals(arg);
            else
                return;
        }
        Object result = op.apply(type.cast(arg));
        storeValue(expr, result);
    }
    
    <A, B> void processBinaryOp(Expression expr, Class<A> leftType, Class<B> rightType, BiFunction<A, B, ?> op) {
        if (expr.getArguments().size() != 2)
            return;
        Object left = get(expr.getArguments().get(0));
        if (left == UNKNOWN_VALUE) {
            storeValue(expr, left);
            return;
        }
        if (!leftType.isInstance(left))
            return;
        Object right = get(expr.getArguments().get(1));
        if (right == UNKNOWN_VALUE) {
            storeValue(expr, right);
            return;
        }
        if (!rightType.isInstance(right))
            return;
        Object result = UNKNOWN_VALUE;
        try {
            result = op.apply(leftType.cast(left), rightType.cast(right));
        } catch (Exception e) {
            // ignore
        }
        storeValue(expr, result);
    }
}
