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

import one.util.huntbugs.warning.WarningType;

/**
 * @author lan
 *
 */
public class CategoryRule implements Rule {
    private final int adjustment;
    private final String category;

    public CategoryRule(String category, int adjustment) {
        this.category = category;
        this.adjustment = adjustment;
    }

    @Override
    public WarningType adjust(WarningType wt) {
        if(wt.getCategory().equals(category))
            return new WarningType(category, wt.getName(), wt.getMaxScore()+adjustment);
        return wt;
    }
}
