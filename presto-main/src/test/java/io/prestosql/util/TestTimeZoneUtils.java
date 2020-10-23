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
package io.prestosql.util;

import io.prestosql.spi.type.TimeZoneKey;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import java.time.ZoneId;
import java.util.TreeSet;

import static io.prestosql.spi.type.DateTimeEncoding.packDateTimeWithZone;
import static io.prestosql.spi.type.DateTimeEncoding.unpackMillisUtc;
import static io.prestosql.spi.type.DateTimeEncoding.unpackZoneKey;
import static io.prestosql.spi.type.TimeZoneKey.isUtcZoneId;
import static io.prestosql.util.DateTimeZoneIndex.getDateTimeZone;
import static io.prestosql.util.DateTimeZoneIndex.packDateTimeWithZone;
import static io.prestosql.util.DateTimeZoneIndex.unpackDateTimeZone;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestTimeZoneUtils
{
    @Test
    public void testNamedZones()
    {
        TreeSet<String> jdkZones = new TreeSet<>(ZoneId.getAvailableZoneIds());
        for (String zoneId : jdkZones) {
            if (zoneId.startsWith("Etc/") || zoneId.startsWith("GMT") || zoneId.startsWith("SystemV/")) {
                continue;
            }

            DateTimeZone dateTimeZone = DateTimeZone.forID(zoneId);
            DateTimeZone indexedZone = getDateTimeZone(TimeZoneKey.getTimeZoneKey(zoneId));

            assertDateTimeZoneEquals(zoneId, indexedZone);
            assertTimeZone(zoneId, dateTimeZone);
        }
    }

    @Test
    public void testOffsets()
    {
        for (int offsetHours = -13; offsetHours < 14; offsetHours++) {
            for (int offsetMinutes = 0; offsetMinutes < 60; offsetMinutes++) {
                DateTimeZone dateTimeZone = DateTimeZone.forOffsetHoursMinutes(offsetHours, offsetMinutes);
                assertTimeZone(dateTimeZone.getID(), dateTimeZone);
            }
        }
    }

    public static void assertTimeZone(String zoneId, DateTimeZone dateTimeZone)
    {
        long packWithDateTime = packDateTimeWithZone(new DateTime(42, dateTimeZone));
        long packWithZoneId = packDateTimeWithZone(42L, dateTimeZone.toTimeZone().getID());
        if (packWithDateTime != packWithZoneId) {
            fail(format(
                    "packWithDateTime and packWithZoneId differ for zone [%s] / [%s]: %s [%s %s] and %s [%s %s]",
                    zoneId,
                    dateTimeZone,
                    packWithDateTime,
                    unpackMillisUtc(packWithDateTime),
                    unpackZoneKey(packWithDateTime),
                    packWithZoneId,
                    unpackMillisUtc(packWithZoneId),
                    unpackZoneKey(packWithZoneId)));
        }
        DateTimeZone unpackedZone = unpackDateTimeZone(packWithDateTime);
        assertDateTimeZoneEquals(zoneId, unpackedZone);
    }

    public static void assertDateTimeZoneEquals(String zoneId, DateTimeZone actualTimeZone)
    {
        DateTimeZone expectedDateTimeZone;
        if (isUtcZoneId(zoneId)) {
            expectedDateTimeZone = DateTimeZone.UTC;
        }
        else {
            expectedDateTimeZone = DateTimeZone.forID(zoneId);
        }

        assertEquals(actualTimeZone, expectedDateTimeZone);
    }
}
