package openrtm.cod.gsc;

import java.util.ArrayList;
import java.util.List;

final class GscParser
{
	private final List<GscToken> tokens;
	private int index;

	GscParser(List<GscToken> tokens)
	{
		this.tokens = tokens;
	}

	GscAst.Program parse()
	{
		List<GscAst.Function> functions = new ArrayList<>();
		while (!end())
		{
			if (match("#"))
			{
				skipDirective();
				continue;
			}
			functions.add(function());
		}
		return new GscAst.Program(List.copyOf(functions));
	}

	private GscAst.Function function()
	{
		GscToken name = require(GscToken.Type.IDENTIFIER, "Expected a function name");
		require("(");
		List<String> parameters = new ArrayList<>();
		if (!check(")"))
		{
			do
			{
				parameters.add(require(GscToken.Type.IDENTIFIER, "Expected a parameter name").text());
			}
			while (match(","));
		}
		require(")");
		return new GscAst.Function(name.text().toLowerCase(), List.copyOf(parameters), block(), name);
	}

	private GscAst.Block block()
	{
		GscToken opening = require("{");
		List<GscAst.Statement> statements = new ArrayList<>();
		while (!check("}") && !end())
		{
			statements.add(statement());
		}
		require("}");
		return new GscAst.Block(List.copyOf(statements), opening);
	}

	private GscAst.Statement statement()
	{
		if (check("{"))
		{
			return block();
		}
		if (match("if"))
		{
			return ifStatement(previous());
		}
		if (match("while"))
		{
			return whileStatement(previous());
		}
		if (match("for"))
		{
			return forStatement(previous());
		}
		if (match("foreach"))
		{
			return foreachStatement(previous());
		}
		if (match("switch"))
		{
			return switchStatement(previous());
		}
		if (match("break"))
		{
			GscToken token = previous();
			require(";");
			return new GscAst.BreakStatement(token);
		}
		if (match("continue"))
		{
			GscToken token = previous();
			require(";");
			return new GscAst.ContinueStatement(token);
		}
		if (match("return"))
		{
			GscToken token = previous();
			GscAst.Expression value = check(";") ? null : expression();
			require(";");
			return new GscAst.ReturnStatement(value, token);
		}
		if (match("wait"))
		{
			GscToken token = previous();
			GscAst.Expression value = expression();
			require(";");
			return new GscAst.WaitStatement(value, token);
		}
		if (match("waittillframeend") || match("waitframe"))
		{
			GscToken token = previous();
			if (match("("))
			{
				require(")");
			}
			require(";");
			return new GscAst.WaitFrameStatement(token);
		}
		GscToken token = peek();
		GscAst.Expression value = expression();
		require(";");
		return new GscAst.ExpressionStatement(value, token);
	}

	private GscAst.Statement ifStatement(GscToken token)
	{
		require("(");
		GscAst.Expression condition = expression();
		require(")");
		GscAst.Statement thenBranch = statement();
		GscAst.Statement elseBranch = match("else") ? statement() : null;
		return new GscAst.IfStatement(condition, thenBranch, elseBranch, token);
	}

	private GscAst.Statement whileStatement(GscToken token)
	{
		require("(");
		GscAst.Expression condition = expression();
		require(")");
		return new GscAst.WhileStatement(condition, statement(), token);
	}

	private GscAst.Statement forStatement(GscToken token)
	{
		require("(");
		GscAst.Expression initializer = check(";") ? null : expression();
		require(";");
		GscAst.Expression condition = check(";") ? null : expression();
		require(";");
		GscAst.Expression iterator = check(")") ? null : expression();
		require(")");
		return new GscAst.ForStatement(initializer, condition, iterator, statement(), token);
	}

	private GscAst.Statement foreachStatement(GscToken token)
	{
		require("(");
		String first = require(GscToken.Type.IDENTIFIER, "Expected a foreach variable").text();
		String second = null;
		if (match(","))
		{
			second = require(GscToken.Type.IDENTIFIER, "Expected a foreach value variable").text();
		}
		require("in");
		GscAst.Expression array = expression();
		require(")");
		return new GscAst.ForeachStatement(first, second, array, statement(), token);
	}

	private GscAst.Statement switchStatement(GscToken token)
	{
		require("(");
		GscAst.Expression value = expression();
		require(")");
		require("{");
		List<GscAst.SwitchCase> cases = new ArrayList<>();
		while (!check("}") && !end())
		{
			GscToken caseToken = peek();
			GscAst.Expression caseValue;
			if (match("case"))
			{
				caseValue = expression();
			}
			else if (match("default"))
			{
				caseValue = null;
			}
			else
			{
				throw error(peek(), "Expected case or default");
			}
			require(":");
			List<GscAst.Statement> statements = new ArrayList<>();
			while (!check("case") && !check("default") && !check("}") && !end())
			{
				statements.add(statement());
			}
			cases.add(new GscAst.SwitchCase(caseValue, List.copyOf(statements), caseToken));
		}
		require("}");
		return new GscAst.SwitchStatement(value, List.copyOf(cases), token);
	}

