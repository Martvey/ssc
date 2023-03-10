
package com.github.martvey.core.cli;

import org.apache.flink.table.client.gateway.SqlExecutionException;
import org.apache.flink.table.delegation.Parser;
import org.apache.flink.table.operations.*;
import org.apache.flink.table.operations.ddl.*;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class SqlCommandParser {

	private SqlCommandParser() {

	}

	public static SqlCommandCall parse(Parser sqlParser, String stmt) {
		// normalize
		stmt = stmt.trim();
		// remove ';' at the end
		if (stmt.endsWith(";")) {
			stmt = stmt.substring(0, stmt.length() - 1).trim();
		}

		// parse statement via regex matching first
		Optional<SqlCommandCall> callOpt = parseByRegexMatching(stmt);
		if (callOpt.isPresent()) {
			return callOpt.get();
		} else {
			return parseBySqlParser(sqlParser, stmt);
		}
	}

	private static SqlCommandCall parseBySqlParser(Parser sqlParser, String stmt) {
		List<Operation> operations;
		try {
			operations = sqlParser.parse(stmt);
		} catch (Throwable e) {
			throw new SqlExecutionException("Invalidate SQL statement.", e);
		}
		if (operations.size() != 1) {
			throw new SqlExecutionException("Only single statement is supported now.");
		}

		final SqlCommand cmd;
		String[] operands = new String[] { stmt };
		Operation operation = operations.get(0);
		if (operation instanceof CatalogSinkModifyOperation) {
			boolean overwrite = ((CatalogSinkModifyOperation) operation).isOverwrite();
			cmd = overwrite ? SqlCommand.INSERT_OVERWRITE : SqlCommand.INSERT_INTO;
		} else if (operation instanceof CreateTableOperation) {
			cmd = SqlCommand.CREATE_TABLE;
		} else if (operation instanceof DropTableOperation) {
			cmd = SqlCommand.DROP_TABLE;
		} else if (operation instanceof AlterTableOperation) {
			cmd = SqlCommand.ALTER_TABLE;
		} else if (operation instanceof CreateDebugOperation){
			cmd = SqlCommand.CREATE_DEBUG;
			operands = new String[]{((CreateDebugOperation) operation).getDebugIdentifier().getObjectName(),
					((CreateDebugOperation) operation).getCatalogDebug().getExpandedQuery()};
		}
		else if (operation instanceof CreateViewOperation) {
			cmd = SqlCommand.CREATE_VIEW;
		} else if (operation instanceof DropViewOperation) {
			cmd = SqlCommand.DROP_VIEW;
		} else if (operation instanceof AlterViewOperation) {
			cmd = SqlCommand.ALTER_VIEW;
		} else if (operation instanceof CreateDatabaseOperation) {
			cmd = SqlCommand.CREATE_DATABASE;
		} else if (operation instanceof DropDatabaseOperation) {
			cmd = SqlCommand.DROP_DATABASE;
		} else if (operation instanceof AlterDatabaseOperation) {
			cmd = SqlCommand.ALTER_DATABASE;
		} else if (operation instanceof CreateCatalogOperation) {
			cmd = SqlCommand.CREATE_CATALOG;
		} else if (operation instanceof DropCatalogOperation) {
			cmd = SqlCommand.DROP_CATALOG;
		} else if (operation instanceof UseCatalogOperation) {
			cmd = SqlCommand.USE_CATALOG;
			operands = new String[] { ((UseCatalogOperation) operation).getCatalogName() };
		} else if (operation instanceof UseDatabaseOperation) {
			cmd = SqlCommand.USE;
			operands = new String[] { ((UseDatabaseOperation) operation).getDatabaseName() };
		} else if (operation instanceof ShowCatalogsOperation) {
			cmd = SqlCommand.SHOW_CATALOGS;
			operands = new String[0];
		} else if (operation instanceof ShowCurrentCatalogOperation) {
			cmd = SqlCommand.SHOW_CURRENT_CATALOG;
			operands = new String[0];
		} else if (operation instanceof ShowDatabasesOperation) {
			cmd = SqlCommand.SHOW_DATABASES;
			operands = new String[0];
		} else if (operation instanceof ShowCurrentDatabaseOperation) {
			cmd = SqlCommand.SHOW_CURRENT_DATABASE;
			operands = new String[0];
		} else if (operation instanceof ShowTablesOperation) {
			cmd = SqlCommand.SHOW_TABLES;
			operands = new String[0];
		} else if (operation instanceof ShowFunctionsOperation) {
			cmd = SqlCommand.SHOW_FUNCTIONS;
			operands = new String[0];
		} else if (operation instanceof ShowPartitionsOperation) {
			cmd = SqlCommand.SHOW_PARTITIONS;
		} else if (operation instanceof CreateCatalogFunctionOperation ||
				operation instanceof CreateTempSystemFunctionOperation) {
			cmd = SqlCommand.CREATE_FUNCTION;
		} else if (operation instanceof DropCatalogFunctionOperation ||
				operation instanceof DropTempSystemFunctionOperation) {
			cmd = SqlCommand.DROP_FUNCTION;
		} else if (operation instanceof AlterCatalogFunctionOperation) {
			cmd = SqlCommand.ALTER_FUNCTION;
		} else if (operation instanceof ExplainOperation) {
			cmd = SqlCommand.EXPLAIN;
		} else if (operation instanceof DescribeTableOperation) {
			cmd = SqlCommand.DESCRIBE;
			operands = new String[] { ((DescribeTableOperation) operation).getSqlIdentifier().asSerializableString() };
		} else if (operation instanceof QueryOperation) {
			cmd = SqlCommand.SELECT;
		} else {
			throw new SqlExecutionException("Unknown operation: " + operation.asSummaryString());
		}

		return new SqlCommandCall(cmd, operands);
	}

	public static Optional<SqlCommandCall> parseByRegexMatching(String stmt) {
		// parse statement via regex matching
		for (SqlCommand cmd : SqlCommand.values()) {
			if (cmd.hasRegexPattern()) {
				final Matcher matcher = cmd.pattern.matcher(stmt);
				if (matcher.matches()) {
					final String[] groups = new String[matcher.groupCount()];
					for (int i = 0; i < groups.length; i++) {
						groups[i] = matcher.group(i + 1);
					}
					return cmd.operandConverter.apply(groups)
							.map((operands) -> {
								String[] newOperands = operands;
								if (cmd == SqlCommand.EXPLAIN) {
									// convert `explain xx` to `explain plan for xx`
									// which can execute through executeSql method
									newOperands = new String[] { "EXPLAIN PLAN FOR " + operands[0] + " "  + operands[1] };
								}
								return new SqlCommandCall(cmd, newOperands);
							});
				}
			}
		}
		return Optional.empty();
	}

	// --------------------------------------------------------------------------------------------

	private static final Function<String[], Optional<String[]>> NO_OPERANDS =
		(operands) -> Optional.of(new String[0]);

	private static final Function<String[], Optional<String[]>> SINGLE_OPERAND =
		(operands) -> Optional.of(new String[]{operands[0]});

	private static final int DEFAULT_PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

	public enum SqlCommand {
		QUIT(
			"(QUIT|EXIT)",
			NO_OPERANDS),

		CLEAR(
			"CLEAR",
			NO_OPERANDS),

		HELP(
			"HELP",
			NO_OPERANDS),

		SHOW_CATALOGS,

		SHOW_CURRENT_CATALOG,

		SHOW_DATABASES,

		SHOW_CURRENT_DATABASE,

		SHOW_TABLES,

		SHOW_FUNCTIONS,

		// FLINK-17396
		SHOW_MODULES(
			"SHOW\\s+MODULES",
			NO_OPERANDS),

		SHOW_PARTITIONS,

		USE_CATALOG,

		USE,

		CREATE_CATALOG,

		DROP_CATALOG,

		DESC(
			"DESC\\s+(.*)",
			SINGLE_OPERAND),

		DESCRIBE,

		// supports both `explain xx` and `explain plan for xx` now
		// TODO should keep `explain xx` ?
		// only match "EXPLAIN SELECT xx" and "EXPLAIN INSERT xx" here
		// "EXPLAIN PLAN FOR xx" should be parsed via sql parser
		EXPLAIN(
			"EXPLAIN\\s+(SELECT|INSERT)\\s+(.*)",
			(operands) -> {
				return Optional.of(new String[] { operands[0], operands[1] });
			}),

		CREATE_DATABASE,

		DROP_DATABASE,

		ALTER_DATABASE,

		CREATE_TABLE,

		DROP_TABLE,

		ALTER_TABLE,

		CREATE_DEBUG,

		CREATE_VIEW,

		DROP_VIEW,

		ALTER_VIEW,

		CREATE_FUNCTION,

		DROP_FUNCTION,

		ALTER_FUNCTION,

		SELECT,

		INSERT_INTO,

		INSERT_OVERWRITE,

		SET(
			"SET(\\s+(\\S+)\\s*=(.*))?", // whitespace is only ignored on the left side of '='
			(operands) -> {
				if (operands.length < 3) {
					return Optional.empty();
				} else if (operands[0] == null) {
					return Optional.of(new String[0]);
				}
				return Optional.of(new String[]{operands[1], operands[2]});
			}),

		RESET(
			"RESET",
			NO_OPERANDS),

		SOURCE(
			"SOURCE\\s+(.*)",
			SINGLE_OPERAND);

		public final @Nullable Pattern pattern;
		public final @Nullable Function<String[], Optional<String[]>> operandConverter;

		SqlCommand() {
			this.pattern = null;
			this.operandConverter = null;
		}

		SqlCommand(String matchingRegex, Function<String[], Optional<String[]>> operandConverter) {
			this.pattern = Pattern.compile(matchingRegex, DEFAULT_PATTERN_FLAGS);
			this.operandConverter = operandConverter;
		}

		@Override
		public String toString() {
			return super.toString().replace('_', ' ');
		}

		public boolean hasOperands() {
			return operandConverter != NO_OPERANDS;
		}

		public boolean hasRegexPattern() {
			return pattern != null;
		}
	}

	public static class SqlCommandCall {
		public final SqlCommand command;
		public final String[] operands;

		public SqlCommandCall(SqlCommand command, String[] operands) {
			this.command = command;
			this.operands = operands;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			SqlCommandCall that = (SqlCommandCall) o;
			return command == that.command && Arrays.equals(operands, that.operands);
		}

		@Override
		public int hashCode() {
			int result = Objects.hash(command);
			result = 31 * result + Arrays.hashCode(operands);
			return result;
		}

		@Override
		public String toString() {
			return command + "(" + Arrays.toString(operands) + ")";
		}
	}
}
