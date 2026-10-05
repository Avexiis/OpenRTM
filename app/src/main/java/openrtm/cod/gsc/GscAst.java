package openrtm.cod.gsc;

import java.util.List;

final class GscAst
{
	record Program(List<Function> functions)
	{
	}

	record Function(String name, List<String> parameters, Block body, GscToken token)
	{
	}

	sealed interface Statement permits Block, ExpressionStatement, IfStatement, WhileStatement, ForStatement,
		ForeachStatement, SwitchStatement, BreakStatement, ContinueStatement, ReturnStatement, WaitStatement,
		WaitFrameStatement
	{
		GscToken token();
	}

	record Block(List<Statement> statements, GscToken token) implements Statement
	{
	}

	record ExpressionStatement(Expression expression, GscToken token) implements Statement
	{
	}

	record IfStatement(Expression condition, Statement thenBranch, Statement elseBranch, GscToken token)
		implements Statement
	{
	}

	record WhileStatement(Expression condition, Statement body, GscToken token) implements Statement
	{
	}

	record ForStatement(Expression initializer, Expression condition, Expression iterator, Statement body,
		GscToken token) implements Statement
	{
	}

	record ForeachStatement(String variable, String value, Expression array, Statement body, GscToken token)
		implements Statement
	{
	}

	record SwitchStatement(Expression expression, List<SwitchCase> cases, GscToken token) implements Statement
	{
	}

	record SwitchCase(Expression value, List<Statement> statements, GscToken token)
	{
	}

	record BreakStatement(GscToken token) implements Statement
	{
	}

	record ContinueStatement(GscToken token) implements Statement
	{
	}

	record ReturnStatement(Expression value, GscToken token) implements Statement
	{
	}

	record WaitStatement(Expression value, GscToken token) implements Statement
	{
	}

	record WaitFrameStatement(GscToken token) implements Statement
	{
	}

	sealed interface Expression permits Literal, Name, ArrayLiteral, VectorLiteral, Unary, Binary, Assignment,
		Field, Index, Call, FunctionReference
	{
		GscToken token();
	}

	record Literal(LiteralType type, String value, GscToken token) implements Expression
	{
	}

	enum LiteralType
	{
		UNDEFINED,
		INTEGER,
		FLOAT,
		STRING,
		LOCALIZED_STRING,
		BOOLEAN
	}

	record Name(String value, GscToken token) implements Expression
	{
	}

	record ArrayLiteral(List<Expression> values, GscToken token) implements Expression
	{
	}

	record VectorLiteral(Expression x, Expression y, Expression z, GscToken token) implements Expression
	{
	}

	record Unary(String operator, Expression expression, boolean postfix, GscToken token) implements Expression
	{
	}

	record Binary(Expression left, String operator, Expression right, GscToken token) implements Expression
	{
	}

	record Assignment(Expression target, String operator, Expression value, GscToken token) implements Expression
	{
	}

	record Field(Expression target, String name, GscToken token) implements Expression
	{
	}

	record Index(Expression target, Expression index, GscToken token) implements Expression
	{
	}

	record Call(Expression receiver, String path, String name, Expression pointer, List<Expression> arguments,
		boolean thread, boolean childThread, GscToken token) implements Expression
	{
	}

	record FunctionReference(String path, String name, GscToken token) implements Expression
	{
	}

	private GscAst()
	{
	}
}
