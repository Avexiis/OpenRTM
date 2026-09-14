package com.jjrpc;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConsoleFrameCapture
{
	private static final Pattern FIELD = Pattern.compile("(?i)(?:^|\\s)([a-z]+)=((?:0x)?[0-9a-f]+)");
	private final int pitch;
	private final int width;
	private final int height;
	private final byte[] pixels;

	ConsoleFrameCapture(String metadata, byte[] pixels)
	{
		Map<String, Long> values = fields(metadata);
		pitch = bounded(values.getOrDefault("pitch", values.getOrDefault("pitchlo", 0L)), "pitch");
		width = bounded(values.getOrDefault("width", 0L), "width");
		height = bounded(values.getOrDefault("height", 0L), "height");
		this.pixels = pixels == null ? new byte[0] : pixels.clone();
		if (width <= 0 || height <= 0 || this.pixels.length == 0)
		{
			throw new IllegalArgumentException("The console returned incomplete screenshot data");
		}
	}

	static boolean hasMetadata(String value)
	{
		if (value == null || value.indexOf('=') < 0)
		{
			return false;
		}
		String normalized = value.toLowerCase(Locale.ROOT);
		return normalized.contains("pitch=") || normalized.contains("width=")
			|| normalized.contains("height=") || normalized.contains("framebuffersize=")
			|| normalized.contains("format=");
	}

	static int payloadLength(String metadata)
	{
		Map<String, Long> values = fields(metadata);
		long length = values.getOrDefault("framebuffersize", 0L);
		if (length == 0)
		{
			long pitch = values.getOrDefault("pitch", values.getOrDefault("pitchlo", 0L));
			length = pitch * values.getOrDefault("height", 0L);
		}
		if (length == 0)
		{
			length = values.getOrDefault("width", 0L) * values.getOrDefault("height", 0L) * 4L;
		}
		if (length <= 0 || length > 48L * 1024L * 1024L)
		{
			throw new IllegalArgumentException("The console returned an invalid screenshot size");
		}
		return (int) length;
	}

	public BufferedImage toImage()
	{
		int rowPitch = pitch > 0 ? pitch : Math.multiplyExact(width, 4);
		if ((rowPitch & 3) != 0 || rowPitch < width * 4)
		{
			throw new IllegalStateException("The console returned an unsupported screenshot pitch");
		}
		byte[] linear = detile(rowPitch);
		if (linear == null)
		{
			throw new IllegalStateException("The console returned unsupported screenshot data");
		}
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int source = (y * width + x) * 4;
				int blue = linear[source] & 0xFF;
				int green = linear[source + 1] & 0xFF;
				int red = linear[source + 2] & 0xFF;
				image.setRGB(x, y, 0xFF000000 | red << 16 | green << 8 | blue);
			}
		}
		return image;
	}

	private byte[] detile(int rowPitch)
	{
		int pitchPixels = rowPitch / 4;
		int alignedPitch = (pitchPixels + 31) & ~31;
		byte[] linear = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int source = tiledOffset(x, y, alignedPitch);
				if (source < 0 || source + 4 > pixels.length)
				{
					return null;
				}
				System.arraycopy(pixels, source, linear, (y * width + x) * 4, 4);
			}
		}
		return linear;
	}

	private static int tiledOffset(int x, int y, int alignedPitch)
	{
		int outerBlocks = (((y >> 5) * (alignedPitch >> 5)) + (x >> 5)) << 6;
		int innerBlocks = (((y >> 1) & 7) << 3) | (x & 7);
		int combined = (outerBlocks | innerBlocks) << 2;
		int bank = (y >> 4) & 1;
		int pipe = ((x >> 3) & 3) ^ (((y >> 3) & 1) << 1);
		return ((y & 1) << 4)
			| ((pipe & 3) << 6)
			| ((bank & 1) << 11)
			| (combined & 0xF)
			| (((combined >> 4) & 1) << 5)
			| (((combined >> 5) & 7) << 8)
			| ((combined >> 8) << 12);
	}

	private static Map<String, Long> fields(String metadata)
	{
		Map<String, Long> values = new LinkedHashMap<>();
		Matcher matcher = FIELD.matcher(metadata == null ? "" : metadata);
		while (matcher.find())
		{
			String raw = matcher.group(2);
			String digits = raw.startsWith("0x") || raw.startsWith("0X") ? raw.substring(2) : raw;
			try
			{
				values.put(matcher.group(1).toLowerCase(Locale.ROOT), Long.parseUnsignedLong(digits, 16));
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		long pitchHigh = values.getOrDefault("pitchhi", 0L);
		long pitchLow = values.getOrDefault("pitchlo", 0L);
		if (!values.containsKey("pitch") && (pitchHigh != 0 || pitchLow != 0))
		{
			values.put("pitch", pitchHigh << 32 | pitchLow);
		}
		return values;
	}

	private static int bounded(long value, String field)
	{
		if (value < 0 || value > Integer.MAX_VALUE)
		{
			throw new IllegalArgumentException("The console returned an invalid screenshot " + field);
		}
		return (int) value;
	}
}
