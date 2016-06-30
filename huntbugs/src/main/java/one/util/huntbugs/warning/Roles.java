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

import one.util.huntbugs.warning.Role.Count;
import one.util.huntbugs.warning.Role.ExpressionRole;
import one.util.huntbugs.warning.Role.LocationRole;
import one.util.huntbugs.warning.Role.MemberRole;
import one.util.huntbugs.warning.Role.NumberRole;
import one.util.huntbugs.warning.Role.OperationRole;
import one.util.huntbugs.warning.Role.StringRole;
import one.util.huntbugs.warning.Role.TypeRole;

/**
 * Predefined warning annotation roles.
 * 
 * You may also create custom role using {@code forName} static methods in corresponding {@link Role} subclass,
 * e.g. {@code StringRole.forName("CUSTOM_STRING")}.
 * 
 * @author Tagir Valeev
 */
public final class Roles {
    /**
     * Class descriptor associated with the warning
     */
    public static final TypeRole TYPE = new TypeRole("TYPE", Count.ONE);
    /**
     * Source file name (like "Test.java")
     */
    public static final StringRole FILE = new StringRole("FILE", Count.ZERO_ONE);
    /**
     * Method associated with the warning (where warning appears)
     */
    public static final MemberRole METHOD = new MemberRole("METHOD", Count.ZERO_ONE);
    /**
     * Field descriptor associated with the warning
     */
    public static final MemberRole FIELD = new MemberRole("FIELD", Count.ZERO_ONE);
    /**
     * Source code location where warning appears
     */
    public static final LocationRole LOCATION = new LocationRole("LOCATION", Count.ZERO_ONE);
    /**
     * Location where expression is used
     */
    public static final LocationRole USED_AT = new LocationRole("USED_AT", Count.ZERO_ONE);
    /**
     * Another location where the same warning appears (within the same method)
     */
    public static final LocationRole ANOTHER_INSTANCE = new LocationRole("ANOTHER_INSTANCE");
    /**
     * Called method which is associated with the warning
     */
    public static final MemberRole CALLED_METHOD = new MemberRole("CALLED_METHOD", Count.ZERO_ONE);
    /**
     * Method reference which is associated with the warning
     */
    public static final MemberRole METHOD_REFERENCE = new MemberRole("METHOD_REFERENCE", Count.ZERO_ONE);
    /**
     * Class which should be used instead
     */
    public static final TypeRole REPLACEMENT_CLASS = new TypeRole("REPLACEMENT", Count.ZERO_ONE);
    /**
     * Method which should be called instead
     */
    public static final MemberRole REPLACEMENT_METHOD = new MemberRole("REPLACEMENT", Count.ZERO_ONE);
    /**
     * Super-class method associated with the warning
     */
    public static final MemberRole SUPER_METHOD = new MemberRole("SUPER_METHOD", Count.ZERO_ONE);
    /**
     * Replacement expression or operator
     */
    public static final StringRole REPLACEMENT_STRING = new StringRole("REPLACEMENT", Count.ZERO_ONE);
    /**
     * Local variable (or parameter) name associated with the warning
     */
    public static final StringRole VARIABLE = new StringRole("VARIABLE");
    /**
     * Any string associated with the warning
     */
    public static final StringRole STRING = new StringRole("STRING");
    /**
     * Any number associated with the warning
     */
    public static final NumberRole NUMBER = new NumberRole("NUMBER");
    /**
     * Operation related to the warning like "<" or "+" or "||"
     */
    public static final OperationRole OPERATION = new OperationRole("OPERATION");
    /**
     * Expression associated with warning
     */
    public static final ExpressionRole EXPRESSION = new ExpressionRole("EXPRESSION");
    /**
     * Argument associated with warning
     */
    public static final ExpressionRole ARGUMENT = new ExpressionRole("ARGUMENT");
    /**
     * Left argument associated with warning
     */
    public static final ExpressionRole LEFT_ARGUMENT = new ExpressionRole("LEFT_ARGUMENT");
    /**
     * Right argument associated with warning
     */
    public static final ExpressionRole RIGHT_ARGUMENT = new ExpressionRole("RIGHT_ARGUMENT");
    /**
     * Regular expression associated with the warning
     */
    public static final StringRole REGEXP = new StringRole("REGEXP");
    /**
     * Minimal allowed value associated with warning 
     */
    public static final NumberRole MIN_VALUE = new NumberRole("MIN_VALUE", Count.ZERO_ONE);
    /**
     * Maximal allowed value associated with warning 
     */
    public static final NumberRole MAX_VALUE = new NumberRole("MAX_VALUE", Count.ZERO_ONE);
    /**
     * Expression target type associated with warning
     */
    public static final TypeRole TARGET_TYPE = new TypeRole("TARGET_TYPE", Count.ZERO_ONE);
    /**
     * Field type associated with warning
     */
    public static final TypeRole FIELD_TYPE = new TypeRole("FIELD_TYPE", Count.ZERO_ONE);
    /**
     * Array type associated with warning
     */
    public static final TypeRole ARRAY_TYPE = new TypeRole("ARRAY_TYPE", Count.ZERO_ONE);
    /**
     * Value type associated with warning
     */
    public static final TypeRole VALUE_TYPE = new TypeRole("VALUE_TYPE", Count.ZERO_ONE);
    /**
     * Type of Exception associated with the warning
     */
    public static final TypeRole EXCEPTION = new TypeRole("EXCEPTION");
    /**
     * Interface type associated with the warning
     */
    public static final TypeRole INTERFACE = TypeRole.forName("INTERFACE");
    /**
     * Superclass type associated with the warning
     */
    public static final TypeRole SUPERCLASS = TypeRole.forName("SUPERCLASS");
    /**
     * Subclass type associated with the warning
     */
    public static final TypeRole SUBCLASS = TypeRole.forName("SUBCLASS");

    private Roles() {
        
    }
}
