package openrtm.ui;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class ConsoleClipboard
{
	private static final DataFlavor PNG = new DataFlavor("image/png;class=java.io.InputStream", "PNG Image");

	private ConsoleClipboard()
	{
	}

	static void copy(BufferedImage image) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "png", output))
		{
			throw new IOException("PNG encoding is unavailable");
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(image, output.toByteArray()), null);
	}

	private static final class ImageSelection implements Transferable
	{
		private static final DataFlavor[] FLAVORS = {DataFlavor.imageFlavor, PNG};
		private final Image image;
		private final byte[] png;

		private ImageSelection(Image image, byte[] png)
		{
			this.image = image;
			this.png = png.clone();
		}

		@Override
		public DataFlavor[] getTransferDataFlavors()
		{
			return FLAVORS.clone();
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor)
		{
			return DataFlavor.imageFlavor.equals(flavor) || PNG.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
		{
			if (DataFlavor.imageFlavor.equals(flavor))
			{
				return image;
			}
			if (PNG.equals(flavor))
			{
				return new ByteArrayInputStream(png);
			}
			throw new UnsupportedFlavorException(flavor);
		}
	}
}
