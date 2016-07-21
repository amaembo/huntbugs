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

import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.flow.etype.SingleType.What;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.util.YesNoMaybe;

/**
 * @author shustkost
 *
 */
public interface EType {
    public static final EType UNKNOWN = new EType() {
        @Override
        public YesNoMaybe isSubtypeOf(TypeReference tr) {
            return YesNoMaybe.MAYBE;
        }

        @Override
        public YesNoMaybe isExact(TypeReference tr) {
            return YesNoMaybe.MAYBE;
        }

        @Override
        public String toString() {
            return "??";
        }

        @Override
        public EType negate() {
            return this;
        }
    };

    YesNoMaybe isSubtypeOf(TypeReference superTr);

    YesNoMaybe isExact(TypeReference tr);

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
        SingleType st1 = (SingleType)t1;
        SingleType st2 = (SingleType)t2;
        if(!st1.what.isNegative() && !st2.what.isNegative()) {
            return subType(Types.mergeTypes(st1.tr, st2.tr));
        }
        return UNKNOWN;
    }
    
    public static EType and(EType t1, EType t2) {
        if(t1 == null || t1 == UNKNOWN)
            return t2;
        if(t2 == null || t2 == UNKNOWN)
            return t1;
        if(t1.equals(t2))
            return t1;
        SingleType st1 = (SingleType)t1;
        SingleType st2 = (SingleType)t2;
        if(st1.what == What.EXACT) {
            if(st2.what == What.EXACT)
                return UNKNOWN;
            return t1;
        }
        if(st2.what == What.EXACT)
            return t2;
        if(st1.what == What.SUBTYPE) {
            if(st2.what == What.SUBTYPE && Types.isInstance(st2.tr, st1.tr))
                return t2;
            return t1;
        }
        if(st2.what == What.SUBTYPE)
            return t2;
        return UNKNOWN;
    }
}
