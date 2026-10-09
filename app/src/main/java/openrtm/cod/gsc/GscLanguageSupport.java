package openrtm.cod.gsc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GscLanguageSupport
{
	public static final Set<String> KEYWORDS = Set.of("if", "else", "for", "foreach", "while", "switch",
		"case", "default", "break", "continue", "return", "thread", "childthread", "wait", "waittill",
		"waittillmatch", "waittillframeend", "notify", "endon", "true", "false", "undefined",
		"self", "level", "game", "anim", "thisthread");
	private static final Pattern ERROR = Pattern.compile("(?s)^(.+):(\\d+):(\\d+): (.*)$");
	private static final Pattern FUNCTION = Pattern.compile("(?m)^\\s*([A-Za-z_][A-Za-z_0-9]*)\\s*\\([^;{}]*\\)\\s*\\{");

	public record Diagnostic(String file, int line, int column, String message)
	{
	}

	private GscLanguageSupport()
	{
	}

	public static Diagnostic diagnostic(String fallback, Exception failure)
	{
		String message = failure.getMessage() == null ? "Could not check source" : failure.getMessage();
		Matcher matcher = ERROR.matcher(message);
		return matcher.matches()
			? new Diagnostic(matcher.group(1), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)), matcher.group(4))
			: new Diagnostic(fallback, 1, 1, message);
	}

	public static List<Diagnostic> syntax(String file, String source)
	{
		try
		{
			new GscParser(new GscLexer(source, file).lex()).parse();
			return List.of();
		}
		catch (IllegalArgumentException failure)
		{
			return List.of(diagnostic(file, failure));
		}
	}

	public static Set<String> completions(Map<String, String> sources)
	{
		Set<String> names = new LinkedHashSet<>(KEYWORDS);
		names.addAll(Iw4GscTables.completionNames());
		for (String source : sources.values())
		{
			Matcher matcher = FUNCTION.matcher(source);
			while (matcher.find())
			{
				names.add(matcher.group(1));
			}
		}
		return names;
	}

	public static String format(String source)
	{
		List<GscToken> before = new GscLexer(source, "source").lex();
		List<String> pieces = pieces(source);
		StringBuilder result = new StringBuilder();
		int indent = 0;
		int parentheses = 0;
		String previous = "";
		for (int index = 0; index < pieces.size(); index++)
		{
			String piece = pieces.get(index);
			String next = index + 1 < pieces.size() ? pieces.get(index + 1) : "";
			if (piece.startsWith("#") || piece.startsWith("//") || piece.startsWith("/*") || piece.startsWith("/#"))
			{
				if (piece.startsWith("#"))
				{
					newline(result);
				}
				else
				{
					space(result);
				}
				append(result, piece, indent);
				newline(result);
			}
			else if (piece.equals("{"))
			{
				newline(result);
				append(result, piece, indent++);
				newline(result);
			}
			else if (piece.equals("}"))
			{
				indent = Math.max(0, indent - 1);
				newline(result);
				append(result, piece, indent);
				if (!Set.of(";", ",", ")", "]").contains(next))
				{
					newline(result);
				}
			}
			else if (piece.equals(";"))
			{
				trimSpace(result);
				append(result, piece, indent);
				if (parentheses == 0)
				{
					newline(result);
				}
				else
				{
					space(result);
				}
			}
			else if (piece.equals("("))
			{
				if (Set.of("if", "for", "foreach", "while", "switch").contains(previous))
				{
					space(result);
				}
				append(result, piece, indent);
				parentheses++;
			}
			else if (Set.of(")", "]", ",", ":").contains(piece))
			{
				trimSpace(result);
				append(result, piece, indent);
				if (piece.equals(")"))
				{
					parentheses--;
				}
				else if (piece.equals(":"))
				{
					newline(result);
				}
				else if (piece.equals(","))
				{
					space(result);
				}
			}
			else if (Set.of(".", "::", "\\", "[", "++", "--").contains(piece))
			{
				trimSpace(result);
				append(result, piece, indent);
			}
			else if (piece.matches("[=+*/%<>!&|^?-]+"))
			{
				space(result);
				append(result, piece, indent);
				space(result);
			}
			else
			{
				if (!previous.isEmpty() && !Set.of("(", "[", ".", "::", "\\").contains(previous))
				{
					space(result);
				}
				append(result, piece, indent);
			}
			previous = piece;
		}
		newline(result);
		String formatted = result.toString();
		List<GscToken> after = new GscLexer(formatted, "source").lex();
		if (before.size() != after.size())
		{
			throw new IllegalArgumentException("Formatting would change the source; no changes were applied");
		}
		for (int index = 0; index < before.size(); index++)
		{
			if (before.get(index).type() != after.get(index).type() || !before.get(index).text().equals(after.get(index).text()))
			{
				throw new IllegalArgumentException("Formatting would change the source; no changes were applied");
			}
		}
		return formatted;
	}

	private static List<String> pieces(String source)
	{
		List<String> pieces = new ArrayList<>();
		for (int index = 0; index < source.length(); )
		{
			if (Character.isWhitespace(source.charAt(index)))
			{
				index++;
				continue;
			}
			int start = index++;
			char first = source.charAt(start);
			if (source.startsWith("//", start) || first == '#')
			{
				while (index < source.length() && source.charAt(index) != '\n' && source.charAt(index) != '\r')
				{
					index++;
				}
			}
			else if (source.startsWith("/*", start) || source.startsWith("/#", start))
			{
				int end = source.indexOf(source.startsWith("/*", start) ? "*/" : "#/", start + 2);
				index = end < 0 ? source.length() : end + 2;
			}
			else if (first == '"')
			{
				while (index < source.length())
				{
					char value = source.charAt(index++);
					if (value == '\\' && index < source.length())
					{
						index++;
					}
					else if (value == '"')
					{
						break;
					}
				}
			}
			else if (Character.isDigit(first) || first == '.' && index < source.length() && Character.isDigit(source.charAt(index)))
			{
				if (first == '0' && index < source.length() && (source.charAt(index) == 'x' || source.charAt(index) == 'X'))
				{
					index++;
					while (index < source.length() && Character.digit(source.charAt(index), 16) >= 0)
					{
						index++;
					}
				}
				else
				{
					while (index < source.length() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.'))
					{
						index++;
					}
					if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E'))
					{
						index++;
						if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-'))
						{
							index++;
						}
						while (index < source.length() && Character.isDigit(source.charAt(index)))
						{
							index++;
						}
					}
				}
			}
			else if (Character.isLetter(first) || first == '_')
			{
				while (index < source.length() && (Character.isLetterOrDigit(source.charAt(index)) || source.charAt(index) == '_'))
				{
					index++;
				}
			}
			else if (index < source.length() && Set.of("::", "++", "--", "+=", "-=", "*=", "/=", "%=", "==", "!=",
				"<=", ">=", "&&", "||", "<<", ">>", "&=", "|=", "^=").contains(source.substring(start, index + 1)))
			{
				index++;
			}
			pieces.add(source.substring(start, index));
		}
		return pieces;
	}

	private static void append(StringBuilder text, String piece, int indent)
	{
		if (text.length() == 0 || text.charAt(text.length() - 1) == '\n')
		{
			text.append("\t".repeat(indent));
		}
		text.append(piece);
	}

	private static void space(StringBuilder text)
	{
		if (text.length() > 0 && !Character.isWhitespace(text.charAt(text.length() - 1)))
		{
			text.append(' ');
		}
	}

	private static void trimSpace(StringBuilder text)
	{
		while (text.length() > 0 && (text.charAt(text.length() - 1) == ' ' || text.charAt(text.length() - 1) == '\t'))
		{
			text.setLength(text.length() - 1);
		}
	}

	private static void newline(StringBuilder text)
	{
		trimSpace(text);
		if (text.length() > 0 && text.charAt(text.length() - 1) != '\n')
		{
			text.append('\n');
		}
	}
}
