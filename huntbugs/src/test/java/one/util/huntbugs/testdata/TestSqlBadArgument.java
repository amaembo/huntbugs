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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestSqlBadArgument {
    @AssertWarning(value="BadResultSetArgument", minScore=70)
    public String get(ResultSet rs) throws SQLException {
        return rs.getString(0);
    }

    @AssertWarning(value="BadResultSetArgument", maxScore=60)
    public String getConditional(ResultSet rs, boolean b) throws SQLException {
        int pos = b ? 2 : 0;
        return rs.getString(pos);
    }
    
    @AssertWarning("BadPreparedStatementArgument")
    public void set(PreparedStatement ps) throws SQLException {
        ps.setInt(0, 10);
    }
    
    @AssertNoWarning("*")
    public void setOk(PreparedStatement ps) throws SQLException {
        ps.setInt(10, 0);
    }
}
