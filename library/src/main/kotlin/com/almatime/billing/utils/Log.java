package com.almatime.billing.utils;

import android.text.TextUtils;

/**
 * <H4>Don't forget to disable DEBUG boolean on release!</H4>
 * When using msg concatenation with a lot of parameters you should put log on if (BuildConfig.DEBUG)
 * Recommended to use TAG name as "ClassName.class.getName()"
 * <p>
 * <b>Priority types in ascending mode:</b>    v, d, i, w, e, wtf
 * </p>
 *
 * @author Alexander Khrapunsky
 * @version 1.2, 22/08/15
 * @since 1.1
 */
public class Log {
    // TODO change log status and output tag
    public static final boolean DEBUG = true;
    private static final String TAG = "xo";

	/**
	 * Creates predefined header which used before message. Contains caller class name and
	 * function name;
	 * @return formatted string of the caller [class_name.method_name.line_num]:
	 */
	private static synchronized String getHeader() {
		final String className = Log.class.getName();
		final StackTraceElement[] traces = Thread.currentThread().getStackTrace();
		boolean found = false;

		for (int i = 0; i < traces.length; i++) {
			StackTraceElement trace = traces[i];

			try {
				if (found) {
					if (!trace.getClassName().startsWith(className)) {
						Class<?> clazz = Class.forName(trace.getClassName());
						return "[" + getClassName(clazz) + "." + trace.getMethodName() + "." +
							   trace.getLineNumber() + "]: ";
					}
				}
				else if (trace.getClassName().startsWith(className)) {
					found = true;
					continue;
				}
			}
			catch (ClassNotFoundException e) {
			}
            catch (IncompatibleClassChangeError e) {
            }
		}
		return "[]: ";
	}

	private static String getClassName(Class<?> clazz) {
		if (clazz != null) {
			if (!TextUtils.isEmpty(clazz.getSimpleName())) {
				return clazz.getSimpleName();
			}
			return getClassName(clazz.getEnclosingClass());
		}
		return "";
	}

    /**
     * Use this when you want to go absolutely nuts with your logging. If for some reason you've decided to
     * log every little thing in a particular part of your app, use the Log.v tag.
     */
    public static void v(String msg) {
        if (DEBUG) android.util.Log.v(TAG, getHeader() + msg);
    }

    /**
     * @see #v(String)
     */
    public static void v(String tag, String msg) {
        if (DEBUG) android.util.Log.v(tag, getHeader() + msg);
    }

    /**
     * Use this for debugging purposes. If you want to print out a bunch of messages so you can log
	 * the exact flow of your program, use this. If you want to keep a log of variable values,
	 * use this.
     */
    public static void d(String msg) {
        if (DEBUG) android.util.Log.d(TAG, getHeader() + msg);
    }

    /**
     * @see #d(String)
     */
    public static void d(String tag, String msg) {
        if (DEBUG) android.util.Log.d(tag, getHeader() + msg);
    }

    /**
     * Use this to post useful information to the log. For example: that you have successfully
	 * connected to a server. Basically use it to report successes.
     */
    public static void i(String msg) {
        if (DEBUG) android.util.Log.i(TAG, getHeader() + msg);
    }

    /**
     * @see #i(String)
     */
    public static void i(String tag, String msg) {
        if (DEBUG) android.util.Log.i(tag, getHeader() + msg);
    }

    /**
     * @see #e(Throwable)
     */
    public static void e(String msg) {
        if (DEBUG) android.util.Log.e(TAG, getHeader() + msg);
    }

    /**
     * @see #e(Throwable)
     */
    public static void e(String tag, String msg) {
        if (DEBUG) android.util.Log.e(tag, getHeader() + msg);
    }

    /**
     * This is for when bad stuff happens. Use this tag in places like inside a catch statement.
     * You know that an error has occurred and therefore you're logging an error.
     */
    public static void e(Throwable t) {
        if (DEBUG) android.util.Log.e(TAG, t.getMessage(), t);
        //Analytics.Companion.getInstance().logException(t);
    }

    /**
     * @see #e(Throwable)
     */
    public static void e(String tag, Throwable t) {
        if (DEBUG) android.util.Log.e(tag, t.getMessage(), t);
        //Analytics.Companion.getInstance().logException(t);
    }

    /**
     * Warning. Anything that happens that is unusual or suspicious, but not necessarily an error.
     *
     * @param msg
     */
    public static void w(String msg) {
        if (DEBUG) android.util.Log.w(TAG, getHeader() + msg);
    }

    /**
     * @see Log#w(String)
     */
    public static void w(String tag, String msg) {
        if (DEBUG) android.util.Log.w(tag, getHeader() + msg);
    }

    /**
     * Used to log events that should never happen ("wtf" being an abbreviation for "What a Terrible
	 * Failure", of course). You can think of this method as the equivalent of Java's assert method.
     */
    public static void wtf(String msg) {
        if (DEBUG) android.util.Log.wtf(TAG, getHeader() + msg);
    }

    /**
     * @see #wtf(String)
     */
    public static void wtf(String tag, String msg) {
        if (DEBUG) android.util.Log.wtf(tag, getHeader() + msg);
    }

}
