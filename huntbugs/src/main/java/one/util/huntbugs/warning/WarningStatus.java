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
package one.util.huntbugs.warning;

/**
 * Warning status suitable to compare new build with old build
 * 
 * @author Tagir Valeev
 */
public enum WarningStatus {
    /**
     * Default status: either comparison is not performed or bug appears both in new and old builds
     */
    DEFAULT,
    
    /**
     * Newly-discovered warning
     */
    ADDED,
    
    /**
     * Changed warning annotation (and probably priority)
     */
    CHANGED,
    
    /**
     * Warning is unchanged, but its score became higher
     */
    SCORE_RAISED,
    
    /**
     * Warning is unchanged, but its score became lower
     */
    SCORE_LOWERED,
    
    /**
     * Fixed warning (does not appear in the new build)
     */
    FIXED
}
