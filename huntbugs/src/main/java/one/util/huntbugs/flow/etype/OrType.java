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

import java.util.HashSet;
import java.util.Set;
import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.flow.etype.SingleType.What;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.util.YesNoMaybe;

/**
 * @author shustkost
 *
 */
public class OrType extends ComplexType {
    OrType(Set<SingleType> types) {
        super(types);
    }

    @Override
    public YesNoMaybe is(TypeReference tr, boolean exact) {
        boolean hasYes = false, hasNo = false, hasMaybe = false;
        for (EType type : types) {
            YesNoMaybe cur = type.is(tr, exact);
            switch (cur) {
            case YES:
                hasYes = true;
                break;
            case NO:
                hasNo = true;
                break;
            case MAYBE:
                hasMaybe = true;
                break;
            default:
                throw new InternalError();
            }
        }
        if (hasMaybe || hasYes && hasNo)
            return YesNoMaybe.MAYBE;
        if (hasYes)
            return YesNoMaybe.YES;
        if (hasNo)
            return YesNoMaybe.NO;
        return YesNoMaybe.MAYBE;
    }

    @Override
    public YesNoMaybe isArray() {
        boolean hasYes = false, hasNo = false, hasMaybe = false;
        for (EType type : types) {
            YesNoMaybe cur = type.isArray();
            switch (cur) {
            case YES:
                hasYes = true;
                break;
            case NO:
                hasNo = true;
                break;
            case MAYBE:
                hasMaybe = true;
                break;
            default:
                throw new InternalError();
            }
        }
        if (hasMaybe || hasYes && hasNo)
            return YesNoMaybe.MAYBE;
        if (hasYes)
            return YesNoMaybe.YES;
        if (hasNo)
            return YesNoMaybe.NO;
        return YesNoMaybe.MAYBE;
    }
    
    @Override
    public EType negate() {
        Set<SingleType> newTypes = new HashSet<>();
        for (SingleType type : types) {
            EType neg = type.negate();
            if (neg instanceof SingleType)
                newTypes.add((SingleType) neg);
            else if (neg != UNKNOWN)
                throw new IllegalStateException("Unexpected type: " + type);
        }
        return AndType.of(newTypes);
    }

    @Override
    EType reduce() {
        SingleType result = null; 
        for(SingleType type : types) {
            if(result == null)
                result = type;
            else {
                if(result.what.isNegative() || type.what.isNegative())
                    return UNKNOWN;
                EType newType = SingleType.of(Types.mergeTypes(result.tr, type.tr), SingleType.What.SUBTYPE);
                if(!(newType instanceof SingleType))
                    return UNKNOWN;
                result = (SingleType) newType;
            }
        }
        return result;
    }

    @Override
    EType append(SingleType st) {
        if(types.contains(st))
            return types.size() == 1 ? types.iterator().next() : this;
        if(types.contains(st.negate()))
            return UNKNOWN;
        if(st.what == What.EXACT) {
            if(types.stream().anyMatch(t -> t.is(st.tr, true) == YesNoMaybe.YES))
                return this;
        }
        Set<SingleType> newTypes = new HashSet<>(types);
        newTypes.add(st);
        return new OrType(newTypes);
    }
    
    @Override
    EType appendAny(EType type) {
        if(type == UNKNOWN)
            return UNKNOWN;
        if(type instanceof SingleType)
            return append((SingleType) type);
        if(type instanceof OrType) {
            OrType result = (OrType) type;
            for(SingleType t : types) {
                EType newResult = result.append(t);
                if(newResult == UNKNOWN)
                    return UNKNOWN;
                result = (OrType)newResult;
            }
            return result;
        }
        if(type instanceof ComplexType) {
            return appendAny(((ComplexType)type).reduce());
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return toString(" or ");
    }
}
