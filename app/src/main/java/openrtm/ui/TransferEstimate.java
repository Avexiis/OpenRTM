package openrtm.ui;

import java.util.ArrayDeque;
import java.util.Locale;

final class TransferEstimate
{
	private static final long WINDOW = 15_000_000_000L;
	private final ArrayDeque<long[]> samples = new ArrayDeque<>();

	void reset()
	{
		samples.clear();
	}

	String describe(long completed, long total)
	{
		long now = System.nanoTime();
		samples.addLast(new long[]{now, completed});
		while (samples.size() > 1 && now - samples.peekFirst()[0] > WINDOW)
		{
			samples.removeFirst();
		}
		long[] first = samples.peekFirst();
		double seconds = (now - first[0]) / 1e9;
		if (seconds < 2 || completed <= first[1])
		{
			return "";
		}
		double rate = (completed - first[1]) / seconds;
		long remaining = Math.max(0, Math.round((total - completed) / rate));
		String time = remaining >= 3600
			? (remaining / 3600) + "h " + (remaining % 3600 / 60) + "m"
			: remaining >= 60 ? (remaining / 60) + "m " + (remaining % 60) + "s" : remaining + "s";
		return time + " left  |  " + String.format(Locale.ROOT, "%.1f MB/s", rate / 1e6) + "  |  ";
	}
}
