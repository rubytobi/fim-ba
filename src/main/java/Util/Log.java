package Util;

public class Log {
	private boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
			.indexOf("jdwp") >= 0;

	private boolean isDebug() {
		return isDebug;
	}

	public static void i(String s) {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		String string = stack[2].toString();

		// remove package name
		string = string.substring(string.indexOf(".") + 1);

		// remove class name
		string = string.substring(string.indexOf(".") + 1);

		System.out.println("#info\t" + string + "\t" + s);
	}

	public static void d(String s) {
		if (!new Log().isDebug()) {
			return;
		}

		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		String string = stack[2].toString();

		// remove package name
		string = string.substring(string.indexOf(".") + 1);

		// remove class name
		string = string.substring(string.indexOf(".") + 1);

		System.out.println("#debug\t" + string + "\t" + s);
	}

	public static void e(String s) {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		String string = stack[2].toString();

		// remove package name
		string = string.substring(string.indexOf(".") + 1);

		// remove class name
		string = string.substring(string.indexOf(".") + 1);

		System.err.println("#error\t" + string + "\t" + s);
	}
}
