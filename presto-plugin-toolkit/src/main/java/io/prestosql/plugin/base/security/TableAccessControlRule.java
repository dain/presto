/*
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
package io.prestosql.plugin.base.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import io.prestosql.spi.connector.SchemaTableName;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

public class TableAccessControlRule
{
    public static final TableAccessControlRule ALLOW_ALL = new TableAccessControlRule(
            ImmutableSet.copyOf(TablePrivilege.values()),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    private final Set<TablePrivilege> privileges;
    private final Optional<Pattern> userRegex;
    private final Optional<Pattern> groupRegex;
    private final Optional<Pattern> schemaRegex;
    private final Optional<Pattern> tableRegex;

    @JsonCreator
    public TableAccessControlRule(
            @JsonProperty("privileges") Set<TablePrivilege> privileges,
            @JsonProperty("user") Optional<Pattern> userRegex,
            @JsonProperty("group") Optional<Pattern> groupRegex,
            @JsonProperty("schema") Optional<Pattern> schemaRegex,
            @JsonProperty("table") Optional<Pattern> tableRegex)
    {
        this.privileges = ImmutableSet.copyOf(requireNonNull(privileges, "privileges is null"));
        this.userRegex = requireNonNull(userRegex, "user is null");
        this.groupRegex = requireNonNull(groupRegex, "group is null");
        this.schemaRegex = requireNonNull(schemaRegex, "sourceRegex is null");
        this.tableRegex = requireNonNull(tableRegex, "tableRegex is null");
    }

    public Optional<Set<TablePrivilege>> match(String user, Set<String> groups, SchemaTableName table)
    {
        if (userRegex.map(regex -> regex.matcher(user).matches()).orElse(true) &&
                groupRegex.map(regex -> groups.stream().anyMatch(group -> regex.matcher(group).matches())).orElse(true) &&
                schemaRegex.map(regex -> regex.matcher(table.getSchemaName()).matches()).orElse(true) &&
                tableRegex.map(regex -> regex.matcher(table.getTableName()).matches()).orElse(true)) {
            return Optional.of(privileges);
        }
        return Optional.empty();
    }

    Set<TablePrivilege> getPrivileges()
    {
        return privileges;
    }

    Optional<Pattern> getUserRegex()
    {
        return userRegex;
    }

    Optional<Pattern> getGroupRegex()
    {
        return groupRegex;
    }

    public enum TablePrivilege
    {
        SELECT, INSERT, DELETE, OWNERSHIP, GRANT_SELECT
    }
}
