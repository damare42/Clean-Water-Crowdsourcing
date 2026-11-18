package fxapp;

import model.Account;
import model.AccountType;
import model.WaterSourceCondition;
import model.WaterSourceReport;
import model.WaterSourceType;
import model.persistence.DatabaseLocation;
import model.security.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppContextTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2016-10-11T12:30:00Z"), ZoneOffset.UTC);
    private static final PasswordHasher HASHER = new PasswordHasher(PasswordHasher.MIN_ITERATIONS);

    @TempDir
    Path dir;

    private static char[] pw(String s) {
        return s.toCharArray();
    }

    @Test
    void accountsProfilesAndReportsSurviveRestart() throws Exception {
        DatabaseLocation location = DatabaseLocation.file(dir.resolve("cwc/cwc.db"));
        WaterSourceReport report;
        try (AppContext first = AppContext.open(location, HASHER, CLOCK, ZoneOffset.UTC)) {
            Account alice = first.getAuthService().register("Alice", AccountType.Worker, "Alice", "Smith",
                    "a@example.com", "a@example.com", pw("secret1"), pw("secret1"));
            first.getAccountService().updateProfile(alice, alice, "Alicia", "Smith", "a@example.com",
                    "555", "Tech Square");
            report = first.getReportService().submit(alice, 33.77, -84.39,
                    WaterSourceType.Lake, WaterSourceCondition.Potable);
        }
        assertTrue(Files.isRegularFile(dir.resolve("cwc/cwc.db")));
        assertFalse(Files.readString(dir.resolve("cwc/cwc.db"), java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("secret1"), "plain password must not be stored");

        try (AppContext second = AppContext.open(location, HASHER, CLOCK, ZoneOffset.UTC)) {
            Account alice = second.getAuthService().login("alice", pw("secret1")).orElseThrow();
            assertEquals("Alice", alice.getId());
            assertEquals(AccountType.Worker, alice.getType());
            assertEquals("Alicia", alice.getFname());
            assertEquals("555", alice.getPhone());
            assertTrue(second.getAuthService().login("alice", pw("wrong12")).isEmpty());
            assertFalse(second.getAuthService().isIdAvailable("ALICE"));
            assertEquals(List.of(report), second.getReportService().listReports(alice));
            assertEquals(2, second.getReportService().nextReportNumber());
        }
    }

    @Test
    void inMemoryContextWorksWithoutDatabase() {
        try (AppContext context = AppContext.inMemory(HASHER, CLOCK, ZoneOffset.UTC)) {
            Account bob = context.getAuthService().register("bob", AccountType.User, "Bob", "Jones",
                    "b@example.com", "b@example.com", pw("secret1"), pw("secret1"));
            context.getReportService().submit(bob, 0, 0, WaterSourceType.Well, WaterSourceCondition.Clear);

            assertEquals(1, context.getReportService().listReports(bob).size());
            assertTrue(context.getAuthService().login("BOB", pw("secret1")).isPresent());
        }
    }
}
