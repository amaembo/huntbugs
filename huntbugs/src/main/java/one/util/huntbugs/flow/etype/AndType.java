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
public class AndType extends ComplexType {
    AndType(Set<SingleType> types) {
        super(types);
    }
    
    static EType of(Set<SingleType> types) {
        if(types.isEmpty())
            return UNKNOWN;
        if(types.size() == 1)
            return types.iterator().next();
        return new AndType(types);
    }

    @Override
    public YesNoMaybe is(TypeReference tr, boolean exact) {
        boolean hasYes = false, hasNo = false;
        for (EType type : types) {
            switch (type.is(tr, exact)) {
            case YES:
                hasYes = true;
                break;
            case NO:
                hasNo = true;
                break;
            default:
            }
        }
        if (hasYes && hasNo)
            return YesNoMaybe.MAYBE;
        if (hasYes)
            return YesNoMaybe.YES;
        if (hasNo)
            return YesNoMaybe.NO;
        return YesNoMaybe.MAYBE;
    }

    @Override
    public YesNoMaybe isArray() {
        boolean hasYes = false, hasNo = false;
        for (EType type : types) {
            switch (type.isArray()) {
            case YES:
                hasYes = true;
                break;
            case NO:
                hasNo = true;
                break;
            default:
            }
        }
        if (hasYes && hasNo)
            return YesNoMaybe.MAYBE;
        if (hasYes)
            return YesNoMaybe.YES;
        if (hasNo)
            return YesNoMaybe.NO;
        return YesNoMaybe.MAYBE;
    }

    @Override
    public EType shrinkConstraint(TypeReference tr, boolean exact) {
        Set<SingleType> yes = new HashSet<>(), no = new HashSet<>();
        for (SingleType type : types) {
            switch (type.is(tr, exact)) {
            case YES:
                yes.add(type);
                break;
            case NO:
                no.add(type);
                break;
            default:
            }
        }
        if (!yes.isEmpty() && !no.isEmpty())
            return this;
        if (!yes.isEmpty())
            return of(yes);
        if (!no.isEmpty())
            return of(no);
        return this;
    }

    @Override
    public EType negate() {
        Set<SingleType> newTypes = new HashSet<>();
        for (SingleType type : types) {
            EType neg = type.negate();
            if (neg instanceof SingleType)
                newTypes.add((SingleType) neg);
            else if (neg == UNKNOWN) {
                return UNKNOWN;
            } else
                throw new IllegalStateException("Unexpected type: " + type);
        }
        return new OrType(newTypes);
    }

    @Override
    EType reduce() {
        SingleType result = null; 
        for(SingleType type : types) {
            if(result == null)
                result = type;
            else {
                if(result.what == What.EXACT) {
                    if(type.what == What.EXACT)
                        return UNKNOWN;
                    continue;
                }
                if(type.what == What.EXACT) {
                    result = type;
                    continue;
                }
                if(result.what == What.SUBTYPE) {
                    if(type.what == What.SUBTYPE && Types.isInstance(type.tr, result.tr))
                        result = type;
                    continue;
                }
                if(type.what == What.SUBTYPE) {
                    result = type;
                    continue;
                }
                return UNKNOWN;
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
            if(types.stream().anyMatch(t -> t.is(st.tr, true) == YesNoMaybe.NO))
                return UNKNOWN;
            return st;
        }
        if(types.size() == 1) {
            SingleType cur = types.iterator().next();
            if(cur.what == What.EXACT) {
                return st.is(cur.tr, true) == YesNoMaybe.NO ? UNKNOWN : cur;
            }
            if(cur.what == What.SUBTYPE && st.what == What.SUBTYPE) {
                if(cur.is(st.tr, false) == YesNoMaybe.YES)
                    return cur;
                if(st.is(cur.tr, false) == YesNoMaybe.YES)
                    return st;
            }
        }
        Set<SingleType> newTypes = new HashSet<>(types);
        newTypes.add(st);
        return new AndType(newTypes);
    }
    
    @Override
    EType appendAny(EType type) {
        if(type == UNKNOWN)
            return types.size() == 1 ? types.iterator().next() : this;
        if(type instanceof SingleType)
            return append((SingleType) type);
        if(type instanceof AndType) {
            AndType result = (AndType) type;
            for(SingleType t : types) {
                EType newResult = result.append(t);
                if(newResult == UNKNOWN)
                    return UNKNOWN;
                result = (AndType)newResult;
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
        return toString(" and ");
    }
}
