package org.neo4j.shell.commands;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.shell.ConnectionConfig;
import org.neo4j.shell.CypherShell;
import org.neo4j.shell.StringLinePrinter;
import org.neo4j.shell.cli.Format;
import org.neo4j.shell.exception.CommandException;
import org.neo4j.shell.prettyprint.PrettyConfig;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.neo4j.driver.internal.messaging.request.MultiDatabaseUtil.ABSENT_DB_NAME;
import static org.neo4j.shell.DatabaseManager.DEFAULT_DEFAULT_DB_NAME;
import static org.neo4j.shell.DatabaseManager.SYSTEM_DB_NAME;
import static org.neo4j.shell.Versions.majorVersion;

public class CypherShellMultiDatabaseIntegrationTest
{
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private StringLinePrinter linePrinter = new StringLinePrinter();
    private Command useCommand;
    private Command beginCommand;
    private Command rollbackCommand;
    private CypherShell shell;

    @Before
    public void setUp() throws Exception {
        linePrinter.clear();
        shell = new CypherShell(linePrinter, new PrettyConfig(Format.PLAIN, true, 1000));
        useCommand = new Use(shell);
        beginCommand = new Begin(shell);
        rollbackCommand = new Rollback(shell);

        shell.connect(new ConnectionConfig("bolt://", "localhost", 7687, "neo4j", "neo", true, ABSENT_DB_NAME));

        // Multiple databases are only available from 4.0
        assumeTrue( majorVersion( shell.getServerVersion() ) >= 4 );
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void switchingToSystemDatabaseWorks() throws CommandException {
        useCommand.execute(SYSTEM_DB_NAME);

        assertThat(linePrinter.output(), is(""));
        assertOnSystemDB();
    }

    @Test
    public void switchingToSystemDatabaseAndBackToNeo4jWorks() throws CommandException {
        useCommand.execute(SYSTEM_DB_NAME);
        useCommand.execute(DEFAULT_DEFAULT_DB_NAME);

        assertThat(linePrinter.output(), is(""));
        assertOnRegularDB();
    }

    @Test
    public void switchingToSystemDatabaseAndBackToDefaultWorks() throws CommandException {
        useCommand.execute(SYSTEM_DB_NAME);
        useCommand.execute(ABSENT_DB_NAME);

        assertThat(linePrinter.output(), is(""));
        assertOnRegularDB();
    }

    @Test
    public void switchingDatabaseInOpenTransactionShouldFail() throws CommandException {
        thrown.expect(CommandException.class);
        thrown.expectMessage("There is an open transaction.");

        beginCommand.execute("");
        useCommand.execute("another_database");
    }

    @Test
    public void switchingDatabaseAfterRollbackTransactionWorks() throws CommandException {
        beginCommand.execute("");
        rollbackCommand.execute("");
        useCommand.execute(SYSTEM_DB_NAME);

        assertThat(linePrinter.output(), is(""));
        assertOnSystemDB();
    }

    @Test
    public void switchingToNonExistingDatabaseShouldGiveErrorResponseFromServer() throws CommandException {
        thrown.expect(ClientException.class);
        thrown.expectMessage("The database requested does not exist.");

        useCommand.execute("this_database_name_does_not_exist_in_test_container");
    }

    // HELPERS

    private void assertOnRegularDB() throws CommandException {
        shell.execute("RETURN 'toadstool'");
        assertThat(linePrinter.output(), containsString("toadstool"));
    }

    private void assertOnSystemDB() throws CommandException {
        shell.execute("SHOW DATABASES");
        assertThat(linePrinter.output(), containsString("neo4j"));
        assertThat(linePrinter.output(), containsString("system"));
    }
}