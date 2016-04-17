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
package one.util.huntbugs.warning;

import com.strobel.core.StringUtilities;

import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;
import one.util.huntbugs.warning.WarningAnnotation.TypeInfo;

/**
 * @author lan
 *
 */
public class Formatter {
    public static final String FORMAT_PLAIN = "plain";
    public static final String FORMAT_HTML = "html";
    public static final String FORMAT_NAME = "name";
    public static final String FORMAT_HEX = "hex";
    public static final String FORMAT_DEC = "dec";
    public static final String FORMAT_CONST = "const";

    private final Messages msgs;

    public Formatter() {
        this(Messages.load());
    }

    public Formatter(Messages msgs) {
        this.msgs = msgs;
    }

    public String getTitle(Warning warning) {
        return msgs.getMessagesForType(warning.getType()).getTitle();
    }

    public String getDescription(Warning warning) {
        return format(msgs.getMessagesForType(warning.getType()).getDescription(), warning, FORMAT_PLAIN);
    }

    public String getLongDescription(Warning warning) {
        return format(msgs.getMessagesForType(warning.getType()).getLongDescription(), warning, FORMAT_HTML);
    }

    private String format(String description, Warning warning, String format) {
        String[] fields = description.split("\\$", -1);
        if (fields.length == 1)
            return description;
        StringBuilder result = new StringBuilder(fields[0]);
        for (int i = 1; i < fields.length; i++) {
            if (i % 2 == 0) {
                result.append(fields[i]);
            } else {
                String key = fields[i];
                String f = format;
                int pos = key.indexOf(':');
                if (pos > 0) {
                    f = key.substring(pos + 1);
                    key = key.substring(0, pos);
                }
                WarningAnnotation<?> anno = warning.getAnnotation(key);
                if (anno == null) {
                    result.append('(').append(key).append(')');
                } else {
                    result.append(formatValue(anno.getValue(), f));
                }
            }
        }
        return result.toString();
    }

    public String formatValue(Object value, String format) {
        if (value instanceof MemberInfo) {
            return formatMemberInfo((MemberInfo) value, format);
        }
        if (value instanceof TypeInfo) {
            return formatTypeInfo((TypeInfo) value, format);
        }
        if (value instanceof Double) {
            return formatDouble((Double) value);
        }
        if (value instanceof Float) {
            return formatFloat((Float) value);
        }
        if (value instanceof Integer) {
            return formatInteger((Integer) value, format);
        }
        if (value instanceof Long) {
            return formatLong((Long) value, format);
        }
        if (value instanceof String) {
            return formatString((String) value, format);
        }
        return String.valueOf(value);
    }

    private String formatString(String value, String format) {
        if(format.equals("const")) {
            return StringUtilities.escape(value, true);
        }
        return value;
    }

    private String formatLong(long value, String format) {
        if (format.equals(FORMAT_HEX)) {
            return "0x" + Long.toHexString(value);
        }
        return Long.toString(value);
    }

    private String formatInteger(int value, String format) {
        if (format.equals(FORMAT_HEX)) {
            return "0x" + Integer.toHexString(value);
        }
        return Integer.toString(value);
    }

    public String formatFloat(float val) {
        if (Float.isNaN(val))
            return "Float.NaN";
        if (val == Float.POSITIVE_INFINITY)
            return "Float.POSITIVE_INFINITY";
        if (val == Float.NEGATIVE_INFINITY)
            return "Float.NEGATIVE_INFINITY";
        if (val == Float.MIN_VALUE)
            return "Float.MIN_VALUE";
        if (val == Float.MAX_VALUE)
            return "Float.MAX_VALUE";
        if (val == -Float.MIN_VALUE)
            return "-Float.MIN_VALUE";
        if (val == -Float.MAX_VALUE)
            return "-Float.MAX_VALUE";
        return Float.toString(val);
    }

    public String formatDouble(double val) {
        if (Double.isNaN(val))
            return "Double.NaN";
        if (val == Double.POSITIVE_INFINITY)
            return "Double.POSITIVE_INFINITY";
        if (val == Double.NEGATIVE_INFINITY)
            return "Double.NEGATIVE_INFINITY";
        if (val == Double.MIN_VALUE)
            return "Double.MIN_VALUE";
        if (val == Double.MAX_VALUE)
            return "Double.MAX_VALUE";
        if (val == -Double.MIN_VALUE)
            return "-Double.MIN_VALUE";
        if (val == -Double.MAX_VALUE)
            return "-Double.MAX_VALUE";
        return Double.toString(val);
    }

    public String formatTypeInfo(TypeInfo ti, String format) {
        String simpleName = ti.getSimpleName();
        String result = simpleName;
        if (format.equals(FORMAT_HTML))
            return "<code class=\"Member\" title=\"" + ti + "\">" + result + "</code>";
        return result;
    }

    public String formatMemberInfo(MemberInfo mi, String format) {
        if (format.equals("name"))
            return mi.getName();
        String type = mi.getTypeName();
        int pos = type.lastIndexOf('/');
        if (pos > -1)
            type = type.substring(pos + 1).replace('$', '.');
        String result;
        if (mi.isMethod()) {
            if (mi.getName().equals("<init>"))
                result = type + "()";
            else if (mi.getName().equals("<clinit>"))
                result = type + " static {}";
            else
                result = type + "." + mi.getName() + "()";
        } else {
            result = type + "." + mi.getName();
        }
        if (format.equals(FORMAT_HTML))
            return "<code class=\"Member\" title=\"" + mi + "\">" + result + "</code>";
        return result;
    }
}
