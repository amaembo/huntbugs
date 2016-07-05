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
package one.util.huntbugs.testdata;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author shustkost
 *
 */
public class TestStaticFieldNonThreadSafe {
    @AssertWarning("StaticNotThreadSafeField")
    public static final Calendar CALENDAR = Calendar.getInstance();
    
    @AssertWarning(value="StaticNotThreadSafeField", maxScore=55)
    protected static final SimpleDateFormat SDF = new SimpleDateFormat("dd");
    
    @AssertNoWarning("*")
    private static final SimpleDateFormat privateSDF = new SimpleDateFormat("dd");

    @AssertNoWarning("*")
    private static final String date = privateSDF.format(new Date());
    
    private static final DateFormat usedSDF = new SimpleDateFormat("dd");
    
    static {
        System.out.println(date);
    }
    
    @AssertWarning("StaticNotThreadSafeFieldInvoke")
    public String format(Date date) {
        return usedSDF.format(date);
    }
}
