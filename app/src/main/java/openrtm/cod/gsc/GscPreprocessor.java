package openrtm.cod.gsc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class GscPreprocessor
{
	record Result(Map<String, List<GscToken>> sources, Map<String, List<GscToken>> definitions)
	{
	}

	private record Conditional(boolean parent, boolean active, boolean matched, boolean alternate, GscToken token)
	{
	}

	private final Map<String, List<GscToken>> definitions = new LinkedHashMap<>();

	private GscPreprocessor(Set<String> symbols)
	{
		for (String symbol : Set.of("MW2", "IW4", "MP", "XBOX", "XENON", "CONSOLE"))
		{
			defineSymbol(symbol);
		}
		for (String symbol : symbols)
		{
			defineSymbol(symbol);
		}
		if (!definitions.containsKey("debug"))
		{
			defineSymbol("RELEASE");
		}
	}

	static Result process(Map<String, String> sources, Set<String> symbols)
	{
		GscPreprocessor preprocessor = new GscPreprocessor(symbols);
		Map<String, List<GscToken>> result = new LinkedHashMap<>();
		List<String> ordered = new ArrayList<>(sources.keySet());
		ordered.sort(Comparator.comparingInt(GscPreprocessor::priority));
		for (String file : ordered)
		{
			result.put(file, preprocessor.process(new GscLexer(sources.get(file), file).lex()));
		}
		Map<String, List<GscToken>> originalOrder = new LinkedHashMap<>();
		for (String file : sources.keySet())
		{
			originalOrder.put(file, result.get(file));
		}
		return new Result(originalOrder, Map.copyOf(preprocessor.definitions));
	}

	private static int priority(String file)
	{
		String path = file.replace('\\', '/').toLowerCase(Locale.ROOT);
		if (path.equals("main.gsc"))
		{
			return 0;
		}
		return path.endsWith("/main.gsc") ? 1 + (int) path.chars().filter(value -> value == '/').count() : 1000;
	}

	private void defineSymbol(String name)
	{
		definitions.put(name.toLowerCase(Locale.ROOT), List.of(new GscToken(GscToken.Type.NUMBER, "1", "target", 1, 1)));
	}

	private List<GscToken> process(List<GscToken> tokens)
	{
		List<GscToken> result = new ArrayList<>();
		Deque<Conditional> conditions = new ArrayDeque<>();
		for (int index = 0; index < tokens.size() - 1;)
		{
			GscToken token = tokens.get(index);
			boolean active = conditions.isEmpty() || conditions.peek().active();
			if (!token.is("#"))
			{
				if (active)
				{
					result.add(token);
				}
				index++;
				continue;
			}
			if (index + 1 >= tokens.size() || tokens.get(index + 1).type() != GscToken.Type.IDENTIFIER)
			{
				throw error(token, "Expected a directive name after '#'");
			}
			String directive = tokens.get(index + 1).text().toLowerCase(Locale.ROOT);
			int end = index + 2;
			boolean terminated = directive.equals("include") || directive.equals("using_animtree")
				|| directive.equals("define") && index + 3 < tokens.size() && tokens.get(index + 3).is("=");
			while (end < tokens.size() - 1 && !tokens.get(end).is("#")
				&& (terminated || tokens.get(end).line() == token.line()) && !tokens.get(end).is(";"))
			{
				end++;
			}
			List<GscToken> arguments = tokens.subList(index + 2, end);
			boolean semicolon = end < tokens.size() - 1 && tokens.get(end).is(";");
			int next = semicolon ? end + 1 : end;
			switch (directive)
			{
				case "ifdef", "ifndef" -> {
					String name = symbol(arguments, token);
					boolean enabled = definitions.containsKey(name) == directive.equals("ifdef");
					conditions.push(new Conditional(active, active && enabled, enabled, false, token));
				}
				case "else" -> {
					noArguments(arguments, token);
					Conditional previous = conditional(conditions, token);
					if (previous.alternate())
					{
						throw error(token, "Duplicate #else");
					}
					conditions.push(new Conditional(previous.parent(), previous.parent() && !previous.matched(), true, true, previous.token()));
				}
				case "endif" -> {
					noArguments(arguments, token);
					conditional(conditions, token);
				}
				case "define" -> {
					if (active)
					{
						if (terminated && !semicolon)
						{
							throw error(token, "Expected ';' after #define assignment");
						}
						define(arguments, token);
					}
				}
				case "include", "using_animtree" -> {
					if (active)
					{
						if (!semicolon)
						{
							throw error(token, "Expected ';' after #" + directive);
						}
						result.addAll(tokens.subList(index, next));
					}
				}
				default -> {
					if (active)
					{
						throw error(token, "Unsupported directive '#" + directive + "'");
					}
				}
			}
			index = next;
		}
		if (!conditions.isEmpty())
		{
			throw error(conditions.peek().token(), "Missing #endif");
		}
		result.add(tokens.get(tokens.size() - 1));
		return List.copyOf(result);
	}

	private void define(List<GscToken> arguments, GscToken token)
	{
		if (arguments.isEmpty() || arguments.get(0).type() != GscToken.Type.IDENTIFIER)
		{
			throw error(token, "Expected a definition name");
		}
		String name = arguments.get(0).text().toLowerCase(Locale.ROOT);
		int start = arguments.size() > 1 && arguments.get(1).is("=") ? 2 : 1;
		if (start == arguments.size())
		{
			if (start == 2)
			{
				throw error(token, "Expected a value for definition '" + name + "'");
			}
			defineSymbol(name);
		}
		else
		{
			definitions.put(name, List.copyOf(arguments.subList(start, arguments.size())));
		}
	}

	private static String symbol(List<GscToken> arguments, GscToken token)
	{
		if (arguments.size() != 1 || arguments.get(0).type() != GscToken.Type.IDENTIFIER)
		{
			throw error(token, "Expected one symbol name");
		}
		return arguments.get(0).text().toLowerCase(Locale.ROOT);
	}

	private static void noArguments(List<GscToken> arguments, GscToken token)
	{
		if (!arguments.isEmpty())
		{
			throw error(token, "Unexpected text after directive");
		}
	}

	private static Conditional conditional(Deque<Conditional> conditions, GscToken token)
	{
		if (conditions.isEmpty())
		{
			throw error(token, "Directive requires an enclosing #ifdef or #ifndef");
		}
		return conditions.pop();
	}

	private static IllegalArgumentException error(GscToken token, String message)
	{
		return new IllegalArgumentException(token.location() + ": " + message);
	}
}
