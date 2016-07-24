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
package one.util.huntbugs.flow.etype;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.flow.etype.SingleType.What;
import one.util.huntbugs.util.YesNoMaybe;

/**
 * @author shustkost
 *
 */
public interface EType {
    public static final EType UNKNOWN = new EType() {
        @Override
        public String toString() {
            return "??";
        }

        @Override
        public EType negate() {
            return this;
        }

        @Override
        public YesNoMaybe is(TypeReference tr, boolean exact) {
            return YesNoMaybe.MAYBE;
        }
    };
    
    YesNoMaybe is(TypeReference tr, boolean exact);
    
    default EType shrinkConstraint(TypeReference tr, boolean exact) {
        return this;
    }

    EType negate();

    public static EType exact(TypeReference tr) {
        return SingleType.of(tr, What.EXACT);
    }

    public static EType subType(TypeReference tr) {
        return SingleType.of(tr, What.SUBTYPE);
    }

    public static EType or(EType t1, EType t2) {
        if (t1 == null)
            return t2;
        if (t2 == null)
            return t1;
        if (t1 == UNKNOWN || t2 == UNKNOWN)
            return UNKNOWN;
        if (t1.equals(t2))
            return t1;
        if (t1 instanceof OrType) {
            return ((OrType)t1).appendAny(t2);
        }
        if (t2 instanceof OrType) {
            return ((OrType)t2).appendAny(t1);
        }
        if (t1 instanceof ComplexType) {
            t1 = ((ComplexType)t1).reduce();
            if(t1 == UNKNOWN)
                return UNKNOWN;
        }
        if (t2 instanceof ComplexType) {
            t2 = ((ComplexType)t2).reduce();
            if(t2 == UNKNOWN)
                return UNKNOWN;
        }
        SingleType st1 = (SingleType)t1;
        SingleType st2 = (SingleType)t2;
        return new OrType(Collections.singleton(st1)).append(st2);
    }
    
    public static EType and(EType t1, EType t2) {
        if(t1 == null || t1 == UNKNOWN)
            return t2;
        if(t2 == null || t2 == UNKNOWN)
            return t1;
        if(t1.equals(t2))
            return t1;
        if (t1 instanceof AndType) {
            return ((AndType)t1).appendAny(t2);
        }
        if (t2 instanceof AndType) {
            return ((AndType)t2).appendAny(t1);
        }
        if (t1 instanceof OrType) {
            OrType ot1 = (OrType)t1;
            if(ot1.types.contains(t2))
                return ot1;
            if(t2 instanceof OrType) {
                Set<SingleType> commonTypes = new HashSet<>(ot1.types);
                OrType ot2 = (OrType) t2;
                commonTypes.retainAll(ot2.types);
                if(!commonTypes.isEmpty()) {
                    Set<SingleType> t1Types = new HashSet<>(ot1.types);
                    t1Types.removeAll(commonTypes);
                    if(t1Types.isEmpty())
                        return t2;
                    Set<SingleType> t2Types = new HashSet<>(ot2.types);
                    t2Types.removeAll(commonTypes);
                    if(t2Types.isEmpty())
                        return t1;
                    EType r1 = new OrType(t1Types).reduce();
                    EType r2 = new OrType(t2Types).reduce();
                    EType orend = r1 == UNKNOWN ? r2 : new AndType(Collections.singleton((SingleType)r1)).appendAny(r2);
                    if(orend != UNKNOWN)
                        return new OrType(commonTypes).appendAny(orend);
                }
            }
            t1 = ot1.reduce();
            if(t1 == UNKNOWN)
                return t2;
        }
        if (t2 instanceof OrType) {
            OrType ot2 = (OrType)t2;
            if(ot2.types.contains(t1))
                return ot2;
            t2 = ot2.reduce();
            if(t2 == UNKNOWN)
                return t1; // TODO: restore t1
        }
        SingleType st1 = (SingleType)t1;
        SingleType st2 = (SingleType)t2;
        return new AndType(Collections.singleton(st1)).append(st2);
    }
}
