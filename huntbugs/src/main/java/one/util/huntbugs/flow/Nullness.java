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

/**
 * @author shustkost
 *
 */
public enum Nullness {
    UNKNOWN, NULLABLE, NULL, NULL_EXCEPTIONAL, NONNULL, NONNULL_DEREF, NONNULL_CHECKED;
    
    Nullness or(Nullness other) {
        if(this == other || other == null)
            return this;
        if(this == UNKNOWN || other == UNKNOWN)
            return UNKNOWN;
        if(this == NULLABLE || this == NULL_EXCEPTIONAL)
            return other.isNonNull() ? UNKNOWN : NULLABLE;
        if(other == NULLABLE || other == NULL_EXCEPTIONAL)
            return this.isNonNull() ? UNKNOWN : NULLABLE;
        if(this == NULL || other == NULL)
            return UNKNOWN;
        return NONNULL;
    }
    
    Nullness orExceptional(Nullness exceptional) {
        if (exceptional == NULL && this != NULL)
            return NULL_EXCEPTIONAL;
        return or(exceptional);
    }
    
    Nullness unknownToNull() {
        return this == UNKNOWN ? null : this;
    }

    Nullness and(Nullness other) {
        if(this == other || other == UNKNOWN || other == null)
            return this;
        if(this == UNKNOWN)
            return other;
        if(this == NULL)
            return other.isNonNull() ? UNKNOWN : this;
        if(other == NULL)
            return isNonNull() ? UNKNOWN : other;
        if(this == NONNULL_DEREF || other == NONNULL_DEREF)
            return NONNULL_DEREF;
        if(this == NONNULL_CHECKED || other == NONNULL_CHECKED)
            return NONNULL_CHECKED;
        if(this == NONNULL || other == NONNULL)
            return NONNULL;
        if(this == NULL_EXCEPTIONAL || other == NULL_EXCEPTIONAL)
            return NULL_EXCEPTIONAL;
        return NULLABLE;
    }

    public boolean isNonNull() {
        return this == NONNULL || this == NONNULL_DEREF || this == NONNULL_CHECKED;
    }
}