	private GscAst.Expression expression()
	{
		return assignment();
	}

	private GscAst.Expression assignment()
	{
		GscAst.Expression left = logicalOr();
		if (match("=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^="))
		{
			GscToken operator = previous();
			return new GscAst.Assignment(left, operator.text(), assignment(), operator);
		}
		return left;
	}

	private GscAst.Expression logicalOr()
	{
		return binary(this::logicalAnd, "||");
	}

	private GscAst.Expression logicalAnd()
	{
		return binary(this::bitOr, "&&");
	}

	private GscAst.Expression bitOr()
	{
		return binary(this::bitXor, "|");
	}

	private GscAst.Expression bitXor()
	{
		return binary(this::bitAnd, "^");
	}

	private GscAst.Expression bitAnd()
	{
		return binary(this::equality, "&");
	}

	private GscAst.Expression equality()
	{
		return binary(this::comparison, "==", "!=");
	}

	private GscAst.Expression comparison()
	{
		return binary(this::shift, "<", ">", "<=", ">=");
	}

	private GscAst.Expression shift()
	{
		return binary(this::term, "<<", ">>");
	}

	private GscAst.Expression term()
	{
		return binary(this::factor, "+", "-");
	}

	private GscAst.Expression factor()
	{
		return binary(this::unary, "*", "/", "%");
	}

	private GscAst.Expression binary(ExpressionReader reader, String... operators)
	{
		GscAst.Expression value = reader.read();
		while (match(operators))
		{
			GscToken operator = previous();
			value = new GscAst.Binary(value, operator.text(), reader.read(), operator);
		}
		return value;
	}

	private GscAst.Expression unary()
	{
		if (match("!", "~", "-", "+", "++", "--"))
		{
			GscToken operator = previous();
			return new GscAst.Unary(operator.text(), unary(), false, operator);
		}
		if (match("thread", "childthread"))
		{
			GscToken token = previous();
			return callTarget(null, token.is("thread"), token.is("childthread"), token);
		}
		return postfix(primary());
	}

	private GscAst.Expression postfix(GscAst.Expression value)
	{
		while (true)
		{
			if (match("."))
			{
				GscToken field = require(GscToken.Type.IDENTIFIER, "Expected a field name");
				value = new GscAst.Field(value, field.text(), field);
			}
			else if (match("["))
			{
				GscToken opening = previous();
				GscAst.Expression subscript = expression();
				require("]");
				value = new GscAst.Index(value, subscript, opening);
			}
			else if (check("(") && (value instanceof GscAst.Name || value instanceof GscAst.FunctionReference))
			{
				value = directCall(value, false, false);
			}
			else if (match("++", "--"))
			{
				value = new GscAst.Unary(previous().text(), value, true, previous());
			}
			else if (implicitCallStart())
			{
				boolean thread = match("thread");
				boolean childThread = !thread && match("childthread");
				value = callTarget(value, thread, childThread, peek());
			}
			else
			{
				return value;
			}
		}
	}

	private GscAst.Expression primary()
	{
		if (match("undefined"))
		{
			return new GscAst.Literal(GscAst.LiteralType.UNDEFINED, "", previous());
		}
		if (match("true", "false"))
		{
			return new GscAst.Literal(GscAst.LiteralType.BOOLEAN, previous().text(), previous());
		}
		if (peek().type() == GscToken.Type.NUMBER)
		{
			GscToken number = advance();
			GscAst.LiteralType type = number.text().contains(".") || number.text().contains("e")
				|| number.text().contains("E") ? GscAst.LiteralType.FLOAT : GscAst.LiteralType.INTEGER;
			return new GscAst.Literal(type, number.text(), number);
		}
		if (peek().type() == GscToken.Type.STRING)
		{
			GscToken string = advance();
			return new GscAst.Literal(GscAst.LiteralType.STRING, string.text(), string);
		}
		if (match("&"))
		{
			GscToken string = require(GscToken.Type.STRING, "Expected a localized string");
			return new GscAst.Literal(GscAst.LiteralType.LOCALIZED_STRING, string.text(), string);
		}
		if (match("["))
		{
			GscToken opening = previous();
			List<GscAst.Expression> values = new ArrayList<>();
			if (!check("]"))
			{
				do
				{
					values.add(expression());
				}
				while (match(","));
			}
			require("]");
			return new GscAst.ArrayLiteral(List.copyOf(values), opening);
		}
		if (match("[["))
		{
			GscToken opening = previous();
			GscAst.Expression pointer = expression();
			require("]");
			require("]");
			return pointerCall(null, pointer, false, false, opening);
		}
		if (match("::"))
		{
			GscToken token = previous();
			String name = require(GscToken.Type.IDENTIFIER, "Expected a function name").text();
			return new GscAst.FunctionReference("", name, token);
		}
		if (match("("))
		{
			GscToken opening = previous();
			GscAst.Expression first = expression();
			if (match(","))
			{
				GscAst.Expression second = expression();
				require(",");
				GscAst.Expression third = expression();
				require(")");
				return new GscAst.VectorLiteral(first, second, third, opening);
			}
			require(")");
			return first;
		}
		GscToken name = require(GscToken.Type.IDENTIFIER, "Expected an expression");
		if (check("\\") || check("::"))
		{
			return qualifiedReference(name);
		}
		return new GscAst.Name(name.text(), name);
	}

