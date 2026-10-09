package openrtm.cod.gsc;

import java.util.ArrayList;
import java.util.List;

final class GscLexer
{
	private static final List<String> OPERATORS = List.of(
		"[[", "::", "++", "--", "+=", "-=", "*=", "/=", "%=", "==", "!=", "<=", ">=",
		"&&", "||", "<<", ">>", "&=", "|=", "^=");
	private final String source;
	private final String file;
	private final List<GscToken> tokens = new ArrayList<>();
	private int index;
	private int line = 1;
	private int column = 1;

	GscLexer(String source, String file)
	{
		this.source = source.startsWith("\uFEFF") ? source.substring(1) : source;
		this.file = file;
	}

	List<GscToken> lex()
	{
		while (!end())
		{
			skipSpaceAndComments();
			if (end())
			{
				break;
			}
			int startLine = line;
			int startColumn = column;
			char value = peek();
			if (Character.isLetter(value) || value == '_')
			{
				identifier(startLine, startColumn);
			}
			else if (Character.isDigit(value) || value == '.' && Character.isDigit(peek(1)))
			{
				number(startLine, startColumn);
			}
			else if (value == '"')
			{
				string(startLine, startColumn);
			}
			else
			{
				symbol(startLine, startColumn);
			}
		}
		tokens.add(new GscToken(GscToken.Type.END, "", file, line, column));
		return List.copyOf(tokens);
	}

	private void skipSpaceAndComments()
	{
		boolean changed;
		do
		{
			changed = false;
			while (!end() && Character.isWhitespace(peek()))
			{
				advance();
				changed = true;
			}
			if (matches("//"))
			{
				while (!end() && peek() != '\n')
				{
					advance();
				}
				changed = true;
			}
			else if (matches("/*"))
			{
				advance();
				advance();
				while (!end() && !matches("*/"))
				{
					advance();
				}
				if (end())
				{
					throw error("Unterminated block comment");
				}
				advance();
				advance();
				changed = true;
			}
			else if (matches("/#"))
			{
				advance();
				advance();
				while (!end() && !matches("#/"))
				{
					advance();
				}
				if (end())
				{
					throw error("Unterminated developer block");
				}
				advance();
				advance();
				changed = true;
			}
		}
		while (changed);
	}

	private void identifier(int startLine, int startColumn)
	{
		int start = index;
		while (!end() && (Character.isLetterOrDigit(peek()) || peek() == '_'))
		{
			advance();
		}
		add(GscToken.Type.IDENTIFIER, source.substring(start, index), startLine, startColumn);
	}

	private void number(int startLine, int startColumn)
	{
		int start = index;
		if (matches("0x") || matches("0X"))
		{
			advance();
			advance();
			while (!end() && Character.digit(peek(), 16) >= 0)
			{
				advance();
			}
		}
		else
		{
			while (!end() && Character.isDigit(peek()))
			{
				advance();
			}
			if (!end() && peek() == '.')
			{
				advance();
				while (!end() && Character.isDigit(peek()))
				{
					advance();
				}
			}
			if (!end() && (peek() == 'e' || peek() == 'E'))
			{
				advance();
				if (!end() && (peek() == '+' || peek() == '-'))
				{
					advance();
				}
				while (!end() && Character.isDigit(peek()))
				{
					advance();
				}
			}
		}
		add(GscToken.Type.NUMBER, source.substring(start, index), startLine, startColumn);
	}

	private void string(int startLine, int startColumn)
	{
		advance();
		StringBuilder value = new StringBuilder();
		while (!end() && peek() != '"')
		{
			char current = advance();
			if (current == '\\' && !end())
			{
				char escaped = advance();
				value.append(switch (escaped)
				{
					case 'n' -> '\n';
					case 'r' -> '\r';
					case 't' -> '\t';
					case '"' -> '"';
					default -> escaped;
				});
			}
			else
			{
				value.append(current);
			}
		}
		if (end())
		{
			throw error("Unterminated string");
		}
		advance();
		add(GscToken.Type.STRING, value.toString(), startLine, startColumn);
	}

	private void symbol(int startLine, int startColumn)
	{
		for (String operator : OPERATORS)
		{
			if (matches(operator))
			{
				for (int i = 0; i < operator.length(); i++)
				{
					advance();
				}
				add(GscToken.Type.SYMBOL, operator, startLine, startColumn);
				return;
			}
		}
		add(GscToken.Type.SYMBOL, Character.toString(advance()), startLine, startColumn);
	}

	private void add(GscToken.Type type, String text, int startLine, int startColumn)
	{
		tokens.add(new GscToken(type, text, file, startLine, startColumn));
	}

	private boolean matches(String value)
	{
		return source.regionMatches(index, value, 0, value.length());
	}

	private char peek()
	{
		return peek(0);
	}

	private char peek(int distance)
	{
		int target = index + distance;
		return target < source.length() ? source.charAt(target) : '\0';
	}

	private char advance()
	{
		char value = source.charAt(index++);
		if (value == '\n')
		{
			line++;
			column = 1;
		}
		else
		{
			column++;
		}
		return value;
	}

	private boolean end()
	{
		return index >= source.length();
	}

	private IllegalArgumentException error(String message)
	{
		return new IllegalArgumentException(file + ":" + line + ":" + column + ": " + message);
	}
}
