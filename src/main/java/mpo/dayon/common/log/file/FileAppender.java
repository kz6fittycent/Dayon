package mpo.dayon.common.log.file;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.log.LogAppender;
import mpo.dayon.common.log.LogLevel;
import mpo.dayon.common.log.console.ConsoleAppender;
import mpo.dayon.common.utils.SystemUtilities;

public class FileAppender extends LogAppender {
	private static final long MAX_FILE_SIZE = 1024 * 1024;

	private static final int MAX_BACKUP_INDEX = 3;

	private final ConsoleAppender fallback = new ConsoleAppender();

	private final String filename;

	private PrintWriter writer;

	private long count;

	private long nextRolloverCount;

	public FileAppender(String filename) throws FileNotFoundException {
		this.filename = filename;
		setupFile(filename, true);
	}

	public synchronized void append(LogLevel level, @Nullable String message, @Nullable Throwable error) {
		try {
			final String info = format(level, message);

			writer.println(info);
			writer.flush();

			count += info.length();

			if (error != null) {
				final String stack = getStackTrace(error);
				writer.println(stack);
				writer.flush();

				count += stack.length();
			}

			if (count >= MAX_FILE_SIZE && count >= nextRolloverCount) {
				rollOver();
			}
		} catch (RuntimeException ex) {
			fallback.append(level, message, error);
			fallback.append(LogLevel.WARN, "[FileAppender] error", ex);
		}
	}

	private String getStackTrace(Throwable error) {
		final StringWriter out = new StringWriter();
		final PrintWriter printer = new PrintWriter(out);

		error.printStackTrace(printer);

		return out.getBuffer().toString();
	}

	private void setupFile(String filename, boolean append) throws FileNotFoundException {
		final FileOutputStream ostream = new FileOutputStream(filename, append);
		writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(ostream)));

		final File file = new File(filename);
		count = file.length();
	}

	private void rollOver() {
		nextRolloverCount = count + MAX_FILE_SIZE;

		boolean renameSucceeded = true;

		// Delete the oldest file ...
		{
			final File file = new File(filename + '.' + MAX_BACKUP_INDEX);
			if (file.exists()) {
				renameSucceeded = file.delete();
			}
		}

		// Rename { .1, .2, ..., .MAX_BACKUP_INDEX-1 } to { .2., .3,
		// ...,.MAX_BACKUP_INDEX }
		for (int idx = MAX_BACKUP_INDEX - 1; idx >= 1 && renameSucceeded; idx--) {
			final File file = new File(filename + "." + idx);
			if (file.exists()) {
				final File target = new File(filename + '.' + (idx + 1));
				renameSucceeded = file.renameTo(target);
			}
		}

		// Rename fileName to fileName.1
		if (renameSucceeded) {
			SystemUtilities.safeClose(writer);
			renameSucceeded = new File(filename).renameTo(new File(filename + "." + 1));

			if (!renameSucceeded) {
				try {
					setupFile(filename, true);
				} catch (IOException ex) {
					ex.printStackTrace(System.err);
				}
			}
		}

		if (renameSucceeded) {
			try {
				setupFile(filename, false);
				nextRolloverCount = 0;
			} catch (IOException ex) {
				ex.printStackTrace(System.err);
			}
		}
	}

}