	private GscAst.Expression qualifiedReference(GscToken first)
	{
		StringBuilder path = new StringBuilder(first.text());
		while (match("\\"))
		{
			path.append('\\').append(require(GscToken.Type.IDENTIFIER, "Expected a path component").text());
		}
		require("::");
		String name = require(GscToken.Type.IDENTIFIER, "Expected a function name").text();
		return new GscAst.FunctionReference(path.toString(), name, first);
	}

	private GscAst.Expression directCall(GscAst.Expression target, boolean thread, boolean childThread)
	{
		if (target instanceof GscAst.Name name)
		{
			return new GscAst.Call(null, "", name.value(), null, arguments(), thread, childThread, name.token());
		}
		GscAst.FunctionReference reference = (GscAst.FunctionReference) target;
		return new GscAst.Call(null, reference.path(), reference.name(), null, arguments(), thread, childThread,
			reference.token());
	}

	private GscAst.Expression callTarget(GscAst.Expression receiver, boolean thread, boolean childThread, GscToken token)
	{
		if (match("[["))
		{
			GscAst.Expression pointer = expression();
			require("]");
			require("]");
			return pointerCall(receiver, pointer, thread, childThread, token);
		}
		GscToken first = require(GscToken.Type.IDENTIFIER, "Expected a called function");
		String path = "";
		String name = first.text();
		if (check("\\") || check("::"))
		{
			GscAst.FunctionReference reference = (GscAst.FunctionReference) qualifiedReference(first);
			path = reference.path();
			name = reference.name();
		}
		return new GscAst.Call(receiver, path, name, null, arguments(), thread, childThread, token);
	}

	private GscAst.Expression pointerCall(GscAst.Expression receiver, GscAst.Expression pointer, boolean thread,
		boolean childThread, GscToken token)
	{
		return new GscAst.Call(receiver, "", "", pointer, arguments(), thread, childThread, token);
	}

	private List<GscAst.Expression> arguments()
	{
		require("(");
		List<GscAst.Expression> arguments = new ArrayList<>();
		if (!check(")"))
		{
			do
			{
				arguments.add(expression());
			}
			while (match(","));
		}
		require(")");
		return List.copyOf(arguments);
	}

	private boolean implicitCallStart()
	{
		int position = index;
		if (tokens.get(position).is("thread") || tokens.get(position).is("childthread"))
		{
			position++;
		}
		if (tokens.get(position).is("[["))
		{
			return true;
		}
		if (tokens.get(position).type() != GscToken.Type.IDENTIFIER)
		{
			return false;
		}
		position++;
		while (position + 1 < tokens.size() && tokens.get(position).is("\\")
			&& tokens.get(position + 1).type() == GscToken.Type.IDENTIFIER)
		{
			position += 2;
		}
		if (position + 1 < tokens.size() && tokens.get(position).is("::")
			&& tokens.get(position + 1).type() == GscToken.Type.IDENTIFIER)
		{
			position += 2;
		}
		return position < tokens.size() && tokens.get(position).is("(");
	}

	private void skipDirective()
	{
		while (!end() && !match(";"))
		{
			advance();
		}
	}

	private boolean match(String... values)
	{
		for (String value : values)
		{
			if (check(value))
			{
				advance();
				return true;
			}
		}
		return false;
	}

	private boolean check(String value)
	{
		return !end() && peek().is(value);
	}

	private GscToken require(String value)
	{
		if (!check(value))
		{
			throw error(peek(), "Expected '" + value + "'");
		}
		return advance();
	}

	private GscToken require(GscToken.Type type, String message)
	{
		if (peek().type() != type)
		{
			throw error(peek(), message);
		}
		return advance();
	}

	private GscToken advance()
	{
		if (!end())
		{
			index++;
		}
		return previous();
	}

	private GscToken peek()
	{
		return tokens.get(index);
	}

	private GscToken previous()
	{
		return tokens.get(index - 1);
	}

	private boolean end()
	{
		return tokens.get(index).type() == GscToken.Type.END;
	}

	private IllegalArgumentException error(GscToken token, String message)
	{
		return new IllegalArgumentException(token.location() + ": " + message + " near '" + token.text() + "'");
	}

	@FunctionalInterface
	private interface ExpressionReader
	{
		GscAst.Expression read();
	}
}
