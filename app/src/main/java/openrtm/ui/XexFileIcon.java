package openrtm.ui;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class XexFileIcon
{
	private static final String RESOURCE = "/openrtm/images/xexfile.ico";

	private XexFileIcon()
	{
	}

	static Icon load(int requestedSize)
	{
		try (InputStream input = XexFileIcon.class.getResourceAsStream(RESOURCE))
		{
			if (input == null)
			{
				return fallback();
			}
			BufferedImage image = decode(input.readAllBytes(), requestedSize);
			return image == null ? fallback() : new ImageIcon(image);
		}
		catch (IOException | RuntimeException failure)
		{
			return fallback();
		}
	}

	private static BufferedImage decode(byte[] data, int requestedSize)
	{
		ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		if (data.length < 22 || Short.toUnsignedInt(buffer.getShort()) != 0
			|| Short.toUnsignedInt(buffer.getShort()) != 1)
		{
			return null;
		}
		int count = Short.toUnsignedInt(buffer.getShort());
		int selectedOffset = -1;
		int selectedWidth = 0;
		int selectedHeight = 0;
		int selectedDistance = Integer.MAX_VALUE;
		for (int index = 0; index < count && buffer.remaining() >= 16; index++)
		{
			int width = Byte.toUnsignedInt(buffer.get());
			int height = Byte.toUnsignedInt(buffer.get());
			width = width == 0 ? 256 : width;
			height = height == 0 ? 256 : height;
			buffer.position(buffer.position() + 4);
			int bitsPerPixel = Short.toUnsignedInt(buffer.getShort());
			int length = buffer.getInt();
			int offset = buffer.getInt();
			int distance = Math.abs(width - requestedSize) + Math.abs(height - requestedSize);
			if (bitsPerPixel == 32 && length > 0 && offset >= 0 && offset + length <= data.length
				&& distance < selectedDistance)
			{
				selectedOffset = offset;
				selectedWidth = width;
				selectedHeight = height;
				selectedDistance = distance;
			}
		}
		return selectedOffset < 0 ? null : decodeBitmap(data, selectedOffset, selectedWidth, selectedHeight);
	}

	private static BufferedImage decodeBitmap(byte[] data, int offset, int width, int height)
	{
		ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		buffer.position(offset);
		int headerSize = buffer.getInt();
		if (headerSize < 40 || offset + headerSize > data.length)
		{
			return null;
		}
		int bitmapWidth = buffer.getInt();
		int bitmapHeight = Math.abs(buffer.getInt()) / 2;
		int planes = Short.toUnsignedInt(buffer.getShort());
		int bitsPerPixel = Short.toUnsignedInt(buffer.getShort());
		int compression = buffer.getInt();
		if (bitmapWidth != width || bitmapHeight != height || planes != 1 || bitsPerPixel != 32 || compression != 0)
		{
			return null;
		}
		int pixelOffset = offset + headerSize;
		int rowStride = width * 4;
		long pixelEnd = (long) pixelOffset + (long) rowStride * height;
		if (pixelEnd > data.length)
		{
			return null;
		}
		boolean hasAlpha = false;
		for (int position = pixelOffset + 3; position < pixelEnd; position += 4)
		{
			if (data[position] != 0)
			{
				hasAlpha = true;
				break;
			}
		}
		int maskOffset = (int) pixelEnd;
		int maskStride = (width + 31) / 32 * 4;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++)
		{
			int sourceY = height - 1 - y;
			for (int x = 0; x < width; x++)
			{
				int source = pixelOffset + sourceY * rowStride + x * 4;
				int blue = data[source] & 0xFF;
				int green = data[source + 1] & 0xFF;
				int red = data[source + 2] & 0xFF;
				int alpha = hasAlpha ? data[source + 3] & 0xFF : maskAlpha(data, maskOffset, maskStride, sourceY, x);
				image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
			}
		}
		return image;
	}

	private static int maskAlpha(byte[] data, int offset, int stride, int y, int x)
	{
		int position = offset + y * stride + x / 8;
		if (position < 0 || position >= data.length)
		{
			return 0xFF;
		}
		return (data[position] & 1 << 7 - x % 8) == 0 ? 0xFF : 0;
	}

	private static Icon fallback()
	{
		return UIManager.getIcon("FileView.fileIcon");
	}
}
