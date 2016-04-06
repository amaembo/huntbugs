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
package one.util.huntbugs.testdata;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestInvalidMinMax {
    @AssertWarning(type = "InvalidMinMax")
    public int checkBounds(int rawInput) {
        return Math.min(0, Math.max(100, rawInput));
    }

    @AssertWarning(type = "InvalidMinMax")
    public int checkBounds2(int rawInput) {
        return Math.min(0, Math.max(rawInput, 100));
    }

    @AssertWarning(type = "InvalidMinMax")
    public int checkBounds3(int rawInput) {
        return Math.min(Math.max(rawInput, 100), 0);
    }

    @AssertWarning(type = "InvalidMinMax")
    public int checkBounds4(int rawInput) {
        return Math.min(Math.max(100, rawInput), 0);
    }

    @AssertWarning(type = "InvalidMinMax")
    public int checkBounds5(int rawInput) {
        return Math.max(Math.min(0, rawInput), 100);
    }

    @AssertWarning(type = "InvalidMinMax")
    public int checkBounds6(int rawInput) {
        rawInput = Math.min(0, rawInput);
        rawInput = Math.max(100, rawInput);
        return rawInput;
    }

    @AssertWarning(type = "InvalidMinMax")
    public int checkWithVars(int rawInput) {
        int min = 0;
        int max = 100;
        if(rawInput > 50) {
            System.out.println("Phew...");
        }
        rawInput = Math.min(min, rawInput);
        rawInput = Math.max(max, rawInput);
        return rawInput;
    }
    
    @AssertWarning(type = "InvalidMinMax")
    public int getScore(int totalCount, int failCount, double scaleFactor) {
        // Based on
        // https://github.com/marksinclair/junit-plugin/commit/c0dc11e08923edd23cee90962da638e4a7eb47d5
        int score = (totalCount == 0) ? 100 : (int) (100.0 * Math.max(1.0, Math.min(0.0, 1.0
            - (scaleFactor * failCount) / totalCount)));
        return score;
    }

    @AssertWarning(type = "InvalidMinMax")
    public long checkBounds(long rawInput) {
        return Math.min(0, Math.max(100, rawInput));
    }

    @AssertWarning(type = "InvalidMinMax")
    public float checkBounds(float rawInput) {
        return Math.min(0, Math.max(100, rawInput));
    }

    @AssertWarning(type = "InvalidMinMax")
    public double checkBounds(double rawInput) {
        return Math.min(0, Math.max(100, rawInput));
    }

    @AssertNoWarning(type = "InvalidMinMax")
    public int checkBoundsCorrect(int rawInput) {
        return Math.min(100, Math.max(0, rawInput));
    }

    @AssertNoWarning(type = "InvalidMinMax")
    public int checkBoundsCorrect2(int rawInput) {
        return Math.max(0, Math.min(100, rawInput));
    }

    @AssertNoWarning(type = "InvalidMinMax")
    public int checkBoundsCorrect3(int rawInput) {
        rawInput = Math.min(100, rawInput);
        rawInput = Math.max(0, rawInput);
        return rawInput;
    }

    @AssertNoWarning(type = "InvalidMinMax")
    public long checkBoundsCorrect(long rawInput) {
        return Math.min(100, Math.max(0, rawInput));
    }

    @AssertNoWarning(type = "InvalidMinMax")
    public float checkBoundsCorrect(float rawInput) {
        return Math.min(100, Math.max(0, rawInput));
    }

    @AssertNoWarning(type = "InvalidMinMax")
    public double checkBoundsCorrect(double rawInput) {
        return Math.min(100, Math.max(0, rawInput));
    }
}
