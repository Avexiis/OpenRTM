package openrtm.cod.gsc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class Iw4GscTables
{
	private static final Map<String, Integer> FUNCTIONS = new LinkedHashMap<>();
	private static final Map<String, Integer> METHODS = new LinkedHashMap<>();
	private static final Map<String, Integer> TOKENS = new LinkedHashMap<>();
	private static final Map<String, Integer> STOCK_CALLS = new LinkedHashMap<>();
	private static final Map<String, Integer> FAR_CALLS = new LinkedHashMap<>();

	static
	{
		loadBuiltins();
		loadTokens();
		loadCalls("/openrtm/cod/iw4-stock-calls.tsv", STOCK_CALLS);
		loadCalls("/openrtm/cod/iw4-far-calls.tsv", FAR_CALLS);
	}

	static int function(String name)
	{
		return FUNCTIONS.getOrDefault(normalize(name), -1);
	}

	static int method(String name)
	{
		return METHODS.getOrDefault(normalize(name), -1);
	}

	static int token(String name)
	{
		return TOKENS.getOrDefault(normalize(name), -1);
	}

	static Set<String> completionNames()
	{
		Set<String> names = new LinkedHashSet<>(FUNCTIONS.keySet());
		names.addAll(METHODS.keySet());
		names.addAll(STOCK_CALLS.keySet());
		names.addAll(FAR_CALLS.keySet());
		names.addAll(Set.of("ReadByte", "WriteByte", "ReadString", "WriteString", "RPC"));
		return Set.copyOf(names);
	}

	static Integer stockCall(String name)
	{
		return STOCK_CALLS.get(normalize(name));
	}

	static Integer farCall(String name)
	{
		return FAR_CALLS.get(normalize(name));
	}

	private static void loadCalls(String resource, Map<String, Integer> target)
	{
		read(resource, values -> {
			int operand = Integer.parseUnsignedInt(values[0], 16);
			if (operand != 0xF1F1F1)
			{
				target.put(normalize(values[1]), operand);
			}
		});
	}

	private static void loadBuiltins()
	{
		read("/openrtm/cod/iw4-builtins.tsv", values -> {
			Map<String, Integer> target = "f".equals(values[0]) ? FUNCTIONS : METHODS;
			target.putIfAbsent(normalize(values[2]), Integer.decode(values[1]));
		});
	}

	private static void loadTokens()
	{
		read("/openrtm/cod/iw4-tokens.tsv",
			values -> TOKENS.putIfAbsent(normalize(values[1]), Integer.decode(values[0])));
	}

	private static void read(String resource, LineReader reader)
	{
		InputStream input = Iw4GscTables.class.getResourceAsStream(resource);
		if (input == null)
		{
			throw new ExceptionInInitializerError("Missing GSC table " + resource);
		}
		try (BufferedReader lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII)))
		{
			String line;
			while ((line = lines.readLine()) != null)
			{
				if (!line.isBlank())
				{
					reader.read(line.split("\\t"));
				}
			}
		}
		catch (IOException failure)
		{
			throw new ExceptionInInitializerError(failure);
		}
	}

	private static String normalize(String value)
	{
		return value.toLowerCase(Locale.ROOT);
	}

	private Iw4GscTables()
	{
	}

	@FunctionalInterface
	private interface LineReader
	{
		void read(String[] values);
	}
}
