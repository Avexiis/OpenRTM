package openrtm.cod;

import java.util.List;

final class CodPresets
{
	static final List<String> MULTIPLIERS = List.of("1x (Default)", "2x", "3x", "4x", "5x");
	static final List<String> GRAVITY = List.of("100% (Default)", "50%", "25%", "10%");
	static final List<String> FALL_DAMAGE = List.of("100% (Default)", "50%", "25%", "10%", "0%");
	static final List<String> FRICTION = List.of("100% (Default)", "75%", "50%", "25%", "0%");
	static final List<String> GAME_SPEED = List.of("100% (Default)", "50%", "75%", "125%", "150%", "200%");

	private CodPresets()
	{
	}

	static long multiply(String value, long base)
	{
		return base * switch (value)
		{
			case "2x" -> 2;
			case "3x" -> 3;
			case "4x" -> 4;
			case "5x" -> 5;
			default -> 1;
		};
	}

	static long percentage(String value, long base)
	{
		return Math.round(base * percent(value) / 100.0);
	}

	static int percent(String value)
	{
		String digits = value.replaceAll("[^0-9]", "");
		return digits.isEmpty() ? 100 : Integer.parseInt(digits);
	}
}
