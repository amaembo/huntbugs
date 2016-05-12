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

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestEqualsContract {

	@Override
	@AssertWarning(type="EqualsReturnsFalse", minScore = 40)
	public boolean equals(Object obj) {
		return false;
	}
	
	static class NonPublic {
	    @Override
	    @AssertWarning(type="EqualsReturnsTrue", maxScore = 30)
	    public boolean equals(Object obj) {
	        return true;
	    }
	}
	
	public static final class Final {
	    @Override
	    @AssertWarning(type="EqualsReturnsFalse", minScore = 35, maxScore = 45)
	    public boolean equals(Object obj) {
	        return false;
	    }
	}

	public static final class ClassName {
	    public int i;
	    
	    @Override
	    @AssertWarning(type="EqualsClassNames")
	    public boolean equals(Object obj) {
	        if(!"one.util.huntbugs.testdata.TestEqualsContract.ClassName".equals(obj.getClass().getName()))
	            return false;
	        ClassName other = (ClassName)obj;
	        return i == other.i;
	    }
	}
	
	@AssertWarning(type="EqualsOther")
	public static class OtherEquals {
	    public boolean equals(TestEqualsContract other) {
	        return other != null;
	    }
	}
	
	@AssertWarning(type="EqualsSelf")
	public static class SelfEquals {
	    public boolean equals(SelfEquals other) {
	        return other == this;
	    }
	}
	
	@AssertWarning(type="EqualsEnum")
	@AssertNoWarning(type="EqualsSelf")
	public static enum EnumEquals {
	    A,B,C;
	    
	    public boolean equals(EnumEquals other) {
	        return other == this;
	    }
	}
	
	@AssertNoWarning(type="*")
	public static class EqualsOk {
	    public boolean equals(EqualsOk other, int check) {
	        return other != this && check > 2;
	    }
	}
}
