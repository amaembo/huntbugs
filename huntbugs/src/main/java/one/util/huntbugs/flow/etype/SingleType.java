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

import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.util.Types;
import one.util.huntbugs.util.YesNoMaybe;

/**
 * @author shustkost
 *
 */
public class SingleType implements EType {
    enum What {
        EXACT, SUBTYPE, NOT, NOT_SUBTYPE;

        What negate() {
            switch (this) {
            case EXACT:
                return NOT;
            case SUBTYPE:
                return NOT_SUBTYPE;
            case NOT:
                return EXACT;
            case NOT_SUBTYPE:
                return SUBTYPE;
            default:
                throw new InternalError();
            }
        }

        boolean isNegative() {
            return this == NOT || this == NOT_SUBTYPE;
        }
    }

    final TypeReference tr;
    final boolean complete;
    final What what;

    static EType of(TypeReference tr, What what) {
        if (tr == null || tr.isPrimitive() || (what == What.SUBTYPE && Types.isObject(tr)))
            return UNKNOWN;
        TypeDefinition td = tr.resolve();
        if (td == null)
            return UNKNOWN;
        if (td.isFinal() || td.isPrimitive()) {
            if (what == What.SUBTYPE)
                what = What.EXACT;
            if (what == What.NOT)
                what = What.NOT_SUBTYPE;
        }
        TypeReference newTr = td;
        while (tr.isArray()) {
            tr = tr.getElementType();
            newTr = newTr.makeArrayType();
        }
        boolean complete = Types.hasCompleteHierarchy(td);
        return new SingleType(newTr, what, complete);
    }

    SingleType(TypeReference tr, What what, boolean complete) {
        this.tr = tr;
        this.what = what;
        this.complete = complete;
    }

    @Override
    public YesNoMaybe is(TypeReference other, boolean exact) {
        switch (what) {
        case EXACT:
            if (exact)
                return YesNoMaybe.of(tr.getInternalName().equals(other.getInternalName()));
            if (Types.isInstance(tr, other))
                return YesNoMaybe.YES;
            if (!complete)
                return YesNoMaybe.MAYBE;
            return YesNoMaybe.NO;
        case NOT:
            if (exact)
                return YesNoMaybe.of(!tr.getInternalName().equals(other.getInternalName()));
            return YesNoMaybe.MAYBE;
        case NOT_SUBTYPE:
            return Types.isInstance(other, tr) ? YesNoMaybe.NO : YesNoMaybe.MAYBE;
        case SUBTYPE: {
            if (exact)
                return Types.isInstance(other, tr) || !Types.hasCompleteHierarchy(other.resolve()) ? YesNoMaybe.MAYBE
                        : YesNoMaybe.NO;
            if (Types.isInstance(tr, other))
                return YesNoMaybe.YES;
            if (!complete || tr.resolve().isInterface())
                return YesNoMaybe.MAYBE;
            TypeDefinition superTd = other.resolve();
            if (superTd == null || superTd.isInterface() || Types.isInstance(other, tr) || !Types.hasCompleteHierarchy(
                superTd))
                return YesNoMaybe.MAYBE;
            return YesNoMaybe.NO;
        }
        default:
            throw new InternalError();
        }
    }

    @Override
    public EType negate() {
        return of(tr, what.negate());
    }

    @Override
    public String toString() {
        switch (what) {
        case EXACT:
            return tr.getFullName();
        case NOT:
            return "not " + tr.getFullName();
        case NOT_SUBTYPE:
            return "not subtype of " + tr.getFullName();
        case SUBTYPE:
            return "subtype of " + tr.getFullName();
        default:
            throw new InternalError();
        }
    }

    @Override
    public int hashCode() {
        return tr.getInternalName().hashCode() * 31 + what.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SingleType other = (SingleType) obj;
        return what == other.what && tr.isEquivalentTo(other.tr);
    }
}
