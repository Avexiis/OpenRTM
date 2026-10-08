package openrtm.cod.gsc;

import java.util.List;
import java.util.function.Consumer;

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

	static void visit(Statement statement, Consumer<Statement> statements, Consumer<Expression> expressions)
	{
		if (statement == null)
		{
			return;
		}
		statements.accept(statement);
		if (statement instanceof Block block)
		{
			for (Statement child : block.statements())
			{
				visit(child, statements, expressions);
			}
		}
		else if (statement instanceof ExpressionStatement expression)
		{
			visit(expression.expression(), expressions);
		}
		else if (statement instanceof IfStatement conditional)
		{
			visit(conditional.condition(), expressions);
			visit(conditional.thenBranch(), statements, expressions);
			visit(conditional.elseBranch(), statements, expressions);
		}
		else if (statement instanceof WhileStatement loop)
		{
			visit(loop.condition(), expressions);
			visit(loop.body(), statements, expressions);
		}
		else if (statement instanceof ForStatement loop)
		{
			visit(loop.initializer(), expressions);
			visit(loop.condition(), expressions);
			visit(loop.iterator(), expressions);
			visit(loop.body(), statements, expressions);
		}
		else if (statement instanceof ForeachStatement loop)
		{
			visit(loop.array(), expressions);
			visit(loop.body(), statements, expressions);
		}
		else if (statement instanceof SwitchStatement selection)
		{
			visit(selection.expression(), expressions);
			for (SwitchCase choice : selection.cases())
			{
				visit(choice.value(), expressions);
				for (Statement child : choice.statements())
				{
					visit(child, statements, expressions);
				}
			}
		}
		else if (statement instanceof ReturnStatement returning)
		{
			visit(returning.value(), expressions);
		}
		else if (statement instanceof WaitStatement wait)
		{
			visit(wait.value(), expressions);
		}
	}

	private static void visit(Expression expression, Consumer<Expression> expressions)
	{
		if (expression == null)
		{
			return;
		}
		expressions.accept(expression);
		if (expression instanceof ArrayLiteral array)
		{
			for (Expression value : array.values())
			{
				visit(value, expressions);
			}
		}
		else if (expression instanceof VectorLiteral vector)
		{
			visit(vector.x(), expressions);
			visit(vector.y(), expressions);
			visit(vector.z(), expressions);
		}
		else if (expression instanceof Unary unary)
		{
			visit(unary.expression(), expressions);
		}
		else if (expression instanceof Binary binary)
		{
			visit(binary.left(), expressions);
			visit(binary.right(), expressions);
		}
		else if (expression instanceof Assignment assignment)
		{
			visit(assignment.target(), expressions);
			visit(assignment.value(), expressions);
		}
		else if (expression instanceof Field field)
		{
			visit(field.target(), expressions);
		}
		else if (expression instanceof Index index)
		{
			visit(index.target(), expressions);
			visit(index.index(), expressions);
		}
		else if (expression instanceof Call call)
		{
			visit(call.receiver(), expressions);
			visit(call.pointer(), expressions);
			for (Expression argument : call.arguments())
			{
				visit(argument, expressions);
			}
		}
	}

	private GscAst()
	{
	}
}
