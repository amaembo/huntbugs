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
package one.util.huntbugs.registry;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.warning.WarningType;

/**
 * @author Tagir Valeev
 *
 */
abstract class ElementContext {
    protected final Context ctx;
    protected final Detector detector;

    public ElementContext(Context ctx, Detector detector) {
        this.ctx = ctx;
        this.detector = detector;
    }

    /**
     * Report an internal analysis error. Alternatively detector may just throw any exception instead.
     * 
     * @param message message to report
     */
    abstract public void error(String message);

    protected WarningType resolveWarningType(String warning, int priority) {
        WarningType wt = detector.getWarningType(warning);
        if (wt == null) {
            error("Tries to report a warning of non-declared type: " + warning);
            return null;
        }
        if (priority < 0) {
            error("Tries to report a warning " + warning + " with negative priority " + priority);
            return null;
        }
        if (wt.getMaxScore() - priority < ctx.getOptions().minScore) {
            return null;
        }
        return wt;
    }
}
