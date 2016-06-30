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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Comparator;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestSerializationIdiom implements Serializable {
    private static final long serialVersionUID = 1L;

    @AssertWarning("ComparatorIsNotSerializable")
    public class MyComparator implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            return o1.toLowerCase().compareTo(o2.toLowerCase());
        }

        @AssertNoWarning("SerializationMethodMustBePrivate")
        void readObjectNoData() {
        }
    }
    
    @SuppressWarnings("serial")
    public static class NonStaticUid implements Serializable {
        @AssertWarning("SerialVersionUidNotStatic")
        final long serialVersionUID = 1L;
    }
    
    @SuppressWarnings("serial")
    public static class NonFinalUid implements Serializable {
        @AssertWarning("SerialVersionUidNotFinal")
        static long serialVersionUID = 1L;
    }
    
    @SuppressWarnings("serial")
    public static class NonLongUid implements Serializable {
        @AssertWarning("SerialVersionUidNotLong")
        static final int serialVersionUID = 1;
    }
    
    @AssertWarning("SerializationMethodMustBePrivate")
    void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
    }
    
    @AssertWarning("SerializationMethodMustBePrivate")
    public void readObjectNoData() {
    }
    
    @AssertWarning("SerializationMethodMustBePrivate")
    protected void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject();
    }
}
