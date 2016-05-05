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
package one.util.huntbugs.warning.rule;

import java.util.regex.Pattern;

import one.util.huntbugs.warning.WarningType;

/**
 * @author lan
 *
 */
public class RegexRule implements Rule {
    private final Pattern regex;
    private final int adjustment;
    
    public RegexRule(String regex, int adjustment) {
        this.regex = Pattern.compile(regex);
        this.adjustment = adjustment;
    }

    @Override
    public WarningType adjust(WarningType wt) {
        if(regex.matcher(wt.getName()).matches())
            return new WarningType(wt.getCategory(), wt.getName(), wt.getMaxScore()+adjustment);
        return wt;
    }
}
