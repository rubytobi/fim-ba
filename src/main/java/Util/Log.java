package Util;

import java.util.UUID;

public class Log {
	private boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
			.indexOf("jdwp") >= 0;

	private boolean isDebug() {
		return isDebug;
	}

	public static void d(UUID uuid, String s) {
		if (!new Log().isDebug()) {
			return;
		}

		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		String string = stack[2].toString();

		// remove package name
		string = string.substring(string.indexOf(".") + 1);

		// remove class name
		string = string.substring(string.indexOf(".") + 1);

		System.out.println(DateTime.timestamp() + "\t" + uuid + "\t" + string + "\t" + s);
	}

	public static void e(UUID uuid, String s) {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		String string = stack[2].toString();

		// remove package name
		string = string.substring(string.indexOf(".") + 1);

		// remove class name
		string = string.substring(string.indexOf(".") + 1);

		System.err.println(DateTime.timestamp() + "\t" + uuid + "\t" + string + "\t" + s);
	}
}
