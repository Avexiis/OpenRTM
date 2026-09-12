package openrtm.profile;

import openrtm.profile.BioPresetCatalog.BioPreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BioSymbolCatalog
{
	private static final String FRAMES = "─━│┃┌┐└┘┏┓┗┛├┤┬┴┼┣┫┳┻╋╭╮╰╯═║╔╗╚╝╠╣╦╩╬■□▪▫●○◆◇";
	private static final String ARROWS = "←↑→↓↔↕↖↗↘↙⇐⇑⇒⇓⇔";
	private static final String NUMBERS = "0123456789①②③④⑤⑥⑦⑧⑨⑩⓪❶❷❸❹❺❻❼❽❾";
	private static final String MISCELLANEOUS = "★☆✦✧♥♡♦♢♣♧♠♤♪♫©®™°±×÷∞€£¥¢♂♀✓✔✕✖◎ㅁ";

	private BioSymbolCatalog()
	{
	}

	public static Map<String, List<String>> create(List<BioPreset> presets)
	{
		Set<String> frames = symbols(FRAMES);
		Set<String> arrows = symbols(ARROWS);
		Set<String> numbers = symbols(NUMBERS);
		Set<String> miscellaneous = symbols(MISCELLANEOUS);
		for (BioPreset preset : presets)
		{
			preset.content().codePoints().forEach(codePoint -> addUsedSymbol(codePoint, frames, arrows,
				numbers, miscellaneous));
		}
		Map<String, List<String>> categories = new LinkedHashMap<>();
		categories.put("Miscellaneous", immutableList(miscellaneous));
		categories.put("Frames", immutableList(frames));
		categories.put("Arrows", immutableList(arrows));
		categories.put("Numbers", immutableList(numbers));
		return Collections.unmodifiableMap(categories);
	}

	private static void addUsedSymbol(int codePoint, Set<String> frames, Set<String> arrows,
	                                  Set<String> numbers, Set<String> miscellaneous)
	{
		if (Character.isWhitespace(codePoint) || codePoint == '\n' || codePoint == '\r')
		{
			return;
		}
		String symbol = new String(Character.toChars(codePoint));
		if (codePoint >= 0x2500 && codePoint <= 0x259F)
		{
			frames.add(symbol);
		}
		else if (codePoint >= 0x2190 && codePoint <= 0x21FF)
		{
			arrows.add(symbol);
		}
		else if (Character.isDigit(codePoint))
		{
			numbers.add(symbol);
		}
		else if (!isAsciiLetter(codePoint))
		{
			miscellaneous.add(symbol);
		}
	}

	private static boolean isAsciiLetter(int codePoint)
	{
		return codePoint >= 'A' && codePoint <= 'Z' || codePoint >= 'a' && codePoint <= 'z';
	}

	private static Set<String> symbols(String value)
	{
		Set<String> symbols = new LinkedHashSet<>();
		value.codePoints().forEach(codePoint -> symbols.add(new String(Character.toChars(codePoint))));
		return symbols;
	}

	private static List<String> immutableList(Set<String> symbols)
	{
		return Collections.unmodifiableList(new ArrayList<>(symbols));
	}
}
