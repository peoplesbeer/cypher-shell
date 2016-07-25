package org.neo4j.shell;

import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.shell.commands.Command;
import org.neo4j.shell.commands.CommandExecutable;
import org.neo4j.shell.commands.CommandHelper;
import org.neo4j.shell.exception.CommandException;
import org.neo4j.shell.exception.ExitException;
import org.neo4j.shell.log.Logger;
import org.neo4j.shell.prettyprint.PrettyPrinter;
import org.neo4j.shell.state.BoltStateHandler;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A possibly interactive shell for evaluating cypher statements.
 */
public class CypherShell implements StatementExecuter, Connector, TransactionHandler, VariableHolder {
    private final Logger logger;

    // Final space to catch newline
    protected static final Pattern cmdNamePattern = Pattern.compile("^\\s*(?<name>[^\\s]+)\\b(?<args>.*)\\s*$");
    private final BoltStateHandler boltStateHandler;
    protected CommandHelper commandHelper;
    protected final Map<String, Object> queryParams = new HashMap<>();

    public CypherShell(@Nonnull Logger logger) {
        this(logger, new BoltStateHandler());
    }

    protected CypherShell(@Nonnull Logger logger, @Nonnull BoltStateHandler boltStateHandler) {
        this.logger = logger;
        this.boltStateHandler = boltStateHandler;
    }

    @Override
    public void execute(@Nonnull final String cmdString) throws ExitException, CommandException {
        // See if it's a shell command
        Optional<CommandExecutable> cmd = getCommandExecutable(cmdString);
        if (cmd.isPresent()) {
            executeCmd(cmd.get());
            return;
        }

        // Else it will be parsed as Cypher, but for that we need to be connected
        if (!isConnected()) {
            throw new CommandException("Not connected to Neo4j");
        }

        executeCypher(cmdString);
    }

    /**
     * Executes a piece of text as if it were Cypher. By default, all of the cypher is executed in single statement
     * (with an implicit transaction).
     *
     * @param cypher non-empty cypher text to executeLine
     */
    protected void executeCypher(@Nonnull final String cypher) throws CommandException {
        final StatementResult result = doCypherSilently(cypher);
        logger.printOut(PrettyPrinter.format(result));
    }

    @Override
    public boolean isConnected() {
        return boltStateHandler.isConnected();
    }

    @Nonnull
    protected Optional<CommandExecutable> getCommandExecutable(@Nonnull final String line) {
        Matcher m = cmdNamePattern.matcher(line);
        if (commandHelper == null || !m.matches()) {
            return Optional.empty();
        }

        String name = m.group("name");
        String args = m.group("args");

        Command cmd = commandHelper.getCommand(name);

        if (cmd == null) {
            return Optional.empty();
        }

        return Optional.of(() -> cmd.execute(args));
    }

    protected void executeCmd(@Nonnull final CommandExecutable cmdExe) throws ExitException, CommandException {
        cmdExe.execute();
    }

    /**
     * Open a session to Neo4j
     *
     * @param connectionConfig
     */
    @Override
    public void connect(@Nonnull ConnectionConfig connectionConfig) throws CommandException {
        boltStateHandler.connect(connectionConfig);
    }

    @Override
    public void disconnect() throws CommandException {
        boltStateHandler.disconnect();
    }

    @Override
    public void beginTransaction() throws CommandException {
        boltStateHandler.beginTransaction();
    }

    @Override
    public void commitTransaction() throws CommandException {
        boltStateHandler.commitTransaction();
    }

    @Override
    public void rollbackTransaction() throws CommandException {
        boltStateHandler.rollbackTransaction();
    }

    @Override
    @Nonnull
    public Optional set(@Nonnull String name, @Nonnull String valueString) throws CommandException {
        Record record = doCypherSilently("RETURN " + valueString + " as " + name).single();
        Object value = record.get(name).asObject();
        queryParams.put(name, value);
        return Optional.ofNullable(value);
    }

    @Override
    @Nonnull
    public Map<String, Object> getAll() {
        return queryParams;
    }

    @Override
    @Nonnull
    public Optional remove(@Nonnull String name) {
        return Optional.ofNullable(queryParams.remove(name));
    }

    /**
     * Run a cypher statement, and return the result. Is not stored in history.
     */
    private StatementResult doCypherSilently(@Nonnull final String cypher) throws CommandException {
        return boltStateHandler.getStatementRunner().run(cypher, queryParams);
    }

    public void setCommandHelper(@Nonnull CommandHelper commandHelper) {
        this.commandHelper = commandHelper;
    }
}