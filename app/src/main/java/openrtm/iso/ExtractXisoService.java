package openrtm.iso;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class ExtractXisoService
{
	private static final String RESOURCE = "/openrtm/iso/extract-xiso";

	private volatile Process process;
	private volatile boolean cancelRequested;
	private Path executable;

	public void run(Request request, Consumer<String> output) throws IOException, InterruptedException
	{
		if (process != null)
		{
			throw new IllegalStateException("extract-xiso is already running");
		}
		validate(request);

		ProcessBuilder builder = new ProcessBuilder(arguments(executable(), request));
		builder.redirectErrorStream(true);
		cancelRequested = false;
		Process started = builder.start();
		process = started;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				output.accept(line + System.lineSeparator());
			}
			int exitCode = started.waitFor();
			if (cancelRequested)
			{
				output.accept("Cancelled" + System.lineSeparator());
			}
			else if (exitCode != 0)
			{
				throw new IOException("extract-xiso exited with code " + exitCode);
			}
		}
		finally
		{
			process = null;
		}
	}

	public void cancel()
	{
		cancelRequested = true;
		Process running = process;
		if (running != null)
		{
			running.destroy();
		}
	}

	static List<String> arguments(Path executable, Request request)
	{
		List<String> command = new ArrayList<>();
		command.add(executable.toString());
		if (request.skipSystemUpdate() && request.mode() != Mode.LIST)
		{
			command.add("-s");
		}
		if (request.disableMediaPatch() && (request.mode() == Mode.CREATE || request.mode() == Mode.REWRITE))
		{
			command.add("-m");
		}

		switch (request.mode())
		{
			case EXTRACT ->
			{
				command.add("-x");
				command.add("-d");
				command.add(request.output().toString());
				command.add(request.source().toString());
			}
			case LIST ->
			{
				command.add("-l");
				command.add(request.source().toString());
			}
			case CREATE ->
			{
				command.add("-c");
				command.add(request.source().toString());
				command.add(request.output().toString());
			}
			case REWRITE ->
			{
				command.add("-r");
				if (request.deleteOriginal())
				{
					command.add("-D");
				}
				command.add("-d");
				command.add(request.output().toString());
				command.add(request.source().toString());
			}
		}
		return command;
	}

	synchronized Path executable() throws IOException
	{
		if (executable != null && Files.isRegularFile(executable))
		{
			return executable;
		}
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (!os.contains("linux"))
		{
			throw new IOException("The bundled extract-xiso binary supports Linux x86-64 only");
		}
		String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		if (!architecture.equals("amd64") && !architecture.equals("x86_64"))
		{
			throw new IOException("The bundled extract-xiso binary requires an x86-64 processor");
		}

		Path extracted = Files.createTempFile("openrtm-extract-xiso-", "");
		try (InputStream source = ExtractXisoService.class.getResourceAsStream(RESOURCE))
		{
			if (source == null)
			{
				throw new IOException("Missing bundled resource " + RESOURCE);
			}
			Files.copy(source, extracted, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (Throwable failure)
		{
			Files.deleteIfExists(extracted);
			throw failure;
		}

		try
		{
			Set<PosixFilePermission> permissions = EnumSet.of(
				PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE,
				PosixFilePermission.OWNER_EXECUTE);
			Files.setPosixFilePermissions(extracted, permissions);
		}
		catch (UnsupportedOperationException ignored)
		{
			if (!extracted.toFile().setExecutable(true, true))
			{
				Files.deleteIfExists(extracted);
				throw new IOException("Could not make the bundled extract-xiso binary executable");
			}
		}
		extracted.toFile().deleteOnExit();
		executable = extracted;
		return extracted;
	}

	private static void validate(Request request) throws IOException
	{
		if (request == null || request.mode() == null)
		{
			throw new IllegalArgumentException("Select an operation");
		}
		if (request.source() == null)
		{
			throw new IllegalArgumentException("Select an input path");
		}
		if (request.mode() == Mode.CREATE)
		{
			if (!Files.isDirectory(request.source()))
			{
				throw new IOException("Input directory does not exist: " + request.source());
			}
		}
		else if (!Files.isRegularFile(request.source()))
		{
			throw new IOException("Input ISO does not exist: " + request.source());
		}
		if (request.mode() != Mode.LIST && request.output() == null)
		{
			throw new IllegalArgumentException("Select an output path");
		}
		if ((request.mode() == Mode.EXTRACT || request.mode() == Mode.REWRITE)
			&& !Files.isDirectory(request.output()))
		{
			throw new IOException("Output directory does not exist: " + request.output());
		}
		if (request.mode() == Mode.CREATE)
		{
			Path parent = request.output().toAbsolutePath().getParent();
			if (parent != null && !Files.isDirectory(parent))
			{
				throw new IOException("Output directory does not exist: " + parent);
			}
		}
	}

	public enum Mode
	{
		EXTRACT("Extract"),
		LIST("List"),
		CREATE("Create / Pack"),
		REWRITE("Rewrite");

		private final String label;

		Mode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public record Request(Mode mode, Path source, Path output, boolean skipSystemUpdate,
	                      boolean disableMediaPatch, boolean deleteOriginal)
	{
	}
}
