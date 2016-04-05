/*
 * Copyright 2015, 2016 Tagir Valeev
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
package one.util.huntbugs.assertions;

import java.util.Locale;

import one.util.huntbugs.warning.Warning;

/**
 * @author lan
 *
 */
class AssertionData {
    private final boolean hasWarning;
    private final boolean isPrefix;
    private final String type;
    private final int minRank, maxRank;
    
    enum Status {
        PASS, FAIL, NONE
    }

    AssertionData(boolean hasWarning, String type, int minRank, int maxRank) {
        super();
        this.hasWarning = hasWarning;
        if(type.endsWith("*")) {
            this.isPrefix = true;
            this.type = type.substring(0, type.length()-1);
        } else {
            this.isPrefix = false;
            this.type = type;
        }
        this.minRank = minRank;
        this.maxRank = maxRank;
    }
    
    
    Status check(Warning warning) {
        boolean typeMatches;
        if(isPrefix)
            typeMatches = warning.getType().getName().toLowerCase(Locale.ENGLISH).startsWith(type.toLowerCase(Locale.ENGLISH));
        else
            typeMatches = warning.getType().getName().equalsIgnoreCase(type);
        if(!typeMatches)
            return Status.NONE;
        if(!hasWarning)
            return Status.FAIL;
        int rank = warning.getRank();
        if(rank < minRank || rank > maxRank)
            return Status.FAIL;
        return Status.PASS;
    }
    
    Status finalStatus() {
        return hasWarning ? Status.FAIL : Status.PASS;
    }

    @Override
    public String toString() {
        if(hasWarning)
            return "AssertWarning(type = "+type+(isPrefix?"*":"")+"; rank = "+minRank+".."+maxRank+")";
        else
            return "AssertNoWarning(type = "+type+(isPrefix?"*":"")+")";
    }
}
